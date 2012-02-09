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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class StartupUtil {
  static boolean isHeadless;

  private static SocketLock ourLock;
  private static String myDefaultLAF;

  @NonNls public static final String NOSPLASH = "nosplash";

  private StartupUtil() { }

  public static void setDefaultLAF(String laf) {
    myDefaultLAF = laf;
  }

  public static String getDefaultLAF() {
    return myDefaultLAF;
  }

  public static boolean shouldShowSplash(final String[] args) {
    return !Arrays.asList(args).contains(NOSPLASH);
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
      if (!version.startsWith("1.6") && !version.startsWith("1.7")) {
        showError("Java Version Mismatch", "The JDK version is " + version + "\n." +
                                           ApplicationNamesInfo.getInstance().getProductName() + " requires JDK 1.6 or 1.7.");
        return false;
      }
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

  private static boolean checkTmpIsAccessible() {
    if (!SystemInfo.isUnix || SystemInfo.isMac) return true;

    final File tmp;
    try {
      tmp = FileUtil.createTempFile("idea_check_", ".tmp");
      FileUtil.writeToFile(tmp, "#!/bin/sh\n" +
                                "exit 0");
    }
    catch (IOException e) {
      showError("Inaccessible Temp Directory", e.getMessage() + " (" + FileUtil.getTempDirectory() + ").\n" +
                                               "Temp directory is not accessible.\n" +
                                               "Please set 'java.io.tmpdir' system property to point to a writable directory.");
      return false;
    }

    String message = null;
    try {
      if (!tmp.setExecutable(true, true) && !tmp.canExecute()) {
        message = "Cannot make '" + tmp.getAbsolutePath() + "' executable.";
      }
      else {
        final int rv = new ProcessBuilder(tmp.getAbsolutePath()).start().waitFor();
        if (rv != 0) {
          message = "Cannot execute '" + tmp.getAbsolutePath() + "': " + rv;
        }
      }
    }
    catch (Exception e) {
      message = e.getMessage();
    }

    if (!tmp.delete()) {
      tmp.deleteOnExit();
    }

    if (message != null) {
      showError("Inaccessible Temp Directory", message + ".\nPossible reason: temp directory is mounted with a 'noexec' option.\n" +
                                               "Please set 'java.io.tmpdir' system property to point to an accessible directory.");
      return false;
    }


    return true;
  }

  static boolean checkStartupPossible(String[] args) {
    return checkJdkVersion() && lockSystemFolders(args) && checkTmpIsAccessible();
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
}
