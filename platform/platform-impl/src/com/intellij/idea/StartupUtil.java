/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SnappyInitializer;
import com.intellij.util.text.DateFormatUtilRt;
import com.sun.jna.Native;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class StartupUtil {
  @NonNls public static final String NO_SPLASH = "nosplash";

  private static SocketLock ourLock;
  private static String myDefaultLAF;

  private StartupUtil() { }

  public static void setDefaultLAF(String laf) {
    myDefaultLAF = laf;
  }

  public static String getDefaultLAF() {
    return myDefaultLAF;
  }

  public static boolean shouldShowSplash(final String[] args) {
    return !Arrays.asList(args).contains(NO_SPLASH);
  }

  /** @deprecated use {@link Main#isHeadless()} (to remove in IDEA 14) */
  @SuppressWarnings("unused")
  public static boolean isHeadless() {
    return Main.isHeadless();
  }

  public synchronized static void addExternalInstanceListener(Consumer<List<String>> consumer) {
    ourLock.setActivateListener(consumer);
  }

  interface AppStarter {
    void start(boolean newConfigFolder);
  }

  static void prepareAndStart(String[] args, AppStarter appStarter) {
    boolean newConfigFolder = false;

    if (!Main.isHeadless()) {
      AppUIUtil.updateFrameClass();

      newConfigFolder = PathManager.ensureConfigFolderExists(true);
      if (newConfigFolder) {
        ConfigImportHelper.importConfigsTo(PathManager.getConfigPath());
      }
    }

    boolean canStart = checkJdkVersion() && checkSystemFolders() && lockSystemFolders(args);  // note: uses config folder!
    if (!canStart) {
      System.exit(Main.STARTUP_IMPOSSIBLE);
    }

    Logger.setFactory(LoggerFactory.getInstance());
    Logger log = Logger.getInstance(Main.class);
    startLogging(log);
    fixProcessEnvironment(log);
    loadSystemLibraries(log);

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
        Class.forName("com.sun.jdi.Field");
      }
      catch (ClassNotFoundException e) {
        String message = "'tools.jar' seems to be not in " + ApplicationNamesInfo.getInstance().getProductName() + " classpath.\n" +
                         "Please ensure JAVA_HOME points to JDK rather than JRE.";
        Main.showMessage("JDK Required", message, true);
        return false;
      }
    }

    return true;
  }

  private synchronized static boolean checkSystemFolders() {
    final String configPath = PathManager.getConfigPath();
    if (!new File(configPath).isDirectory()) {
      String message = "Config path '" + configPath + "' is invalid.\n" +
                       "If you have modified the 'idea.config.path' property please make sure it is correct,\n" +
                       "otherwise please re-install the IDE.";
      Main.showMessage("Invalid Config Path", message, true);
      return false;
    }

    final String systemPath = PathManager.getSystemPath();
    if (systemPath == null || !new File(systemPath).isDirectory()) {
      String message = "System path '" + systemPath + "' is invalid.\n" +
                       "If you have modified the 'idea.system.path' property please make sure it is correct,\n" +
                       "otherwise please re-install the IDE.";
      Main.showMessage("Invalid System Path", message, true);
      return false;
    }

    return true;
  }

  private synchronized static boolean lockSystemFolders(String[] args) {
    if (ourLock == null) {
      ourLock = new SocketLock();
    }

    SocketLock.ActivateStatus activateStatus = ourLock.lock(PathManager.getConfigPath(false), true, args);
    if (activateStatus == SocketLock.ActivateStatus.NO_INSTANCE) {
      activateStatus = ourLock.lock(PathManager.getSystemPath(), false);
    }

    if (activateStatus != SocketLock.ActivateStatus.NO_INSTANCE) {
      if (Main.isHeadless() || activateStatus == SocketLock.ActivateStatus.CANNOT_ACTIVATE) {
        String message = "Only one instance of " + ApplicationNamesInfo.getInstance().getFullProductName() + " can be run at a time.";
        Main.showMessage("Too Many Instances", message, true);
      }
      return false;
    }

    return true;
  }

  private static void fixProcessEnvironment(Logger log) {
    boolean envReady = EnvironmentUtil.isEnvironmentReady();  // trigger environment loading
    if (!envReady) {
      log.info("initializing environment");
    }
  }

  private static final String JAVA_IO_TEMP_DIR = "java.io.tmpdir";

  private static void loadSystemLibraries(final Logger log) {
    // load JNA and Snappy in own temp directory - to avoid collisions and work around no-exec /tmp
    final File ideaTempDir = new File(PathManager.getSystemPath(), "tmp");
    if (!(ideaTempDir.mkdirs() || ideaTempDir.exists())) {
      throw new RuntimeException("Unable to create temp directory '" + ideaTempDir + "'");
    }

    final String javaTempDir = System.getProperty(JAVA_IO_TEMP_DIR);
    try {
      System.setProperty(JAVA_IO_TEMP_DIR, ideaTempDir.getPath());
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

    SnappyInitializer.initializeSnappy(log, ideaTempDir);

    if (SystemInfo.isWin2kOrNewer) {
      IdeaWin32.isAvailable();  // logging is done there
    }

    if (SystemInfo.isWin2kOrNewer && !Main.isHeadless()) {
      try {
        System.loadLibrary(SystemInfo.isAMD64 ? "focusKiller64" : "focusKiller");
        log.info("Using \"FocusKiller\" library to prevent focus stealing.");
      }
      catch (Throwable t) {
        log.info("\"FocusKiller\" library not found or there were problems loading it.", t);
      }
    }
  }

  private static void logError(Logger log, String message, Throwable t) {
    message = message + " (OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION + ")";
    log.error(message, t);
  }

  private static void startLogging(final Logger log) {
    Runtime.getRuntime().addShutdownHook(new Thread("Shutdown hook - logging") {
      public void run() {
        log.info("------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------");
      }
    });
    log.info("------------------------------------------------------ IDE STARTED ------------------------------------------------------");

    ApplicationInfo appInfo = ApplicationInfoImpl.getShadowInstance();
    ApplicationNamesInfo namesInfo = ApplicationNamesInfo.getInstance();
    log.info("IDE: " + namesInfo.getFullProductName() + " (build #" + appInfo.getBuild() + ", " +
                   DateFormatUtilRt.formatBuildDate(appInfo.getBuildDate()) + ")");
    log.info("OS: " + SystemInfoRt.OS_NAME + " (" + SystemInfoRt.OS_VERSION + ")");
    log.info("JRE: " + System.getProperty("java.runtime.version", "-") + " (" + System.getProperty("java.vendor", "-") + ")");
    log.info("JVM: " + System.getProperty("java.vm.version", "-") + " (" + System.getProperty("java.vm.vendor", "-") + ")");

    List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
    if (arguments != null) {
      log.info("JVM Args: " + StringUtil.join(arguments, " "));
    }
  }
}
