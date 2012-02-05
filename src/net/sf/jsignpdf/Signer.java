package net.sf.jsignpdf;

import static net.sf.jsignpdf.Constants.*;

import java.io.File;
import java.io.FileFilter;

import javax.swing.UIManager;
import javax.swing.WindowConstants;

import net.sf.jsignpdf.utils.ConfigProvider;
import net.sf.jsignpdf.utils.GuiUtils;
import net.sf.jsignpdf.utils.KeyStoreUtils;
import net.sf.jsignpdf.utils.PKCS11Utils;
import net.sf.jsignpdf.utils.ResourceProvider;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * JSignPdf main class - it either process command line or if no argument is
 * given, sets system Look&Feel and creates SignPdfForm GUI.
 * 
 * @author Josef Cacek
 */
public class Signer {

  private static void printHelp() {
    final HelpFormatter formatter = new HelpFormatter();
    final ResourceProvider res = ResourceProvider.getInstance();
    final String ls = System.getProperty("line.separator");
    formatter.printHelp(80, "java -jar JSignPdf.jar [file1.pdf [file2.pdf ...]]", res.get("hlp.header"),
        SignerOptionsFromCmdLine.OPTS, ls + res.get("hlp.footer.exitCodes") + ls + StringUtils.repeat("-", 80) + ls
            + res.get("hlp.footer.examples"), true);
  }

  /**
   * Main.
   * 
   * @param args
   */
  public static void main(String[] args) {
    PKCS11Utils.registerProvider(ConfigProvider.getInstance().getProperty("pkcs11config.path"));

    if (args != null && args.length > 0) {
      final SignerOptionsFromCmdLine tmpOpts = new SignerOptionsFromCmdLine();
      parseCommandLine(args, tmpOpts);

      if (tmpOpts.isPrintVersion()) {
        System.out.println("JSignPdf version " + VERSION);
      }
      if (tmpOpts.isPrintHelp()) {
        printHelp();
      }
      if (tmpOpts.isListKeyStores()) {
        tmpOpts.log("console.keystores");
        for (String tmpKsType : KeyStoreUtils.getKeyStores()) {
          System.out.println(tmpKsType);
        }
      }
      if (tmpOpts.isListKeys()) {
        final String[] tmpKeyAliases = KeyStoreUtils.getKeyAliases(tmpOpts);
        tmpOpts.log("console.keys");
        // list certificate aliases in the keystore
        for (String tmpCert : tmpKeyAliases) {
          System.out.println(tmpCert);
        }
      }
      if (ArrayUtils.isNotEmpty(tmpOpts.getFiles())
          || (!StringUtils.isEmpty(tmpOpts.getInFile()) && !StringUtils.isEmpty(tmpOpts.getOutFile()))) {
        signFiles(tmpOpts);
      } else {
        final boolean tmpCommand = tmpOpts.isPrintVersion() || tmpOpts.isPrintHelp() || tmpOpts.isListKeyStores()
            || tmpOpts.isListKeys();
        if (!tmpCommand) {
          // no valid command provided - print help and exit
          printHelp();
          System.exit(EXIT_CODE_NO_COMMAND);
        }
      }
    } else {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception e) {
        System.err.println("Can't set Look&Feel.");
      }
      SignPdfForm tmpForm = new SignPdfForm(WindowConstants.EXIT_ON_CLOSE);
      tmpForm.pack();
      GuiUtils.center(tmpForm);
      tmpForm.setVisible(true);
    }
  }

  /**
   * Sign the files
   * 
   * @param anOpts
   */
  private static void signFiles(SignerOptionsFromCmdLine anOpts) {
    final SignerLogic tmpLogic = new SignerLogic(anOpts);
    if (ArrayUtils.isEmpty(anOpts.getFiles())) {
      // we've used -lp (loadproperties) parameter
      if (!tmpLogic.signFile()) {
        System.exit(Constants.EXIT_CODE_ALL_SIG_FAILED);
      }
      return;
    }
    int successCount = 0;
    int failedCount = 0;

    for (final String wildcardPath : anOpts.getFiles()) {
      final File wildcardFile = new File(wildcardPath);

      File[] inputFiles;
      if (StringUtils.containsAny(wildcardFile.getName(), '*', '?')) {
        final File inputFolder = wildcardFile.getAbsoluteFile().getParentFile();
        final FileFilter fileFilter = new AndFileFilter(FileFileFilter.FILE, new WildcardFileFilter(
            wildcardFile.getName()));
        inputFiles = inputFolder.listFiles(fileFilter);
        if (inputFiles == null) {
          continue;
        }
      } else {
        inputFiles = new File[] { wildcardFile };
      }
      for (File inputFile : inputFiles) {
        final String tmpInFile = inputFile.getPath();
        if (!inputFile.canRead()) {
          failedCount++;
          System.err.println(ResourceProvider.getInstance().get("file.notReadable", new String[] { tmpInFile }));
          continue;
        }
        anOpts.setInFile(tmpInFile);
        String tmpNameBase = inputFile.getName();
        String tmpSuffix = ".pdf";
        if (StringUtils.endsWithIgnoreCase(tmpNameBase, tmpSuffix)) {
          tmpSuffix = StringUtils.right(tmpNameBase, 4);
          tmpNameBase = StringUtils.left(tmpNameBase, tmpNameBase.length() - 4);
        }
        final StringBuilder tmpName = new StringBuilder(anOpts.getOutPath());
        tmpName.append(anOpts.getOutPrefix());
        tmpName.append(tmpNameBase).append(anOpts.getOutSuffix()).append(tmpSuffix);
        anOpts.setOutFile(tmpName.toString());
        if (tmpLogic.signFile()) {
          successCount++;
        } else {
          failedCount++;
        }

      }
    }
    if (failedCount > 0) {
      System.exit(successCount > 0 ? Constants.EXIT_CODE_SOME_SIG_FAILED : Constants.EXIT_CODE_ALL_SIG_FAILED);
    }
  }

  /**
   * Parses the command line. Exits with error exit code when parsing fails.
   * 
   * @param args
   * @param opts
   */
  private static void parseCommandLine(String[] args, final SignerOptionsFromCmdLine opts) {
    try {
      opts.loadCmdLine(args);
    } catch (ParseException exp) {
      System.err.println("Unable to parse command line (Use -h for the help)\n" + exp.getMessage());
      System.exit(EXIT_CODE_PARSE_ERR);
    }
  }
}
