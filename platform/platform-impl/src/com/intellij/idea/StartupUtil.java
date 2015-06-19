/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.customize.CustomizeIDEWizardDialog;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.startupWizard.StartupWizard;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.lang.UrlClassLoader;
import com.sun.jna.Native;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author yole
 */
public class StartupUtil {
  public static final String NO_SPLASH = "nosplash";

  private static SocketLock ourLock;

  private StartupUtil() { }

  public static boolean shouldShowSplash(final String[] args) {
    return !Arrays.asList(args).contains(NO_SPLASH);
  }

  public synchronized static void addExternalInstanceListener(@Nullable Consumer<List<String>> consumer) {
    // method called by app after startup
    ourLock.setExternalInstanceListener(consumer);
  }

  public synchronized static int getAcquiredPort() {
    return ourLock.getAcquiredPort();
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
    }

    appStarter.start(newConfigFolder);
  }

  /**
   * Checks if the program can run under the JDK it was started with.
   */
  private static boolean checkJdkVersion() {
    if (!"true".equals(System.getProperty("idea.no.jre.check"))) {
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
    
    if (!"true".equals(System.getProperty("idea.no.64bit.check"))) {
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

  private static void delete(File ideTempFile) {
    if (!FileUtilRt.delete(ideTempFile)) {
      ideTempFile.deleteOnExit();
    }
  }

  private synchronized static boolean lockSystemFolders(String[] args) {
    assert ourLock == null;
    ourLock = new SocketLock(PathManager.getConfigPath(), PathManager.getSystemPath());
    if (ourLock.getAcquiredPort() == -1) {
      showErrorTooManyInstances(null);
      return false;
    }

    SocketLock.ActivateStatus activateStatus = null;
    try {
      activateStatus = ourLock.lock(args);
    }
    catch (Exception e) {
      Main.showMessage("Cannot lock system folders", e);
    }

    if (activateStatus != SocketLock.ActivateStatus.NO_INSTANCE) {
      showErrorTooManyInstances(activateStatus);
      return false;
    }

    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      @Override
      public void run() {
        ourLock.dispose();
      }
    }, "Unlock system folder"));

    return true;
  }

  private static void showErrorTooManyInstances(@Nullable SocketLock.ActivateStatus activateStatus) {
    if (Main.isHeadless() || activateStatus == SocketLock.ActivateStatus.CANNOT_ACTIVATE) {
      String message = "Only one instance of " + ApplicationNamesInfo.getInstance().getFullProductName() + " can be run at a time.";
      Main.showMessage("Too Many Instances", message, true);
    }
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

  private static final String JAVA_IO_TEMP_DIR = "java.io.tmpdir";

  private static void loadSystemLibraries(final Logger log) {
    // load JNA and Snappy in own temp directory - to avoid collisions and work around no-exec /tmp
    File ideTempDir = new File(PathManager.getTempPath());
    if (!(ideTempDir.mkdirs() || ideTempDir.exists())) {
      throw new RuntimeException("Unable to create temp directory '" + ideTempDir + "'");
    }

    String javaTempDir = System.getProperty(JAVA_IO_TEMP_DIR);
    try {
      System.setProperty(JAVA_IO_TEMP_DIR, ideTempDir.getPath());
      if (System.getProperty("jna.nosys") == null && System.getProperty("jna.nounpack") == null) {
        // force using bundled JNA dispatcher (if not explicitly stated)
        System.setProperty("jna.nosys", "true");
        System.setProperty("jna.nounpack", "false");
      }
      try {
        final long t = System.currentTimeMillis();
        log.info("JNA library loaded (" + (Native.POINTER_SIZE * 8) + "-bit) in " + (System.currentTimeMillis() - t) + " ms");
      }
      catch (Throwable t) {
        logError(log, "Unable to load JNA library", t);
      }
    }
    finally {
      System.setProperty(JAVA_IO_TEMP_DIR, javaTempDir);
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
  }

  static void runStartupWizard() {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();

    String stepsProvider = appInfo.getCustomizeIDEWizardStepsProvider();
    if (stepsProvider != null) {
      CustomizeIDEWizardDialog.showCustomSteps(stepsProvider);
      PluginManagerCore.invalidatePlugins();
      return;
    }

    if (PlatformUtils.isIntelliJ()) {
      new CustomizeIDEWizardDialog().show();
      PluginManagerCore.invalidatePlugins();
      return;
    }

    List<ApplicationInfoEx.PluginChooserPage> pages = appInfo.getPluginChooserPages();
    if (!pages.isEmpty()) {
      StartupWizard startupWizard = new StartupWizard(pages);
      startupWizard.setCancelText("Skip");
      startupWizard.show();
      PluginManagerCore.invalidatePlugins();
    }
  }
}
