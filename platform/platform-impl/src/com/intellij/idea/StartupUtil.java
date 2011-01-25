/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class StartupUtil {
  static boolean isHeadless;
  static SocketLock ourLock;
  private static String myDefaultLAF;

  @NonNls public static final String NOSPLASH = "nosplash";

  private StartupUtil() {
  }

  public static void setDefaultLAF(String laf) {
    myDefaultLAF = laf;
  }

  public static String getDefaultLAF() {
    return myDefaultLAF;
  }

  public static boolean shouldShowSplash(final String[] args) {
    @NonNls final String nosplashCode = NOSPLASH;
    return !Arrays.asList(args).contains(nosplashCode);
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  /**
   * Checks if the program can run under the JDK it was started with
   */
  static boolean checkJdkVersion() {
    if (!"true".equals(System.getProperty("idea.no.jre.check"))) {
      try {
        // try to find a class from tools.jar
        Class.forName("com.sun.jdi.Field");
      }
      catch (ClassNotFoundException e) {
        if (isHeadless()) { //team server inspections
          System.out.println("tools.jar is not in " + ApplicationNamesInfo.getInstance().getProductName() + " classpath. Please ensure JAVA_HOME points to JDK rather than JRE");
          return false;
        }
        try {
          final Runnable runnable = new Runnable() {
            public void run() {
              JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), "tools.jar is not in " +
                                                                        ApplicationNamesInfo.getInstance().getProductName() +
                                                                        " classpath. Please ensure JAVA_HOME points to JDK rather than JRE",
                                            "Error", JOptionPane.ERROR_MESSAGE);
            }
          };
          if(EventQueue.isDispatchThread()) {
            runnable.run();
          } else {
            EventQueue.invokeAndWait(runnable);
          }
        } catch(Exception ex) {
          // do nothing
        }
        return false;
      }
    }

    if ("true".equals(System.getProperty("idea.no.jdk.check"))) return true;

    String version = System.getProperty("java.version");

    if (version.startsWith("1.5") || version.startsWith("1.6")) {
      return true;
    }

    showVersionMismatch(version);
    return false;
  }

  private static void showVersionMismatch(final String version) {
    if (isHeadless()) { //team server inspections
      System.out.println("The JDK version is " + version + " but " + ApplicationNamesInfo.getInstance().getProductName() + " requires JDK 1.5 or 1.6");
      return;
    }
    JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                  "The JDK version is " + version + "\n" + ApplicationNamesInfo.getInstance().getProductName() +
                                  " requires JDK 1.5 or 1.6",
                                  "Java Version Mismatch",
                                  JOptionPane.INFORMATION_MESSAGE);
  }

  synchronized static boolean lockSystemFolders(String[] args) {
    if (ourLock == null) {
      ourLock = new SocketLock();
    }

    SocketLock.ActivateStatus activateStatus = ourLock.lock(PathManager.getConfigPath(false), true, args);
    if (activateStatus == SocketLock.ActivateStatus.NO_INSTANCE) {
      activateStatus = ourLock.lock(PathManager.getSystemPath(), false);
    }

    if (activateStatus != SocketLock.ActivateStatus.NO_INSTANCE) {
      if (isHeadless()) { //team server inspections
        System.out.println("Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() + " can be run at a time.");
        return false;
      }
      if (activateStatus == SocketLock.ActivateStatus.CANNOT_ACTIVATE) {
        JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                      "Only one instance of " + ApplicationNamesInfo.getInstance().getProductName() +
                                      " can be run at a time.",
                                      "Error",
                                      JOptionPane.INFORMATION_MESSAGE);
      }
      return false;
    }

    return true;
  }

  static boolean checkStartupPossible(String[] args) {
    return checkJdkVersion() && lockSystemFolders(args);
  }

  static void runStartupWizard() {
    final java.util.List<ApplicationInfoEx.PluginChooserPage> pages = ApplicationInfoImpl.getShadowInstance().getPluginChooserPages();
    if (!pages.isEmpty()) {
      final StartupWizard startupWizard = new StartupWizard(pages);
      startupWizard.setCancelText("Skip");
      startupWizard.show();
      PluginManager.invalidatePlugins();
    }
  }

  public static void addExternalInstanceListener(Consumer<List<String>> consumer) {
    ourLock.setActivateListener(consumer);
  }
}
