// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.diagnostic.*;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.*;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.MainRunner;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogEarthquakeShaker;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryKeyBean;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.SystemDock;
import com.intellij.openapi.wm.impl.WindowManagerImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.ui.AppIcon;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.CustomProtocolHandler;
import com.intellij.ui.mac.MacOSApplicationProvider;
import com.intellij.ui.mac.touchbar.TouchBarsManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public final class IdeaApplication {
  private static final String[] SAFE_JAVA_ENV_PARAMETERS = {JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY};

  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication");

  private static boolean ourPerformProjectLoad = true;

  private IdeaApplication() { }

  public static void initApplication(@NotNull String[] rawArgs) {
    Activity initAppActivity = MainRunner.startupStart.endAndStart(Phases.INIT_APP);
    CompletableFuture<List<IdeaPluginDescriptor>> pluginDescriptorsFuture = new CompletableFuture<>();
    EventQueue.invokeLater(() -> executeInitAppInEdt(rawArgs, initAppActivity, pluginDescriptorsFuture));

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

  private static void executeInitAppInEdt(@NotNull String[] rawArgs, @NotNull Activity initAppActivity,
                                          @NotNull CompletableFuture<List<IdeaPluginDescriptor>> pluginDescriptorsFuture) {
    String[] args = processProgramArguments(rawArgs);

    ApplicationStarter starter = createAppStarter(args, pluginDescriptorsFuture);

    Activity createAppActivity = initAppActivity.startChild("create app");
    boolean headless = Main.isHeadless();
    ApplicationImpl app = new ApplicationImpl(Boolean.getBoolean(PluginManagerCore.IDEA_IS_INTERNAL_PROPERTY), false, headless,
                                              Main.isCommandLine(), ApplicationManagerEx.IDEA_APPLICATION);
    createAppActivity.end();

    if (!headless) {
      // todo investigate why in test mode dummy icon manager is not suitable
      IconLoader.activate();
      IconLoader.setStrictGlobally(app.isInternal());

      if (SystemInfo.isMac) {
        Activity activity = initAppActivity.startChild("mac app init");
        MacOSApplicationProvider.initApplication();
        activity.end();
      }
    }

    starter.premain(args);

    List<Future<?>> futures = new ArrayList<>();
    futures.add(registerRegistryAndMessageBusAndComponent(pluginDescriptorsFuture, app));

    if (!headless) {
      if (SystemInfo.isMac) {
        // ensure that TouchBarsManager is loaded before WelcomeFrame/project
        futures.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
          Activity activity = ParallelActivity.PREPARE_APP_INIT.start("mac touchbar");
          //noinspection ResultOfMethodCallIgnored
          TouchBarsManager.isTouchBarAvailable();
          activity.end();
        }));
      }
      SplashManager.showLicenseeInfoOnSplash(LOG);
    }

    // this invokeLater() call is needed to place the app starting code on a freshly minted IdeEventQueue instance
    Activity placeOnEventQueueActivity = initAppActivity.startChild(Phases.PLACE_ON_EVENT_QUEUE);
    EventQueue.invokeLater(() -> {
      placeOnEventQueueActivity.end();
      StartupUtil.installExceptionHandler();
      initAppActivity.end();
      try {
        Activity activity = StartUpMeasurer.start(Phases.WAIT_PLUGIN_INIT);
        for (Future<?> future : futures) {
          future.get();
        }
        activity.end();
      }
      catch (InterruptedException | ExecutionException e) {
        throw new CompletionException(e);
      }

      app.load(null, SplashManager.getProgressIndicator());
      if (!headless) {
        addActivateAndWindowsCliListeners(app);
      }
      ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> starter.main(args));

      if (PluginManagerCore.isRunningFromSources()) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame()));
      }
    });
  }

  @NotNull
  private static CompletableFuture<Void> registerRegistryAndMessageBusAndComponent(@NotNull CompletableFuture<List<IdeaPluginDescriptor>> pluginDescriptorsFuture,
                                                                                   @NotNull ApplicationImpl app) {
    return pluginDescriptorsFuture
      .thenCompose(pluginDescriptors -> {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
          Activity activity = ParallelActivity.PREPARE_APP_INIT.start("add registry keys");
          RegistryKeyBean.addKeysFromPlugins();
          activity.end();

          Activity busActivity = ParallelActivity.PREPARE_APP_INIT.start("add message bus listeners");
          ApplicationImpl.registerMessageBusListeners(app, pluginDescriptors, false);
          busActivity.end();
        }, AppExecutorUtil.getAppExecutorService());

        Activity activity = ParallelActivity.PREPARE_APP_INIT.start("app component registration");
        ((ApplicationImpl)ApplicationManager.getApplication()).registerComponents(pluginDescriptors);
        activity.end();

        return future;
      });
  }

  private static void addActivateAndWindowsCliListeners(@NotNull ApplicationImpl app) {
    StartupUtil.addExternalInstanceListener(args -> {
      AtomicReference<Future<? extends CliResult>> ref = new AtomicReference<>();

      app.invokeAndWait(() -> {
        LOG.info("ApplicationImpl.externalInstanceListener invocation");
        String currentDirectory = args.isEmpty() ? null : args.get(0);
        List<String> realArgs = args.isEmpty() ? args : args.subList(1, args.size());
        final Pair<Project, Future<? extends CliResult>> projectAndFuture =
          CommandLineProcessor.processExternalCommandLine(realArgs, currentDirectory);

        ref.set(projectAndFuture.getSecond());
        final Project project = projectAndFuture.getFirst();
        JFrame frame = project == null ? WindowManager.getInstance().findVisibleFrame() :
                       (JFrame)WindowManager.getInstance().getIdeFrame(project);
        if (frame != null) {
          if (frame instanceof IdeFrame) {
            AppIcon.getInstance().requestFocus((IdeFrame)frame);
          } else {
            frame.toFront();
            DialogEarthquakeShaker.shake(frame);
          }
        }
      });

      return ref.get();
    });

    MainRunner.LISTENER = (currentDirectory, args) -> {
      List<String> argsList = Arrays.asList(args);
      LOG.info("Received external Windows command line: current directory " + currentDirectory + ", command line " + argsList);
      if (argsList.isEmpty()) return 0;
      ModalityState state = app.getDefaultModalityState();
      for (ApplicationStarter starter : ApplicationStarter.EP_NAME.getExtensionList()) {
        if (starter.canProcessExternalCommandLine() &&
            argsList.get(0).equals(starter.getCommandName()) &&
            starter.allowAnyModalityState()) {
          state = app.getAnyModalityState();
        }
      }
      AtomicReference<Future<? extends CliResult>> ref = new AtomicReference<>();
      app.invokeAndWait(() -> ref.set(CommandLineProcessor.processExternalCommandLine(argsList, currentDirectory).getSecond()), state);
      final CliResult result = CliResult.getOrWrapFailure(ref.get(), 1);
      return result.getReturnCode();
    };
  }

  @NotNull
  private static ApplicationStarter createAppStarter(@NotNull String[] args, @NotNull Future<?> pluginsLoaded) {
    LOG.assertTrue(!ApplicationManagerEx.isAppLoaded());
    LoadingPhase.setCurrentPhase(LoadingPhase.SPLASH);
    StartupUtil.patchSystem(LOG);
    if (args.length <= 0) {
      return new IdeStarter();
    }

    try {
      pluginsLoaded.get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new CompletionException(e);
    }

    ApplicationStarter starter = findStarter(args[0]);
    if (starter != null) {
      if (Main.isHeadless() && !starter.isHeadless()) {
        Main.showMessage("Startup Error", "Application cannot start in headless mode", true);
        System.exit(Main.NO_GRAPHICS);
      }
      return starter;
    }
    return new IdeStarter();
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
    return ArrayUtilRt.toStringArray(arguments);
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

    @NotNull
    @Override
    public Future<? extends CliResult> processExternalCommandLineAsync(@NotNull String[] args, @Nullable String currentDirectory) {
      LOG.info("Request to open in " + currentDirectory + " with parameters: " + StringUtil.join(args, ","));

      if (args.length > 0) {
        String filename = args[0];
        File file = new File(currentDirectory, filename);

        if (file.exists()) {
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
        return CliResult.error(1, "Can't find file:" + file);
      }
      return CliResult.ok();
    }

    private static Project loadProjectFromExternalCommandLine(@NotNull List<String> commandLineArgs) {
      Project project = null;
      if (!commandLineArgs.isEmpty() && commandLineArgs.get(0) != null) {
        LOG.info("IdeaApplication.loadProject");
        project = CommandLineProcessor.processExternalCommandLine(commandLineArgs, null).getFirst();
      }
      return project;
    }

    @Override
    public void main(String[] args) {
      Activity frameInitActivity = StartUpMeasurer.start(Phases.FRAME_INITIALIZATION);

      GcPauseWatcher.Companion.getInstance();

      // Event queue should not be changed during initialization of application components.
      // It also cannot be changed before initialization of application components because IdeEventQueue uses other
      // application components. So it is proper to perform replacement only here.
      Activity setWindowManagerActivity = frameInitActivity.startChild("set window manager");
      Application app = ApplicationManager.getApplication();
      WindowManagerImpl windowManager = (WindowManagerImpl)WindowManager.getInstance();
      IdeEventQueue.getInstance().setWindowManager(windowManager);
      setWindowManagerActivity.end();

      List<String> commandLineArgs = args == null || args.length == 0 ? Collections.emptyList() : Arrays.asList(args);

      Activity appFrameCreatedActivity = frameInitActivity.startChild("call appFrameCreated");
      AppLifecycleListener lifecyclePublisher = app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
      lifecyclePublisher.appFrameCreated(commandLineArgs);
      appFrameCreatedActivity.end();

      // must be after appFrameCreated because some listeners can mutate state of RecentProjectsManager
      boolean willOpenProject = !commandLineArgs.isEmpty() || RecentProjectsManager.getInstance().willReopenProjectOnStart();

      // Temporary check until the jre implementation has been checked and bundled
      if (Registry.is("ide.popup.enablePopupType")) {
        System.setProperty("jbre.popupwindow.settype", "true");
      }

      LoadingPhase.setCurrentPhase(LoadingPhase.FRAME_SHOWN);

      if (!willOpenProject || JetBrainsProtocolHandler.getCommand() != null) {
        WelcomeFrame.showNow(SplashManager.getHideTask());
        lifecyclePublisher.welcomeScreenDisplayed();
      }

      frameInitActivity.end();

      app.executeOnPooledThread(() -> LifecycleUsageTriggerCollector.onIdeStart());

      TransactionGuard.submitTransaction(app, () -> {
        Project projectFromCommandLine = ourPerformProjectLoad ? loadProjectFromExternalCommandLine(commandLineArgs) : null;
        app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).appStarting(projectFromCommandLine);
        if (projectFromCommandLine == null && !JetBrainsProtocolHandler.appStartedWithCommand()) {
          RecentProjectsManager.getInstance().reopenLastProjectOnStart();
        }

        //noinspection SSBasedInspection
        EventQueue.invokeLater(PluginManager::reportPluginError);
      });

      if (!app.isHeadlessEnvironment()) {
        postOpenUiTasks(app);
      }
    }

    private static void postOpenUiTasks(@NotNull Application app) {
      if (SystemInfo.isMac) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          TouchBarsManager.onApplicationInitialized();
          CustomActionsSchema customActionSchema = ServiceManager.getServiceIfCreated(CustomActionsSchema.class);
          if (customActionSchema != null) {
            customActionSchema.touchBarAvailable(TouchBarsManager.isTouchBarAvailable());
          }
        });
      }

      app.invokeLater(() -> {
        Activity updateSystemDockActivity = StartUpMeasurer.start("system dock menu");
        SystemDock.updateMenu();
        updateSystemDockActivity.end();
      });
      app.invokeLater(() -> {
        GeneralSettings generalSettings = GeneralSettings.getInstance();
        generalSettings.addPropertyChangeListener(GeneralSettings.PROP_SUPPORT_SCREEN_READERS, app,
                                                  e -> ScreenReader.setActive((Boolean)e.getNewValue()));
        ScreenReader.setActive(generalSettings.isSupportScreenReaders());
      });
    }
  }

  public static void disableProjectLoad() {
    ourPerformProjectLoad = false;
  }
}