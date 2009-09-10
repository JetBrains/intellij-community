package com.intellij.idea;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.AppUIUtil;

import javax.swing.*;

@SuppressWarnings({"HardCodedStringLiteral", "UseOfSystemOutOrSystemErr"})
public class MainImpl {
  final static String APPLICATION_NAME = "idea";
  private static final String LOG_CATEGORY = "#com.intellij.idea.Main";
  private static boolean runStartupWizard = false;

  private MainImpl() {
  }

  /**
   * Is called from PluginManager
   */
  protected static void start(final String[] args) {
    System.setProperty("idea.platform.prefix", "Idea");
    StartupUtil.isHeadless = Main.isHeadless(args);
    boolean isNewConfigFolder = PathManager.ensureConfigFolderExists(true);
    if (!StartupUtil.isHeadless && isNewConfigFolder) {
      runStartupWizard = true;
      try {
        if (SystemInfo.isWindowsVista || SystemInfo.isMac) {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
      }
      catch (Exception e) {
        // ignore
      }
      if (ApplicationNamesInfo.getInstance().getLowercaseProductName().equals("Idea")) {
        ConfigImportHelper.importConfigsTo(PathManager.getConfigPath());
      }
    }

    if (!StartupUtil.checkStartupPossible()) {   // It uses config folder!
      System.exit(-1);
    }

    Logger.setFactory(LoggerFactory.getInstance());

    final Logger LOG = Logger.getInstance(LOG_CATEGORY);

    Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook - logging") {
      public void run() {
        LOG.info(
          "------------------------------------------------------ IDEA SHUTDOWN ------------------------------------------------------");
      }
    });
    LOG.info("------------------------------------------------------ IDEA STARTED ------------------------------------------------------");

    _main(args);
  }

  protected static void _main(final String[] args) {
    // http://weblogs.java.net/blog/shan_man/archive/2005/06/improved_drag_g.html
    System.setProperty("sun.swing.enableImprovedDragGesture", "");

    if (!StartupUtil.isHeadless()) {
      AppUIUtil.updateFrameIcon(JOptionPane.getRootFrame());
    }

    if (SystemInfo.isWindows && !SystemInfo.isWindows9x) {
      final Logger LOG = Logger.getInstance(LOG_CATEGORY);
      try {
        if (SystemInfo.isAMD64) {
          System.loadLibrary("focuskiller64");
        }
        else {
          System.loadLibrary("focuskiller");
        }
        LOG.info("Using \"FocusKiller\" library to prevent focus stealing.");
      }
      catch (Throwable e) {
        LOG.info("\"FocusKiller\" library not found or there were problems loading it.", e);
      }
    }

    startApplication(args);
  }

  private static void startApplication(final String[] args) {
    if (runStartupWizard) {
      StartupUtil.runStartupWizard();
    }
    final IdeaApplication app = new IdeaApplication(args);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        app.run();
      }
    });
  }
}