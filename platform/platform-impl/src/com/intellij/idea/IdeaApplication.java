// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.Patches;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.*;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
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
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static com.intellij.openapi.application.JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY;

public class IdeaApplication {
  public static final String IDEA_IS_INTERNAL_PROPERTY = "idea.is.internal";
  public static final String IDEA_IS_UNIT_TEST = "idea.is.unit.test";

  private static final String[] SAFE_JAVA_ENV_PARAMETERS = {REQUIRED_PLUGINS_KEY};

  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication");

  private static IdeaApplication ourInstance;

  public static IdeaApplication getInstance() {
    return ourInstance;
  }

  public static boolean isLoaded() {
    return ourInstance != null && ourInstance.myLoaded;
  }

  @SuppressWarnings("SSBasedInspection")
  public static void initApplication(@NotNull String[] args) {
    Activity activity = PluginManager.startupStart.endAndStart(Phases.INIT_APP);
    IdeaApplication app = new IdeaApplication(args);

    // this invokeLater() call is needed to place the app starting code on a freshly minted IdeEventQueue instance
    Activity placeOnEventQueueActivity = activity.startChild(Phases.PLACE_ON_EVENT_QUEUE);
    SwingUtilities.invokeLater(() -> {
      PluginManager.installExceptionHandler();
      placeOnEventQueueActivity.end();
      activity.end();
      // this run is blocking, while app is running
      app.run();
    });
  }

  private final @NotNull String[] myArgs;
  private static boolean myPerformProjectLoad = true;
  private ApplicationStarter myStarter;
  private volatile boolean myLoaded;

  public IdeaApplication(@NotNull String[] args) {
    LOG.assertTrue(ourInstance == null);
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourInstance = this;

    myArgs = processProgramArguments(args);
    boolean isInternal = Boolean.getBoolean(IDEA_IS_INTERNAL_PROPERTY);
    boolean isUnitTest = Boolean.getBoolean(IDEA_IS_UNIT_TEST);
    boolean isShowSplash = !Boolean.getBoolean(StartupUtil.NO_SPLASH);

    boolean headless = Main.isHeadless();
    patchSystem(headless);

    myStarter = getStarter();

    if (Main.isCommandLine()) {
      if (CommandLineApplication.ourInstance == null) {
        new CommandLineApplication(isInternal, isUnitTest, headless);
      }
      if (isUnitTest) {
        myLoaded = true;
      }
    }
    else {
      if (isShowSplash && myStarter instanceof IdeStarter) {
        ((IdeStarter)myStarter).showSplash();
      }

      new ApplicationImpl(isInternal, isUnitTest, false, false, ApplicationManagerEx.IDEA_APPLICATION);
    }

    if (headless && myStarter instanceof ApplicationStarterEx && !((ApplicationStarterEx)myStarter).isHeadless()) {
      Main.showMessage("Startup Error", "Application cannot start in headless mode", true);
      System.exit(Main.NO_GRAPHICS);
    }

    myStarter.premain(myArgs);
  }

  /**
   * Method looks for -Dkey=value program arguments and stores some of them in system properties.
   * We should use it for a limited number of safe keys.
   * One of them is a list of ids of required plugins
   *
   * @see IdeaApplication#SAFE_JAVA_ENV_PARAMETERS
   */
  @NotNull
  private static String[] processProgramArguments(@NotNull String[] args) {
    List<String> arguments = new ArrayList<>();
    List<String> safeKeys = Arrays.asList(SAFE_JAVA_ENV_PARAMETERS);
    for (String arg : args) {
      if (arg.startsWith("-D")) {
        String[] keyValue = arg.substring(2).split("=");
        if (keyValue.length == 2 && safeKeys.contains(keyValue[0])) {
          System.setProperty(keyValue[0], keyValue[1]);
          continue;
        }
      }
      if (StartupUtil.NO_SPLASH.equals(arg)) {
        System.setProperty(StartupUtil.NO_SPLASH, "true");
        continue;
      }
      arguments.add(arg);
    }
    return ArrayUtil.toStringArray(arguments);
  }

  private static void patchSystem(boolean headless) {
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(headless);
    LOG.info("CPU cores: " + Runtime.getRuntime().availableProcessors() +
             "; ForkJoinPool.commonPool: " + ForkJoinPool.commonPool() +
             "; factory: " + ForkJoinPool.commonPool().getFactory());

    System.setProperty("sun.awt.noerasebackground", "true");

    //noinspection ResultOfMethodCallIgnored
    IdeEventQueue.getInstance();  // replaces system event queue

    if (headless) return;

    /* Using custom RepaintManager disables BufferStrategyPaintManager (and so, true double buffering)
       because the only non-private constructor forces RepaintManager.BUFFER_STRATEGY_TYPE = BUFFER_STRATEGY_SPECIFIED_OFF.

       At the same time, http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6209673 seems to be now fixed.

       This matters only if swing.bufferPerWindow = true and we don't invoke JComponent.getGraphics() directly.

       True double buffering is needed to eliminate tearing on blit-accelerated scrolling and to restore
       frame buffer content without the usual repainting, even when the EDT is blocked. */
    if (Patches.REPAINT_MANAGER_LEAK) {
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

    // isStartParallel doesn't affect this functionality, but isStartParallel flag is used as default value for this flag
    if (SystemProperties.getBooleanProperty("idea.app.use.fake.frame", !StartupUtil.isStartParallel())) {
      // this peer will prevent shutting down our application
      new JFrame().pack();
    }
  }

  @NotNull
  public ApplicationStarter getStarter() {
    if (myArgs.length > 0) {
      PluginManagerCore.getPlugins();

      ExtensionPoint<ApplicationStarter> point = ApplicationStarter.EP_NAME.getPoint(null);
      String key = myArgs[0];
      for (ApplicationStarter o : point.getExtensionList()) {
        if (Comparing.equal(o.getCommandName(), key)) {
          return o;
        }
      }
    }

    return new IdeStarter();
  }

  private void run() {
    Splash splash = myStarter instanceof IdeStarter ? ((IdeStarter)myStarter).mySplash : null;
    ((ApplicationImpl)ApplicationManager.getApplication()).load(null, splash);
    myLoaded = true;

    ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> myStarter.main(myArgs));
    // GC it
    myStarter = null;
  }

  public static class IdeStarter extends ApplicationStarterEx {
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
    }

    @Nullable
    private Splash showSplash() {
      final ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
      final SplashScreen splashScreen = getSplashScreen();
      if (splashScreen == null) {
        mySplash = new Splash(appInfo);
        mySplash.show();
        return mySplash;
      }
      else {
        updateSplashScreen(appInfo, splashScreen);
        return null;
      }
    }

    private static void updateSplashScreen(@NotNull ApplicationInfoEx appInfo, @NotNull SplashScreen splashScreen) {
      final Graphics2D graphics = splashScreen.createGraphics();
      final Dimension size = splashScreen.getSize();
      if (Splash.showLicenseeInfo(graphics, 0, 0, size.height, appInfo.getSplashTextColor(), appInfo)) {
        splashScreen.update();
      }
    }

    @Nullable
    private static SplashScreen getSplashScreen() {
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
    public void processExternalCommandLine(@NotNull String[] args, @Nullable String currentDirectory) {
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
            EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class);
            PlatformProjectOpenProcessor.doOpenProject(virtualFile, null, line, null, options);
          }
        }
        throw new IncorrectOperationException("Can't find file:" + file);
      }
    }

    private static Project loadProjectFromExternalCommandLine(String[] args) {
      Project project = null;
      if (args != null && args.length > 0 && args[0] != null) {
        LOG.info("IdeaApplication.loadProject");
        project = CommandLineProcessor.processExternalCommandLine(Arrays.asList(args), null);
      }
      return project;
    }

    @Override
    public void main(String[] args) {
      SystemDock.updateMenu();

      RecentProjectsManager.getInstance();  // ensures that RecentProjectsManager app listener is added

      // Event queue should not be changed during initialization of application components.
      // It also cannot be changed before initialization of application components because IdeEventQueue uses other
      // application components. So it is proper to perform replacement only here.
      final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
      IdeEventQueue.getInstance().setWindowManager(windowManager);

      Ref<Boolean> willOpenProject = new Ref<>(Boolean.FALSE);
      AppLifecycleListener lifecyclePublisher = app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
      lifecyclePublisher.appFrameCreated(args, willOpenProject);

      PluginManagerCore.dumpPluginClassStatistics();

      // Temporary check until the jre implementation has been checked and bundled
      if (Registry.is("ide.popup.enablePopupType")) {
        System.setProperty("jbre.popupwindow.settype", "true");
      }

      if (JetBrainsProtocolHandler.getCommand() != null || !willOpenProject.get()) {
        WelcomeFrame.showNow();
        lifecyclePublisher.welcomeScreenDisplayed();
      }
      else {
        windowManager.showFrame();
      }

      if (mySplash != null) {
        app.invokeLater(() -> {
          mySplash.dispose();
          mySplash = null; // Allow GC collect the splash window
        }, ModalityState.any());
      }

      TransactionGuard.submitTransaction(app, () -> {
        Project projectFromCommandLine = myPerformProjectLoad ? loadProjectFromExternalCommandLine(args) : null;
        app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).appStarting(projectFromCommandLine);

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(PluginManager::reportPluginError);

        LifecycleUsageTriggerCollector.onIdeStart();
      });
    }
  }

  /**
   * Used for GUI tests to stop IdeEventQueue dispatching when Application is disposed already
   */
  public void shutdown() {
    myLoaded = false;
    IdeEventQueue.applicationClose();
    ShutDownTracker.getInstance().run();
  }

  @NotNull
  public String[] getCommandLineArguments() {
    return myArgs;
  }

  public void disableProjectLoad() {
    myPerformProjectLoad = false;
  }
}