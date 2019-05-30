// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.Patches;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.diagnostic.*;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.*;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.MainRunner;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogEarthquakeShaker;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.X11UiUtil;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.ui.AppIcon;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.CustomProtocolHandler;
import com.intellij.ui.mac.MacOSApplicationProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

public final class IdeaApplication {
  private static final String[] SAFE_JAVA_ENV_PARAMETERS = {JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY};

  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication");

  private static boolean ourPerformProjectLoad = true;

  private IdeaApplication() {
  }

  public static void initApplication(@NotNull String[] rawArgs) {
    Activity activity = MainRunner.startupStart.endAndStart(Phases.INIT_APP);
    CompletableFuture<List<IdeaPluginDescriptor>> pluginDescriptorsFuture = new CompletableFuture<>();
    EventQueue.invokeLater(() -> {
      String[] args = processProgramArguments(rawArgs);
      ApplicationStarter starter = createAppStarter(args, pluginDescriptorsFuture);

      CompletableFuture<Void> registerComponentsFuture = pluginDescriptorsFuture
        .thenAccept(pluginDescriptors -> {
          Activity activity1 = ParallelActivity.PREPARE_APP_INIT.start("app component registration");
          ((ApplicationImpl)ApplicationManager.getApplication()).registerComponents(pluginDescriptors);
          activity1.end();
        });

      if (!Main.isHeadless()) {
        SplashManager.showLicenseeInfoOnSplash(LOG);
      }

      // this invokeLater() call is needed to place the app starting code on a freshly minted IdeEventQueue instance
      Activity placeOnEventQueueActivity = activity.startChild(Phases.PLACE_ON_EVENT_QUEUE);
      EventQueue.invokeLater(() -> {
        placeOnEventQueueActivity.end();
        PluginManager.installExceptionHandler();
        activity.end();
        try {
          Activity activity1 = StartUpMeasurer.start(Phases.WAIT_PLUGIN_INIT);
          registerComponentsFuture.get();
          activity1.end();
        }
        catch (InterruptedException | ExecutionException e) {
          throw new CompletionException(e);
        }

        ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
        app.load(null, SplashManager.getProgressIndicator());
        if (!app.isUnitTestMode() && !Main.isHeadless()) {
          addActivateAndWindowsCliListeners(app);
        }
        ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> starter.main(args));

        if (PluginManagerCore.isRunningFromSources()) {
          app.invokeLater(() -> AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame()));
        }
      });
    });

    List<IdeaPluginDescriptor> plugins;
    try {
      plugins = PluginManagerCore.getLoadedPlugins();
    }
    catch (Throwable e) {
      pluginDescriptorsFuture.completeExceptionally(e);
      return;
    }
    pluginDescriptorsFuture.complete(plugins);
  }

  private static void addActivateAndWindowsCliListeners(@NotNull ApplicationImpl app) {
    StartupUtil.addExternalInstanceListener(args -> app.invokeLater(() -> {
      LOG.info("ApplicationImpl.externalInstanceListener invocation");
      String currentDirectory = args.isEmpty() ? null : args.get(0);
      List<String> realArgs = args.isEmpty() ? args : args.subList(1, args.size());
      Project project = CommandLineProcessor.processExternalCommandLine(realArgs, currentDirectory);
      JFrame frame = project == null
                     ? WindowManager.getInstance().findVisibleFrame() :
                     (JFrame)WindowManager.getInstance().getIdeFrame(project);
      if (frame != null) {
        if (frame instanceof IdeFrame) {
          AppIcon.getInstance().requestFocus((IdeFrame)frame);
        }
        else {
          frame.toFront();
          DialogEarthquakeShaker.shake(frame);
        }
      }
    }));

    MainRunner.LISTENER = (currentDirectory, args) -> {
      List<String> argsList = Arrays.asList(args);
      LOG.info("Received external Windows command line: current directory " + currentDirectory + ", command line " + argsList);
      if (argsList.isEmpty()) return;
      ModalityState state = app.getDefaultModalityState();
      for (ApplicationStarter starter : ApplicationStarter.EP_NAME.getExtensionList()) {
        if (starter.canProcessExternalCommandLine() &&
            argsList.get(0).equals(starter.getCommandName()) &&
            starter.allowAnyModalityState()) {
          state = app.getAnyModalityState();
        }
      }
      app.invokeLater(() -> CommandLineProcessor.processExternalCommandLine(argsList, currentDirectory), state);
    };
  }

  @NotNull
  public static ApplicationStarter createAppStarter(@NotNull String[] args, @Nullable Future<?> pluginsLoaded) {
    LOG.assertTrue(!ApplicationManagerEx.isAppLoaded());

    {
      LoadingPhase.setCurrentPhase(LoadingPhase.SPLASH);
      Activity activity = StartUpMeasurer.start("patch system");
      patchSystem(Main.isHeadless());
      activity.end();
    }

    ApplicationStarter starter = getStarter(args, pluginsLoaded);

    boolean headless = Main.isHeadless();
    if (headless && !starter.isHeadless()) {
      Main.showMessage("Startup Error", "Application cannot start in headless mode", true);
      System.exit(Main.NO_GRAPHICS);
    }

    boolean isInternal = Boolean.getBoolean(ApplicationImpl.IDEA_IS_INTERNAL_PROPERTY);
    boolean isUnitTest = Boolean.getBoolean(ApplicationImpl.IDEA_IS_UNIT_TEST);

    if (Main.isCommandLine()) {
      if (CommandLineApplication.ourInstance == null) {
        new CommandLineApplication(isInternal, isUnitTest, headless);
      }
    }
    else {
      Activity activity = StartUpMeasurer.start("create app");
      new ApplicationImpl(isInternal, isUnitTest, false, false, ApplicationManagerEx.IDEA_APPLICATION);
      activity.end();
    }

    starter.premain(args);
    return starter;
  }

  /**
   * Method looks for -Dkey=value program arguments and stores some of them in system properties.
   * We should use it for a limited number of safe keys.
   * One of them is a list of ids of required plugins
   *
   * @see IdeaApplication#SAFE_JAVA_ENV_PARAMETERS
   */
  @NotNull
  public static String[] processProgramArguments(@NotNull String[] args) {
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
      if (SplashManager.NO_SPLASH.equals(arg)) {
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

    if (SystemProperties.getBooleanProperty("idea.app.use.fake.frame", false)) {
      // this peer will prevent shutting down our application
      new JFrame().pack();
    }
  }

  @NotNull
  private static ApplicationStarter getStarter(@NotNull String[] args, @Nullable Future<?> pluginsLoaded) {
    if (args.length > 0) {
      if (pluginsLoaded == null) {
        PluginManagerCore.getPlugins();
      }
      else {
        try {
          pluginsLoaded.get();
        }
        catch (InterruptedException | ExecutionException e) {
          throw new CompletionException(e);
        }
      }

      ApplicationStarter starter = findStarter(args[0]);
      if (starter != null) {
        return starter;
      }
    }

    return new IdeStarter();
  }

  @Nullable
  public static ApplicationStarter findStarter(@Nullable String key) {
    for (ApplicationStarter starter : ApplicationStarter.EP_NAME.getIterable(null)) {
      if (starter == null) {
        break;
      }

      if (Comparing.equal(starter.getCommandName(), key)) {
        return starter;
      }
    }
    return null;
  }

  public static class IdeStarter implements ApplicationStarter {
    @Override
    public boolean isHeadless() {
      return false;
    }

    @Override
    public String getCommandName() {
      return null;
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

    private static Project loadProjectFromExternalCommandLine(@NotNull List<String> commandLineArgs) {
      Project project = null;
      if (!commandLineArgs.isEmpty() && commandLineArgs.get(0) != null) {
        LOG.info("IdeaApplication.loadProject");
        project = CommandLineProcessor.processExternalCommandLine(commandLineArgs, null);
      }
      return project;
    }

    @Override
    public void main(String[] args) {
      Activity activity = StartUpMeasurer.start(Phases.FRAME_INITIALIZATION);
      if (SystemInfoRt.isMac) {
        MacOSApplicationProvider.initApplication();
      }

      SystemDock.updateMenu();

      RecentProjectsManager.getInstance();  // ensures that RecentProjectsManager app listener is added
      GcPauseWatcher.Companion.getInstance();

      // Event queue should not be changed during initialization of application components.
      // It also cannot be changed before initialization of application components because IdeEventQueue uses other
      // application components. So it is proper to perform replacement only here.
      Application app = ApplicationManager.getApplication();
      WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
      IdeEventQueue.getInstance().setWindowManager(windowManager);

      List<String> commandLineArgs = args == null || args.length == 0 ? Collections.emptyList() : Arrays.asList(args);

      Ref<Boolean> willOpenProject = new Ref<>(Boolean.FALSE);
      AppLifecycleListener lifecyclePublisher = app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
      lifecyclePublisher.appFrameCreated(commandLineArgs, willOpenProject);

      PluginManagerCore.dumpPluginClassStatistics();

      // Temporary check until the jre implementation has been checked and bundled
      if (Registry.is("ide.popup.enablePopupType")) {
        System.setProperty("jbre.popupwindow.settype", "true");
      }

      LoadingPhase.setCurrentPhase(LoadingPhase.FRAME_SHOWN);

      Runnable beforeSetVisible = SplashManager.getHideTask();

      if (JetBrainsProtocolHandler.getCommand() != null || !willOpenProject.get()) {
        WelcomeFrame.showNow(beforeSetVisible);
        lifecyclePublisher.welcomeScreenDisplayed();
      }
      else {
        windowManager.showFrame(beforeSetVisible);
      }

      activity.end();

      TransactionGuard.submitTransaction(app, () -> {
        Project projectFromCommandLine = ourPerformProjectLoad ? loadProjectFromExternalCommandLine(commandLineArgs) : null;
        // The appStarting callback in RecentProjectsManagerBase will reopen the last project
        app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).appStarting(projectFromCommandLine);

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(PluginManager::reportPluginError);

        LifecycleUsageTriggerCollector.onIdeStart();
      });
    }
  }

  public static void disableProjectLoad() {
    ourPerformProjectLoad = false;
  }
}