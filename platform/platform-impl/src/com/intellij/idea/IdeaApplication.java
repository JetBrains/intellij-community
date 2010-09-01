/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.ExtensionPoints;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.ui.Splash;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;


@SuppressWarnings({"CallToPrintStackTrace"})
public class IdeaApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication");

  protected final String[] myArgs;
  private boolean myPerformProjectLoad = true;
  private static IdeaApplication ourInstance;
  private ApplicationStarter myStarter;
  @NonNls public static final String IDEA_IS_INTERNAL_PROPERTY = "idea.is.internal";

  public IdeaApplication(String[] args) {
    LOG.assertTrue(ourInstance == null);
    ourInstance = this;
    myArgs = args;
    boolean isInternal = Boolean.valueOf(System.getProperty(IDEA_IS_INTERNAL_PROPERTY)).booleanValue();

    if (Main.isCommandLine(args)) {
      new CommandLineApplication(isInternal, false, Main.isHeadless(args));
    }
    else {
      System.setProperty("sun.awt.noerasebackground","true");
      ApplicationManagerEx.createApplication(isInternal, false, false, false, "idea");
    }

    myStarter = getStarter();
    myStarter.premain(args);
  }

  protected ApplicationStarter getStarter() {
    if (myArgs.length > 0) {
      final Application app = ApplicationManager.getApplication();
      app.getPlugins(); //TODO[max] make it clearer plugins should initialize before querying for extpoints.

      ExtensionPoint<ApplicationStarter> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.APPLICATION_STARTER);
      final ApplicationStarter[] starters = point.getExtensions();
      String key = myArgs[0];
      for (ApplicationStarter o : starters) {
        if (Comparing.equal(o.getCommandName(), key)) return o;
      }
    }
    return new IdeStarter();
  }

  public static IdeaApplication getInstance() {
    return ourInstance;
  }

  public void run() {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    try {
      app.load(PathManager.getOptionsPath());
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    catch (InvalidDataException e) {
      e.printStackTrace();
    }

    myStarter.main(myArgs);
    myStarter = null; //GC it
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initLAF() {
    if (SystemInfo.isMac) {
      UIManager.put("Panel.opaque", Boolean.TRUE);
      UIManager.installLookAndFeel("Quaqua", "ch.randelshofer.quaqua.QuaquaLookAndFeel");
    }
    else {
      UIManager.installLookAndFeel("JGoodies Plastic", "com.jgoodies.looks.plastic.PlasticLookAndFeel");
      UIManager.installLookAndFeel("JGoodies Plastic 3D", "com.jgoodies.looks.plastic.Plastic3DLookAndFeel");
      UIManager.installLookAndFeel("JGoodies Plastic XP", "com.jgoodies.looks.plastic.PlasticXPLookAndFeel");
      UIManager.installLookAndFeel("JGoodies Windows L&F", "com.jgoodies.looks.windows.WindowsLookAndFeel");
    }
  }

  protected class IdeStarter implements ApplicationStarter {
    private Splash mySplash;
    public String getCommandName() {
      return null;
    }

    public void premain(String[] args) {
      if (StartupUtil.shouldShowSplash(args)) {
        final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
        final Object splashScreen = getSplashScreen();
        if (splashScreen == null) {
          final Splash splash = new Splash(appInfo.getLogoUrl(), appInfo.getLogoTextColor());
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              splash.show();
            }
          });
          mySplash = splash;
        }
        else {
          updateSplashScreen(appInfo, splashScreen);
        }
      }
      initLAF();
    }

    private void updateSplashScreen(ApplicationInfoEx appInfo, Object splashScreen) {
      final Graphics2D graphics;
      try {
        final Class<?> aClass = splashScreen.getClass();
        graphics = (Graphics2D)aClass.getMethod("createGraphics").invoke(splashScreen);
        final Dimension size = (Dimension)aClass.getMethod("getSize").invoke(splashScreen);
        if (Splash.showLicenseeInfo(graphics, 0, 0, size.height, appInfo.getLogoTextColor())) {
          aClass.getMethod("update").invoke(splashScreen);
        }
      }
      catch (Exception e) {
        LOG.info(e);
      }
    }

    @Nullable
    private Object getSplashScreen() {
      //todo[nik] get rid of reflection when (if?) IDEA will be built only under jdk 1.6
      try {
        final Class<?> aClass = Class.forName("java.awt.SplashScreen");
        return aClass.getMethod("getSplashScreen").invoke(null);
      }
      catch (Exception e) {
        return null;
      }
    }

    public void main(String[] args) {

      // Event queue should not be changed during initialization of application components.
      // It also cannot be changed before initialization of application components because IdeEventQueue uses other
      // application components. So it is proper to perform replacement only here.
      ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      // app.setupIdeQueue(IdeEventQueue.getInstance());
      ((WindowManagerImpl)WindowManager.getInstance()).showFrame(args);

      app.invokeLater(new Runnable() {
        public void run() {
          if (mySplash != null) {
            mySplash.dispose();
            mySplash = null; // Allow GC collect the splash window
          }
        }
      }, ModalityState.NON_MODAL);


      app.invokeLater(new Runnable() {
        public void run() {
          if (UpdateChecker.isMyVeryFirstOpening() && UpdateChecker.checkNeeded()) {
            UpdateChecker.setMyVeryFirstOpening(false);
            updatePlugins(true);
          }

          if (myPerformProjectLoad) {
            loadProject();
          }

          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              PluginManager.reportPluginError();
            }
          });
        }
      }, ModalityState.NON_MODAL);
    }

    private void updatePlugins(boolean showConfirmation) {
      try {
        final UpdateChannel newVersion = UpdateChecker.checkForUpdates();
        final List<PluginDownloader> updatedPlugins = UpdateChecker.updatePlugins(false);
        if (newVersion != null) {
          UpdateChecker.showUpdateInfoDialog(true, newVersion, updatedPlugins);
        } else if (updatedPlugins != null) {
          UpdateChecker.showNoUpdatesDialog(true, updatedPlugins, showConfirmation);
        }
      }
      catch (ConnectionException e) {
        // It's not a problem on automatic check
      }
    }
  }

  private void loadProject() {
    Project project = null;
    if (myArgs != null && myArgs.length > 0 && myArgs[0] != null) {
      project = ProjectUtil.openOrImport(myArgs[0], null, false);
    }

    final MessageBus bus = ApplicationManager.getApplication().getMessageBus();
    bus.syncPublisher(AppLifecycleListener.TOPIC).appStarting(project);
  }

  public String[] getCommandLineArguments() {
    return myArgs;
  }

  public void setPerformProjectLoad(boolean performProjectLoad) {
    myPerformProjectLoad = performProjectLoad;
  }
}
