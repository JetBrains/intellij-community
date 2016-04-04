/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.idea;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.PrivacyPolicy;
import com.intellij.ide.customize.CustomizeIDEWizardDialog;
import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.startupWizard.StartupWizard;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.sun.jna.Native;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.BuiltInServer;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author yole
 */
public class StartupUtil {
  public static final String NO_SPLASH = "nosplash";

  private static SocketLock ourSocketLock;

  private StartupUtil() { }

  public static boolean shouldShowSplash(final String[] args) {
    return !Arrays.asList(args).contains(NO_SPLASH);
  }

  public synchronized static void addExternalInstanceListener(@Nullable Consumer<List<String>> consumer) {
    // method called by app after startup
    if (ourSocketLock != null) {
      ourSocketLock.setExternalInstanceListener(consumer);
    }
  }

  public static int getAcquiredPort() {
    BuiltInServer server = getServer();
    return server == null ? -1 : server.getPort();
  }

  @Nullable
  public synchronized static BuiltInServer getServer() {
    return ourSocketLock == null ? null : ourSocketLock.getServer();
  }

  interface AppStarter {
    void start(boolean newConfigFolder);
  }

  static void prepareAndStart(String[] args, AppStarter appStarter) {
    boolean newConfigFolder = false;

    if (!Main.isHeadless()) {
      AppUIUtil.updateFrameClass();
      newConfigFolder = !new File(PathManager.getConfigPath()).exists();
    }

    if (!checkJdkVersion()) {
      System.exit(Main.JDK_CHECK_FAILED);
    }

    // avoiding "log4j:WARN No appenders could be found"
    System.setProperty("log4j.defaultInitOverride", "true");
    try {
      org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();
      if (!root.getAllAppenders().hasMoreElements()) {
        root.setLevel(Level.WARN);
        root.addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.DEFAULT_CONVERSION_PATTERN)));
      }
    }
    catch (Throwable e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }

    // note: uses config folder!
    if (!checkSystemFolders()) {
      System.exit(Main.DIR_CHECK_FAILED);
    }
    if (!lockSystemFolders(args)) {
      System.exit(Main.INSTANCE_CHECK_FAILED);
    }

    if (newConfigFolder) {
      ConfigImportHelper.importConfigsTo(PathManager.getConfigPath());
    }

    Logger.setFactory(LoggerFactory.class);
    Logger log = Logger.getInstance(Main.class);
    startLogging(log);
    loadSystemLibraries(log);
    fixProcessEnvironment(log);

    if (!Main.isHeadless()) {
      AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame());
      AppUIUtil.registerBundledFonts();
      final Pair<PrivacyPolicy.Version, String> policy = PrivacyPolicy.getContent();
      if (!PrivacyPolicy.isVersionAccepted(policy.getFirst())) {
        try {
          SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
              showPrivacyPolicyAgreement(policy.getSecond());
            }
          });
          PrivacyPolicy.setVersionAccepted(policy.getFirst());
        }
        catch (Exception ignored) {
        }
      }
    }

    appStarter.start(newConfigFolder);
  }

  /**
   * Checks if the program can run under the JDK it was started with.
   */
  private static boolean checkJdkVersion() {
    String jreCheck = System.getProperty("idea.jre.check");
    if (jreCheck != null && "true".equals(jreCheck)) {
      try {
        // try to find a class from tools.jar
        Class.forName("com.sun.jdi.Field", false, StartupUtil.class.getClassLoader());
      }
      catch (ClassNotFoundException e) {
        String message = "'tools.jar' seems to be not in " + ApplicationNamesInfo.getInstance().getProductName() + " classpath.\n" +
                         "Please ensure JAVA_HOME points to JDK rather than JRE.";
        Main.showMessage("JDK Required", message, true);
        return false;
      }
      catch (LinkageError e) {
        String message = "Cannot load a class from 'tools.jar': " + e.getMessage() + "\n" +
                         "Please ensure JAVA_HOME points to JDK rather than JRE.";
        Main.showMessage("JDK Required", message, true);
        return false;
      }

      if (StringUtil.containsIgnoreCase(System.getProperty("java.vm.name", ""), "OpenJDK") && !SystemInfo.isJavaVersionAtLeast("1.7")) {
        String message = "OpenJDK 6 is not supported. Please use Oracle Java or newer OpenJDK.";
        Main.showMessage("Unsupported JVM", message, true);
        return false;
      }
    }
    jreCheck = System.getProperty("idea.64bit.check");
    if (jreCheck != null && "true".equals(jreCheck)) {
      if (PlatformUtils.isCidr() && !SystemInfo.is64Bit) {
          String message = "32-bit JVM is not supported. Please install 64-bit version.";
          Main.showMessage("Unsupported JVM", message, true);
        return false;
      }
    }

    return true;
  }

  private synchronized static boolean checkSystemFolders() {
    String configPath = PathManager.getConfigPath();
    PathManager.ensureConfigFolderExists();
    if (!new File(configPath).isDirectory()) {
      String message = "Config path '" + configPath + "' is invalid.\n" +
                       "If you have modified the '" + PathManager.PROPERTY_CONFIG_PATH + "' property please make sure it is correct,\n" +
                       "otherwise please re-install the IDE.";
      Main.showMessage("Invalid Config Path", message, true);
      return false;
    }

    String systemPath = PathManager.getSystemPath();
    if (!new File(systemPath).isDirectory()) {
      String message = "System path '" + systemPath + "' is invalid.\n" +
                       "If you have modified the '" + PathManager.PROPERTY_SYSTEM_PATH + "' property please make sure it is correct,\n" +
                       "otherwise please re-install the IDE.";
      Main.showMessage("Invalid System Path", message, true);
      return false;
    }

    File logDir = new File(PathManager.getLogPath());
    boolean logOk = false;
    if (logDir.isDirectory() || logDir.mkdirs()) {
      try {
        File ideTempFile = new File(logDir, "idea_log_check.txt");
        write(ideTempFile, "log check");
        delete(ideTempFile);
        logOk = true;
      }
      catch (IOException ignored) { }
    }
    if (!logOk) {
      String message = "Log path '" + logDir.getPath() + "' is inaccessible.\n" +
                       "If you have modified the '" + PathManager.PROPERTY_LOG_PATH + "' property please make sure it is correct,\n" +
                       "otherwise please re-install the IDE.";
      Main.showMessage("Invalid Log Path", message, true);
      return false;
    }

    File ideTempDir = new File(PathManager.getTempPath());
    String tempInaccessible = null;

    if (!ideTempDir.isDirectory() && !ideTempDir.mkdirs()) {
      tempInaccessible = "unable to create the directory";
    }
    else {
      try {
        File ideTempFile = new File(ideTempDir, "idea_tmp_check.sh");
        write(ideTempFile, "#!/bin/sh\nexit 0");

        if (SystemInfo.isWindows || SystemInfo.isMac) {
          tempInaccessible = null;
        }
        else if (!ideTempFile.setExecutable(true, true)) {
          tempInaccessible = "cannot set executable permission";
        }
        else if (new ProcessBuilder(ideTempFile.getAbsolutePath()).start().waitFor() != 0) {
          tempInaccessible = "cannot execute test script";
        }

        delete(ideTempFile);
      }
      catch (Exception e) {
        tempInaccessible = e.getClass().getSimpleName() + ": " + e.getMessage();
      }
    }

    if (tempInaccessible != null) {
      String message = "Temp directory '" + ideTempDir + "' is inaccessible.\n" +
                       "If you have modified the '" + PathManager.PROPERTY_SYSTEM_PATH + "' property please make sure it is correct,\n" +
                       "otherwise please re-install the IDE.\n\nDetails: " + tempInaccessible;
      Main.showMessage("Invalid System Path", message, true);
      return false;
    }

    return true;
  }

  private static void write(File file, String content) throws IOException {
    FileWriter writer = new FileWriter(file);
    try { writer.write(content); }
    finally { writer.close(); }
  }

  @SuppressWarnings("SSBasedInspection")
  private static void delete(File ideTempFile) {
    if (!FileUtilRt.delete(ideTempFile)) {
      ideTempFile.deleteOnExit();
    }
  }

  private synchronized static boolean lockSystemFolders(String[] args) {
    if (ourSocketLock != null) {
      throw new AssertionError();
    }

    ourSocketLock = new SocketLock(PathManager.getConfigPath(), PathManager.getSystemPath());

    SocketLock.ActivateStatus status;
    try {
      status = ourSocketLock.lock(args);
    }
    catch (Exception e) {
      Main.showMessage("Cannot Lock System Folders", e);
      return false;
    }

    if (status == SocketLock.ActivateStatus.NO_INSTANCE) {
      ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
        @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
        @Override
        public void run() {
          synchronized (StartupUtil.class) {
            ourSocketLock.dispose();
            ourSocketLock = null;
          }
        }
      });
      return true;
    }
    else if (Main.isHeadless() || status == SocketLock.ActivateStatus.CANNOT_ACTIVATE) {
      String message = "Only one instance of " + ApplicationNamesInfo.getInstance().getFullProductName() + " can be run at a time.";
      Main.showMessage("Too Many Instances", message, true);
    }

    if (status == SocketLock.ActivateStatus.ACTIVATED) {
      System.out.println("Already running");
      System.exit(0);
    }

    return false;
  }

  private static void fixProcessEnvironment(Logger log) {
    if (!Main.isCommandLine()) {
      System.setProperty("__idea.mac.env.lock", "unlocked");
    }
    boolean envReady = EnvironmentUtil.isEnvironmentReady();  // trigger environment loading
    if (!envReady) {
      log.info("initializing environment");
    }
  }

  private static void loadSystemLibraries(final Logger log) {
    // load JNA and Snappy in own temp directory - to avoid collisions and work around no-exec /tmp
    File ideTempDir = new File(PathManager.getTempPath());
    if (!(ideTempDir.mkdirs() || ideTempDir.exists())) {
      throw new RuntimeException("Unable to create temp directory '" + ideTempDir + "'");
    }

    if (System.getProperty("jna.tmpdir") == null) {
      System.setProperty("jna.tmpdir", ideTempDir.getPath());
    }
    if (System.getProperty("jna.nosys") == null) {
      System.setProperty("jna.nosys", "true");  // prefer bundled JNA dispatcher lib
    }
    try {
      long t = System.currentTimeMillis();
      log.info("JNA library loaded (" + (Native.POINTER_SIZE * 8) + "-bit) in " + (System.currentTimeMillis() - t) + " ms");
    }
    catch (Throwable t) {
      logError(log, "Unable to load JNA library", t);
    }

    if (SystemInfo.isWin2kOrNewer) {
      IdeaWin32.isAvailable();  // logging is done there
    }

    if (SystemInfo.isWin2kOrNewer && !Main.isHeadless()) {
      try {
        UrlClassLoader.loadPlatformLibrary("focusKiller");
        log.info("Using \"FocusKiller\" library to prevent focus stealing.");
      }
      catch (Throwable t) {
        log.info("\"FocusKiller\" library not found or there were problems loading it.", t);
      }
    }

    if (SystemInfo.isWindows) {
      // WinP should not unpack .dll files into parent directory
      System.setProperty("winp.unpack.dll.to.parent.dir", "false");
    }
  }

  private static void logError(Logger log, String message, Throwable t) {
    message = message + " (OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION + ")";
    log.error(message, t);
  }

  private static void startLogging(final Logger log) {
    Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook - logging") {
      @Override
      public void run() {
        log.info("------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------");
      }
    });
    log.info("------------------------------------------------------ IDE STARTED ------------------------------------------------------");

    ApplicationInfo appInfo = ApplicationInfoImpl.getShadowInstance();
    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    String buildDate = new SimpleDateFormat("dd MMM yyyy HH:ss", Locale.US).format(appInfo.getBuildDate().getTime());
    log.info("IDE: " + namesInfo.getFullProductName() + " (build #" + appInfo.getBuild().asStringWithAllDetails() + ", " + buildDate + ")");
    log.info("OS: " + SystemInfoRt.OS_NAME + " (" + SystemInfoRt.OS_VERSION + ", " + SystemInfo.OS_ARCH + ")");
    log.info("JRE: " + System.getProperty("java.runtime.version", "-") + " (" + System.getProperty("java.vendor", "-") + ")");
    log.info("JVM: " + System.getProperty("java.vm.version", "-") + " (" + System.getProperty("java.vm.name", "-") + ")");

    List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
    if (arguments != null) {
      log.info("JVM Args: " + StringUtil.join(arguments, " "));
    }

    String extDirs = System.getProperty("java.ext.dirs");
    if (extDirs != null) {
      for (String dir : StringUtil.split(extDirs, File.pathSeparator)) {
        String[] content = new File(dir).list();
        if (content != null && content.length > 0) {
          log.info("ext: " + dir + ": " + Arrays.toString(content));
        }
      }
    }

    log.info("JNU charset: " + System.getProperty("sun.jnu.encoding"));
  }

  /**
   * @param htmlText Updated version of Privacy Policy text if any.
   *                        If it's <code>null</code> the standard text from bundled resources would be used.
   */
  public static void showPrivacyPolicyAgreement(@NotNull String htmlText) {
    DialogWrapper dialog = new DialogWrapper(true) {
      @Nullable
      @Override
      protected JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(JBUI.scale(5), JBUI.scale(5)));
        JEditorPane viewer = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
        viewer.setFocusable(true);
        viewer.addHyperlinkListener(new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(HyperlinkEvent e) {
            URL url = e.getURL();
            if (url != null) {
              BrowserUtil.browse(url);
            }
            else {
              SwingHelper.scrollToReference(viewer, e.getDescription());
            }
          }
        });
        viewer.setText(htmlText);
        StyleSheet styleSheet = ((HTMLDocument)viewer.getDocument()).getStyleSheet();
        styleSheet.addRule("body {font-family: \"Segoe UI\", Tahoma, sans-serif;}");
        styleSheet.addRule("body {margin-top:0;padding-top:0;}");
        styleSheet.addRule("body {font-size:" + JBUI.scaleFontSize(13) + "pt;}");
        styleSheet.addRule("h2, em {margin-top:" + JBUI.scaleFontSize(20) + "pt;}");
        styleSheet.addRule("h1, h2, h3, p, h4, em {margin-bottom:0;padding-bottom:0;}");
        styleSheet.addRule("p, h1 {margin-top:0;padding-top:"+JBUI.scaleFontSize(6)+"pt;}");
        styleSheet.addRule("li {margin-bottom:" + JBUI.scaleFontSize(6) + "pt;}");
        styleSheet.addRule("h2 {margin-top:0;padding-top:"+JBUI.scaleFontSize(13)+"pt;}");
        viewer.setCaretPosition(0);
        viewer.setBorder(JBUI.Borders.empty(0, 5, 5, 5));
        centerPanel.add(new JLabel("Please read and accept these terms and conditions:"), BorderLayout.NORTH);
        centerPanel
          .add(new JBScrollPane(viewer, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER),
               BorderLayout.CENTER);
        return centerPanel;
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        init();
        setOKButtonText("Accept");
        setCancelButtonText("Reject and Exit");
        setAutoAdjustable(false);
      }

      @Override
      public void doCancelAction() {
        super.doCancelAction();
        ApplicationEx application = ApplicationManagerEx.getApplicationEx();
        if (application == null) {
          System.exit(Main.PRIVACY_POLICY_REJECTION);
        }
        else {
          ((ApplicationImpl)application).exit(true, true, false, false);
        }
      }
    };
    dialog.setModal(true);
    dialog.setTitle(ApplicationNamesInfo.getInstance().getFullProductName() + " Privacy Policy Agreement");
    dialog.setSize(JBUI.scale(509), JBUI.scale(395));
    dialog.show();
  }

  static void runStartupWizard() {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();

    String stepsProviderName = appInfo.getCustomizeIDEWizardStepsProvider();
    if (stepsProviderName != null) {
      CustomizeIDEWizardStepsProvider provider;

      try {
        Class<?> providerClass = Class.forName(stepsProviderName);
        provider = (CustomizeIDEWizardStepsProvider)providerClass.newInstance();
      }
      catch (Throwable e) {
        Main.showMessage("Configuration Wizard Failed", e);
        return;
      }

      new CustomizeIDEWizardDialog(provider).show();
      PluginManagerCore.invalidatePlugins();
      return;
    }

    List<ApplicationInfoEx.PluginChooserPage> pages = appInfo.getPluginChooserPages();
    if (!pages.isEmpty()) {
      new StartupWizard(pages).show();
      PluginManagerCore.invalidatePlugins();
    }
  }
}
