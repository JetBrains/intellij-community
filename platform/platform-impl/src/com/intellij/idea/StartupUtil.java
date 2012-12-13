/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.startupWizard.StartupWizard;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import com.sun.jna.Native;
import org.jetbrains.annotations.NonNls;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyLoader;

import javax.swing.*;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class StartupUtil {
  @NonNls public static final String NO_SPLASH = "nosplash";

  public static final boolean NO_SNAPPY = SystemProperties.getBooleanProperty("idea.no.snappy", false) ||
                                          SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast("1.7"); // TODO [Maxim] Update Snappy to 1.5M2 http://youtrack.jetbrains.com/issue/IDEA-95319

  static boolean isHeadless;

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

  public static boolean isHeadless() {
    return isHeadless;
  }

  private static void showError(final String title, final String message) {
    if (isHeadless()) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(message);
    }
    else {
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), message, title, JOptionPane.ERROR_MESSAGE);
    }
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
        showError("Error", "'tools.jar' is not in " + ApplicationNamesInfo.getInstance().getProductName() + " classpath.\n" +
                           "Please ensure JAVA_HOME points to JDK rather than JRE.");
        return false;
      }
    }

    if (!"true".equals(System.getProperty("idea.no.jdk.check"))) {
      final String version = System.getProperty("java.version");
      if (!SystemInfo.isJavaVersionAtLeast("1.6")) {
        showError("Java Version Mismatch", "The JDK version is " + version + ".\n" +
                                           ApplicationNamesInfo.getInstance().getProductName() + " requires JDK 1.6 or higher.");
        return false;
      }
    }

    return true;
  }

  private synchronized static boolean checkSystemFolders() {
    final String configPath = PathManager.getConfigPath();
    if (configPath == null || !new File(configPath).isDirectory()) {
      showError("Invalid config path", "Config path '" + configPath + "' is invalid.\n" +
                                       "If you have modified the 'idea.config.path' property please make sure it is correct,\n" +
                                       "otherwise please re-install the IDE.");
      return false;
    }

    final String systemPath = PathManager.getSystemPath();
    if (systemPath == null || !new File(systemPath).isDirectory()) {
      showError("Invalid system path", "System path '" + systemPath + "' is invalid.\n" +
                                       "If you have modified the 'idea.system.path' property please make sure it is correct,\n" +
                                       "otherwise please re-install the IDE.");
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
      if (isHeadless() || activateStatus == SocketLock.ActivateStatus.CANNOT_ACTIVATE) {
        showError("Error", "Only one instance of " + ApplicationNamesInfo.getInstance().getFullProductName() + " can be run at a time.");
      }
      return false;
    }

    return true;
  }

  static boolean checkStartupPossible(String[] args) {
    return checkJdkVersion() &&
           checkSystemFolders() &&
           lockSystemFolders(args);
  }

  static void runStartupWizard() {
    final List<ApplicationInfoEx.PluginChooserPage> pages = ApplicationInfoImpl.getShadowInstance().getPluginChooserPages();
    if (!pages.isEmpty()) {
      final StartupWizard startupWizard = new StartupWizard(pages);
      startupWizard.setCancelText("Skip");
      startupWizard.show();
      PluginManager.invalidatePlugins();
    }
  }

  public synchronized static void addExternalInstanceListener(Consumer<List<String>> consumer) {
    ourLock.setActivateListener(consumer);
  }

  private static final String JAVA_IO_TEMP_DIR = "java.io.tmpdir";

  static void loadSystemLibraries(final Logger log) {
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
        log.error("Unable to load JNA library", t);
      }
    }
    finally {
      System.setProperty(JAVA_IO_TEMP_DIR, javaTempDir);
    }

    if (!NO_SNAPPY) {
      if (System.getProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR) == null) {
        System.setProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, ideaTempDir.getPath());
      }
      try {
        final long t = System.currentTimeMillis();
        loadSnappyForJRockit();
        log.info("Snappy library loaded (" + Snappy.getNativeLibraryVersion() + ") in " + (System.currentTimeMillis() - t) + " ms");
      }
      catch (Throwable t) {
        log.error("Unable to load Snappy library", t);
      }
    }

    if (SystemInfo.isWindows && SystemInfo.isWin2kOrNewer) {
      IdeaWin32.isAvailable();  // logging is done there
    }

    if (SystemInfo.isWindows && SystemInfo.isWin2kOrNewer && !isHeadless) {
      try {
        System.loadLibrary(SystemInfo.isAMD64 ? "focusKiller64" : "focusKiller");
        log.info("Using \"FocusKiller\" library to prevent focus stealing.");
      }
      catch (Throwable t) {
        log.info("\"FocusKiller\" library not found or there were problems loading it.", t);
      }
    }
  }

  // todo[r.sh] drop after migration on Java 7
  private static void loadSnappyForJRockit() throws Exception {
    String vmName = System.getProperty("java.vm.name");
    if (vmName == null || !vmName.toLowerCase().contains("jrockit")) {
      return;
    }

    byte[] bytes;
    InputStream in = StartupUtil.class.getResourceAsStream("/org/xerial/snappy/SnappyNativeLoader.bytecode");
    try {
      bytes = FileUtil.loadBytes(in);
    }
    finally {
      in.close();
    }

    ClassLoader classLoader = StartupUtil.class.getClassLoader();

    Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
    defineClass.setAccessible(true);
    Class<?> loaderClass = (Class<?>)defineClass.invoke(classLoader, "org.xerial.snappy.SnappyNativeLoader", bytes, 0, bytes.length);
    loaderClass = classLoader.loadClass(loaderClass.getName());

    String[] classes = {"org.xerial.snappy.SnappyNativeAPI", "org.xerial.snappy.SnappyNative", "org.xerial.snappy.SnappyErrorCode"};
    for (String aClass : classes) {
      classLoader.loadClass(aClass);
    }

    Method loadNativeLibrary = SnappyLoader.class.getDeclaredMethod("loadNativeLibrary", Class.class);
    loadNativeLibrary.setAccessible(true);
    loadNativeLibrary.invoke(null, loaderClass);
  }
}
