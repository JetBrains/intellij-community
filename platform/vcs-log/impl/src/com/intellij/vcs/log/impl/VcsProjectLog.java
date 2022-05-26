// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.intellij.vcs.log.VcsLogProvider.LOG_PROVIDER_EP;
import static com.intellij.vcs.log.impl.CustomVcsLogUiFactoryProvider.LOG_CUSTOM_UI_FACTORY_PROVIDER_EP;
import static com.intellij.vcs.log.util.PersistentUtil.LOG_CACHE;
import static java.util.Objects.requireNonNull;

@Service(Service.Level.PROJECT)
public final class VcsProjectLog implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsProjectLog.class);
  @Topic.ProjectLevel
  public static final Topic<ProjectLogListener> VCS_PROJECT_LOG_CHANGED = new Topic<>(ProjectLogListener.class,
                                                                                      Topic.BroadcastDirection.NONE,
                                                                                      true);
  private static final int RECREATE_LOG_TRIES = 5;
  @NotNull private final Project myProject;
  @NotNull private final VcsLogTabsProperties myUiProperties;
  @NotNull private final VcsLogTabsManager myTabsManager;

  @NotNull private final LazyVcsLogManager myLogManager = new LazyVcsLogManager();
  @NotNull private final Disposable myDisposable = Disposer.newDisposable();
  @NotNull private final ExecutorService myExecutor;
  @NotNull private final AtomicBoolean myDisposeStarted = new AtomicBoolean(false);
  private int myRecreatedLogCount = 0;

  public VcsProjectLog(@NotNull Project project) {
    myProject = project;

    VcsLogProjectTabsProperties uiProperties = myProject.getService(VcsLogProjectTabsProperties.class);
    myUiProperties = uiProperties;
    myTabsManager = new VcsLogTabsManager(project, uiProperties, this);

    myExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Vcs Log Initialization/Dispose", 1);
    myProject.getMessageBus().connect(myDisposable).subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        if (myProject == project) {
          shutDown();
        }
      }
    });
    ShutDownTracker.getInstance().registerShutdownTask(this::shutDown, myDisposable);
  }

  private void shutDown() {
    if (myDisposeStarted.compareAndSet(false, true)) {
      Disposer.dispose(myDisposable);
      disposeLog(false);
      myExecutor.shutdown();
      Runnable awaitDisposal = () -> {
        try {
          myExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException ignored) {
        }
      };
      if (ApplicationManager.getApplication().isDispatchThread()) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(awaitDisposal,
                                                                          VcsLogBundle.message("vcs.log.closing.process"),
                                                                          false, myProject);
      }
      else {
        awaitDisposal.run();
      }
    }
  }

  private void subscribeToMappingsAndPluginsChanges() {
    MessageBusConnection connection = myProject.getMessageBus().connect(myDisposable);
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, () -> disposeLog(true));
    connection.subscribe(DynamicPluginListener.TOPIC, new MyDynamicPluginUnloader());
  }

  @Nullable
  public VcsLogData getDataManager() {
    VcsLogManager cached = myLogManager.getCached();
    if (cached == null) return null;
    return cached.getDataManager();
  }

  /**
   * The instance of the {@link MainVcsLogUi} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getMainLogUi() {
    VcsLogContentProvider logContentProvider = VcsLogContentProvider.getInstance(myProject);
    if (logContentProvider == null) return null;
    return (VcsLogUiImpl)logContentProvider.getUi();
  }

  @Nullable
  public VcsLogManager getLogManager() {
    return myLogManager.getCached();
  }

  @NotNull
  public VcsLogTabsManager getTabsManager() {
    return myTabsManager;
  }

  @RequiresEdt
  @Nullable
  public MainVcsLogUi openLogTab(@NotNull VcsLogFilterCollection filters) {
    return openLogTab(filters, VcsLogTabLocation.TOOL_WINDOW);
  }

  @RequiresEdt
  @Nullable
  public MainVcsLogUi openLogTab(@NotNull VcsLogFilterCollection filters,
                                 @NotNull VcsLogTabLocation location) {
    VcsLogManager logManager = getLogManager();
    if (logManager == null) return null;
    return myTabsManager.openAnotherLogTab(logManager, filters, location);
  }

  @CalledInAny
  private void disposeLog(boolean recreate) {
    myExecutor.execute(() -> {
      VcsLogManager logManager = invokeAndWait(() -> {
        VcsLogManager manager = myLogManager.dropValue();
        if (manager != null) {
          manager.disposeUi();
        }
        return manager;
      });
      if (logManager != null) {
        Disposer.dispose(logManager);
      }
      if (recreate) {
        createLog(false);
      }
    });
  }

  @RequiresEdt
  private void recreateOnError(@NotNull Throwable t) {
    if (myDisposeStarted.get()) return;

    myRecreatedLogCount++;
    String logMessage = "Recreating Vcs Log after storage corruption. Recreated count " + myRecreatedLogCount;
    if (myRecreatedLogCount % RECREATE_LOG_TRIES == 0) {
      LOG.error(logMessage, t);

      VcsLogManager manager = getLogManager();
      if (manager != null && manager.isLogVisible()) {
        String balloonMessage = VcsLogBundle.message("vcs.log.recreated.due.to.corruption",
                                                     VcsLogUtil.getVcsDisplayName(myProject, manager),
                                                     myRecreatedLogCount,
                                                     LOG_CACHE,
                                                     ApplicationNamesInfo.getInstance().getFullProductName());
        VcsBalloonProblemNotifier.showOverChangesView(myProject, balloonMessage, MessageType.ERROR);
      }
    }
    else {
      LOG.debug(logMessage, t);
    }

    disposeLog(true);
  }

  @NotNull
  CompletableFuture<Boolean> createLogInBackground(boolean forceInit) {
    return CompletableFuture.supplyAsync(() -> createLog(forceInit), myExecutor).thenApply(Objects::nonNull);
  }

  @Nullable
  @RequiresBackgroundThread
  private VcsLogManager createLog(boolean forceInit) {
    if (myDisposeStarted.get()) return null;
    Map<VirtualFile, VcsLogProvider> logProviders = getLogProviders(myProject);
    if (!logProviders.isEmpty()) {
      VcsLogManager logManager = myLogManager.getValue(logProviders);
      initialize(logManager, forceInit);
      return logManager;
    }
    return null;
  }

  @RequiresBackgroundThread
  private static void initialize(@NotNull VcsLogManager logManager, boolean force) {
    if (force) {
      logManager.scheduleInitialization();
      return;
    }

    if (PostponableLogRefresher.keepUpToDate()) {
      VcsLogCachesInvalidator invalidator = requireNonNull(CachesInvalidator.EP_NAME.findExtension(VcsLogCachesInvalidator.class));
      if (invalidator.isValid()) {
        HeavyAwareExecutor.executeOutOfHeavyProcessLater(logManager::scheduleInitialization, 5000);
        return;
      }
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      if (logManager.isLogVisible()) {
        logManager.scheduleInitialization();
      }
    }, getModality());
  }

  @NotNull
  public static Map<VirtualFile, VcsLogProvider> getLogProviders(@NotNull Project project) {
    return VcsLogManager.findLogProviders(Arrays.asList(ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()), project);
  }

  public static VcsProjectLog getInstance(@NotNull Project project) {
    return project.getService(VcsProjectLog.class);
  }

  @Override
  public void dispose() {
  }

  private static <T> T invokeAndWait(@NotNull Supplier<T> computable) {
    Ref<T> result = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> result.set(computable.get()), getModality());
    return result.get();
  }

  @NotNull
  private static ModalityState getModality() {
    /*
     Using "any" modality specifically is required in order to be able to wait for log initialization or disposal under modal progress.
     Otherwise, methods such as "VcsProjectLog#runWhenLogIsReady" or "VcsProjectLog.shutDown" won't be able to work
     when "disposeLog" is queued as "invokeAndWait" (used there in order to ensure sequential execution) will hang when modal progress is displayed.
    */
    return ModalityState.any();
  }

  /**
   * Executes the given action if the VcsProjectLog has been initialized. If not, then schedules the log initialization,
   * waits for it in a background task, and executes the action after the log is ready.
   */
  @RequiresEdt
  public static void runWhenLogIsReady(@NotNull Project project, @NotNull Consumer<? super VcsLogManager> action) {
    VcsProjectLog log = getInstance(project);
    VcsLogManager manager = log.getLogManager();
    if (manager != null) {
      action.consume(manager);
    }
    else { // schedule showing the log, wait its initialization, and then open the tab
      Future<Boolean> futureResult = log.createLogInBackground(true);
      new Task.Backgroundable(project, VcsLogBundle.message("vcs.log.creating.process")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            futureResult.get(5, TimeUnit.SECONDS);
          }
          catch (InterruptedException ignored) {
          }
          catch (ExecutionException e) {
            LOG.error(e);
          }
          catch (TimeoutException e) {
            LOG.warn(e);
          }
        }

        @Override
        public void onSuccess() {
          VcsLogManager manager = log.getLogManager();
          if (manager != null) {
            action.consume(manager);
          }
        }
      }.queue();
    }
  }

  static @NotNull Future<Boolean> waitWhenLogIsReady(@NotNull Project project) {
    VcsProjectLog log = getInstance(project);
    VcsLogManager manager = log.getLogManager();
    if (manager != null) {
      return new FutureResult<>(true);
    }
    return log.createLogInBackground(true);
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread
  public static boolean ensureLogCreated(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    try {
      return waitWhenLogIsReady(project).get() == Boolean.TRUE;
    }
    catch (InterruptedException ignored) {
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }

    return false;
  }

  private class LazyVcsLogManager {
    @Nullable private volatile VcsLogManager myValue;

    @NotNull
    @RequiresBackgroundThread
    public VcsLogManager getValue(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
      if (myValue == null) {
        LOG.debug("Creating Vcs Log for " + VcsLogUtil.getProvidersMapText(logProviders));
        VcsLogManager value = new VcsLogManager(myProject, myUiProperties, logProviders, false,
                                                VcsProjectLog.this::recreateOnError);
        myValue = value;
        ApplicationManager.getApplication().invokeAndWait(() -> {
          myProject.getMessageBus().syncPublisher(VCS_PROJECT_LOG_CHANGED).logCreated(value);
        }, getModality());
      }
      return requireNonNull(myValue);
    }

    @Nullable
    @RequiresEdt
    public VcsLogManager dropValue() {
      LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
      if (myValue != null) {
        VcsLogManager oldValue = myValue;
        myValue = null;

        LOG.debug("Disposing Vcs Log for " + VcsLogUtil.getProvidersMapText(requireNonNull(oldValue).getDataManager().getLogProviders()));
        myProject.getMessageBus().syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed(oldValue);

        return oldValue;
      }
      return null;
    }

    @Nullable
    public VcsLogManager getCached() {
      return myValue;
    }
  }

  static final class InitLogStartupActivity implements StartupActivity, DumbAware {
    InitLogStartupActivity() {
      Application app = ApplicationManager.getApplication();
      if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
        throw ExtensionNotApplicableException.create();
      }
    }

    @Override
    public void runActivity(@NotNull Project project) {
      VcsProjectLog projectLog = getInstance(project);
      projectLog.subscribeToMappingsAndPluginsChanges();
      projectLog.createLogInBackground(false);
    }
  }

  private class MyDynamicPluginUnloader implements DynamicPluginListener {
    private final Set<PluginId> affectedPlugins = new HashSet<>();

    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
      if (hasLogExtensions(pluginDescriptor)) {
        disposeLog(true);
      }
    }

    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
      if (hasLogExtensions(pluginDescriptor)) {
        affectedPlugins.add(pluginDescriptor.getPluginId());
        LOG.debug("Disposing Vcs Log before unloading " + pluginDescriptor.getPluginId());
        disposeLog(false);
      }
    }

    @Override
    public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
      if (affectedPlugins.remove(pluginDescriptor.getPluginId())) {
        LOG.debug("Recreating Vcs Log after unloading " + pluginDescriptor.getPluginId());
        // createLog calls between beforePluginUnload and pluginUnloaded are technically not prohibited
        // so just in case, recreating log here
        disposeLog(true);
      }
    }

    private boolean hasLogExtensions(@NotNull IdeaPluginDescriptor descriptor) {
      for (VcsLogProvider logProvider : LOG_PROVIDER_EP.getExtensions(myProject)) {
        if (logProvider.getClass().getClassLoader() == descriptor.getPluginClassLoader()) return true;
      }
      for (CustomVcsLogUiFactoryProvider factory : LOG_CUSTOM_UI_FACTORY_PROVIDER_EP.getExtensions(myProject)) {
        if (factory.getClass().getClassLoader() == descriptor.getPluginClassLoader()) return true;
      }
      return false;
    }
  }

  public interface ProjectLogListener {
    @RequiresEdt
    void logCreated(@NotNull VcsLogManager manager);

    @RequiresEdt
    void logDisposed(@NotNull VcsLogManager manager);
  }
}
