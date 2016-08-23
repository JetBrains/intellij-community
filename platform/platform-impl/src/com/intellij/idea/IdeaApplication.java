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

import com.intellij.ExtensionPoints;
import com.intellij.Patches;
import com.intellij.ide.*;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.X11UiUtil;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.ui.CustomProtocolHandler;
import com.intellij.ui.Splash;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IdeaApplication {
  @NonNls public static final String IDEA_IS_INTERNAL_PROPERTY = "idea.is.internal";
  @NonNls public static final String IDEA_IS_UNIT_TEST = "idea.is.unit.test";

  private static final String[] SAFE_JAVA_ENV_PARAMETERS = {"idea.required.plugins.id"};

  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication");

  private static IdeaApplication ourInstance;

  public static IdeaApplication getInstance() {
    return ourInstance;
  }

  public static boolean isLoaded() {
    return ourInstance != null && ourInstance.myLoaded;
  }

  private final String[] myArgs;
  private boolean myPerformProjectLoad = true;
  private ApplicationStarter myStarter;
  private volatile boolean myLoaded = false;

  public IdeaApplication(String[] args) {
    LOG.assertTrue(ourInstance == null);
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourInstance = this;
    myArgs = processProgramArguments(args);
    boolean isInternal = Boolean.getBoolean(IDEA_IS_INTERNAL_PROPERTY);
    boolean isUnitTest = Boolean.getBoolean(IDEA_IS_UNIT_TEST);

    boolean headless = Main.isHeadless();
    patchSystem(headless);

    if (Main.isCommandLine()) {
      if (CommandLineApplication.ourInstance == null) {
        new CommandLineApplication(isInternal, isUnitTest, headless);
      }
      if (isUnitTest) {
        myLoaded = true;
      }
    }
    else {
      Splash splash = null;
      if (myArgs.length == 0) {
        myStarter = getStarter();
        if (myStarter instanceof IdeStarter) {
          splash = ((IdeStarter)myStarter).showSplash(myArgs);
        }
      }

      ApplicationManagerEx.createApplication(isInternal, isUnitTest, false, false, ApplicationManagerEx.IDEA_APPLICATION, splash);
    }

    if (myStarter == null) {
      myStarter = getStarter();
    }

    if (headless && myStarter instanceof ApplicationStarterEx && !((ApplicationStarterEx)myStarter).isHeadless()) {
      Main.showMessage("Startup Error", "Application cannot start in headless mode", true);
      System.exit(Main.NO_GRAPHICS);
    }

    myStarter.premain(args);
  }

  /**
   * Method looks for -Dkey=value program arguments and stores some of them in system properties.
   * We should use it for a limited number of safe keys.
   * One of them is a list of ids of required plugins
   *
   * @see IdeaApplication#SAFE_JAVA_ENV_PARAMETERS
   */
  @NotNull
  private static String[] processProgramArguments(String[] args) {
    ArrayList<String> arguments = new ArrayList<>();
    List<String> safeKeys = Arrays.asList(SAFE_JAVA_ENV_PARAMETERS);
    for (String arg : args) {
      if (arg.startsWith("-D")) {
        String[] keyValue = arg.substring(2).split("=");
        if (keyValue.length == 2 && safeKeys.contains(keyValue[0])) {
          System.setProperty(keyValue[0], keyValue[1]);
          continue;
        }
      }
      arguments.add(arg);
    }
    return ArrayUtil.toStringArray(arguments);
  }

  private static void patchSystem(boolean headless) {
    System.setProperty("sun.awt.noerasebackground", "true");

    IdeEventQueue.getInstance(); // replace system event queue
    
    if (headless) return;

    if (Patches.SUN_BUG_ID_6209673) {
      RepaintManager.setCurrentManager(new IdeRepaintManager());
    }

    if (SystemInfo.isXWindow) {
      String wmName = X11UiUtil.getWmName();
      LOG.info("WM detected: " + wmName);
      if (wmName != null) {
        X11UiUtil.patchDetectedWm(wmName);
      }
    }

    IconLoader.activate();

    new JFrame().pack(); // this peer will prevent shutting down our application
  }

  @NotNull
  public ApplicationStarter getStarter() {
    if (myArgs.length > 0) {
      PluginManagerCore.getPlugins();

      ExtensionPoint<ApplicationStarter> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.APPLICATION_STARTER);
      ApplicationStarter[] starters = point.getExtensions();
      String key = myArgs[0];
      for (ApplicationStarter o : starters) {
        if (Comparing.equal(o.getCommandName(), key)) return o;
      }
    }

    return new IdeStarter();
  }

  public void run() {
    try {
      ApplicationManagerEx.getApplicationEx().load();
      myLoaded = true;

      ((TransactionGuardImpl) TransactionGuard.getInstance()).performUserActivity(() -> myStarter.main(myArgs));
      myStarter = null; //GC it
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initLAF() {
    try {
      Class.forName("com.jgoodies.looks.plastic.PlasticLookAndFeel");

      if (SystemInfo.isWindows) {
        UIManager.installLookAndFeel("JGoodies Windows L&F", "com.jgoodies.looks.windows.WindowsLookAndFeel");
      }

      UIManager.installLookAndFeel("JGoodies Plastic", "com.jgoodies.looks.plastic.PlasticLookAndFeel");
      UIManager.installLookAndFeel("JGoodies Plastic 3D", "com.jgoodies.looks.plastic.Plastic3DLookAndFeel");
      UIManager.installLookAndFeel("JGoodies Plastic XP", "com.jgoodies.looks.plastic.PlasticXPLookAndFeel");
    }
    catch (ClassNotFoundException ignored) { }
  }

  protected class IdeStarter extends ApplicationStarterEx {
    private Splash mySplash;

    @Override
    public boolean isHeadless() {
      return false;
    }

    @Override
    public String getCommandName() {
      return null;
    }

    @Override
    public void premain(String[] args) {
      initLAF();
    }

    @Nullable
    private Splash showSplash(String[] args) {
      if (StartupUtil.shouldShowSplash(args)) {
        final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
        final SplashScreen splashScreen = getSplashScreen();
        if (splashScreen == null) {
          mySplash = new Splash(appInfo);
          mySplash.show();
          return mySplash;
        }
        else {
          updateSplashScreen(appInfo, splashScreen);
        }
      }
      return null;
    }

    private void updateSplashScreen(ApplicationInfoEx appInfo, SplashScreen splashScreen) {
      final Graphics2D graphics = splashScreen.createGraphics();
      final Dimension size = splashScreen.getSize();
      if (Splash.showLicenseeInfo(graphics, 0, 0, size.height, appInfo.getSplashTextColor())) {
        splashScreen.update();
      }
    }

    @Nullable
    private SplashScreen getSplashScreen() {
      try {
        return SplashScreen.getSplashScreen();
      }
      catch (Throwable t) {
        LOG.warn(t);
        return null;
      }
    }

    @Override
    public boolean canProcessExternalCommandLine() {
      return true;
    }

    @Override
    public void processExternalCommandLine(String[] args, @Nullable String currentDirectory) {
      LOG.info("Request to open in " + currentDirectory + " with parameters: " + StringUtil.join(args, ","));

      if (args.length > 0) {
        String filename = args[0];
        File file = new File(currentDirectory, filename);

        if(file.exists()) {
          VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
          if (virtualFile != null) {
            int line = -1;
            if (args.length > 2 && CustomProtocolHandler.LINE_NUMBER_ARG_NAME.equals(args[1])) {
              try {
                line = Integer.parseInt(args[2]);
              } catch (NumberFormatException ex) {
                LOG.error("Wrong line number:" + args[2]);
              }
            }
            PlatformProjectOpenProcessor.doOpenProject(virtualFile, null, false, line, null, false);
          }
        }
        throw new IncorrectOperationException("Can't find file:" + file);
      }
    }

    @Override
    public void main(String[] args) {
      SystemDock.updateMenu();

      // if OS has dock, RecentProjectsManager will be already created, but not all OS have dock, so, we trigger creation here to ensure that RecentProjectsManager app listener will be added
      RecentProjectsManager.getInstance();

      // Event queue should not be changed during initialization of application components.
      // It also cannot be changed before initialization of application components because IdeEventQueue uses other
      // application components. So it is proper to perform replacement only here.
      final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
      IdeEventQueue.getInstance().setWindowManager(windowManager);

      Ref<Boolean> willOpenProject = new Ref<>(Boolean.FALSE);
      AppLifecycleListener lifecyclePublisher = app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
      lifecyclePublisher.appFrameCreated(args, willOpenProject);

      LOG.info("App initialization took " + (System.nanoTime() - PluginManager.startupStart) / 1000000 + " ms");
      PluginManagerCore.dumpPluginClassStatistics();

      if (JetBrainsProtocolHandler.getCommand() != null || !willOpenProject.get()) {
        WelcomeFrame.showNow();
        lifecyclePublisher.welcomeScreenDisplayed();
      }
      else {
        windowManager.showFrame();
      }

      app.invokeLater(() -> {
        if (mySplash != null) {
          mySplash.dispose();
          mySplash = null; // Allow GC collect the splash window
        }
      }, ModalityState.any());

      TransactionGuard.submitTransaction(app, () -> {
        Project projectFromCommandLine = myPerformProjectLoad ? loadProjectFromExternalCommandLine() : null;
        app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).appStarting(projectFromCommandLine);

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(PluginManager::reportPluginError);

        //safe for headless and unit test modes
        UsageTrigger.trigger(app.getName() + "app.started");
      });
    }
  }

  private Project loadProjectFromExternalCommandLine() {
    Project project = null;
    if (myArgs != null && myArgs.length > 0 && myArgs[0] != null) {
      LOG.info("IdeaApplication.loadProject");
      project = CommandLineProcessor.processExternalCommandLine(Arrays.asList(myArgs), null);
    }
    return project;
  }

  public String[] getCommandLineArguments() {
    return myArgs;
  }

  public void setPerformProjectLoad(boolean performProjectLoad) {
    myPerformProjectLoad = performProjectLoad;
  }
}
