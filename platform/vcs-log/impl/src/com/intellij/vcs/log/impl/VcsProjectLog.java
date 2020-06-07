// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static com.intellij.vcs.log.VcsLogProvider.LOG_PROVIDER_EP;
import static com.intellij.vcs.log.impl.CustomVcsLogUiFactoryProvider.LOG_CUSTOM_UI_FACTORY_PROVIDER_EP;
import static com.intellij.vcs.log.util.PersistentUtil.LOG_CACHE;

public class VcsProjectLog implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsProjectLog.class);
  public static final Topic<ProjectLogListener> VCS_PROJECT_LOG_CHANGED =
    Topic.create("Project Vcs Log Created or Disposed", ProjectLogListener.class);
  private static final int RECREATE_LOG_TRIES = 5;
  @NotNull private final Project myProject;
  @NotNull private final MessageBus myMessageBus;
  @NotNull private final VcsLogTabsProperties myUiProperties;
  @NotNull private final VcsLogTabsManager myTabsManager;

  @NotNull private final LazyVcsLogManager myLogManager = new LazyVcsLogManager();
  @NotNull private final Disposable myListenersDisposable = Disposer.newDisposable();
  @NotNull private final ExecutorService myExecutor;
  private volatile boolean myDisposeStarted = false;
  private int myRecreatedLogCount = 0;

  public VcsProjectLog(@NotNull Project project) {
    myProject = project;
    myMessageBus = myProject.getMessageBus();

    VcsLogProjectTabsProperties uiProperties = ServiceManager.getService(myProject, VcsLogProjectTabsProperties.class);
    myUiProperties = uiProperties;
    myTabsManager = new VcsLogTabsManager(project, myMessageBus, uiProperties, this);

    myExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Vcs Log Initialization/Dispose", 1);
    myMessageBus.connect(myListenersDisposable).subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        if (myProject != project) return;

        myDisposeStarted = true;
        Disposer.dispose(myListenersDisposable);
        disposeLog(false);
        myExecutor.shutdown();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          try {
            myExecutor.awaitTermination(5, TimeUnit.SECONDS);
          }
          catch (InterruptedException ignored) {
          }
        }, VcsLogBundle.message("vcs.log.closing.process"), false, project);
      }
    });
  }

  private void subscribeToMappingsAndPluginsChanges() {
    MessageBusConnection connection = myMessageBus.connect(myListenersDisposable);
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

  @CalledInAwt
  @Nullable
  public MainVcsLogUi openLogTab(@Nullable VcsLogFilterCollection filters) {
    return openLogTab(filters, VcsLogManager.LogWindowKind.TOOL_WINDOW);
  }

  @CalledInAwt
  @Nullable
  public MainVcsLogUi openLogTab(@Nullable VcsLogFilterCollection filters,
                                 @NotNull VcsLogManager.LogWindowKind kind) {
    VcsLogManager logManager = getLogManager();
    if (logManager == null) return null;
    return myTabsManager.openAnotherLogTab(logManager, filters, kind);
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

  @CalledInAwt
  private void recreateOnError(@NotNull Throwable t) {
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
  Future<VcsLogManager> createLogInBackground(boolean forceInit) {
    return myExecutor.submit(() -> createLog(forceInit));
  }

  @Nullable
  @CalledInBackground
  private VcsLogManager createLog(boolean forceInit) {
    if (myDisposeStarted) return null;
    Map<VirtualFile, VcsLogProvider> logProviders = getLogProviders(myProject);
    if (!logProviders.isEmpty()) {
      VcsLogManager logManager = myLogManager.getValue(logProviders);
      initialize(logManager, forceInit);
      return logManager;
    }
    return null;
  }

  @CalledInBackground
  private static void initialize(@NotNull VcsLogManager logManager, boolean force) {
    if (force) {
      logManager.scheduleInitialization();
      return;
    }

    if (PostponableLogRefresher.keepUpToDate()) {
      VcsLogCachesInvalidator invalidator = CachesInvalidator.EP_NAME.findExtension(VcsLogCachesInvalidator.class);
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
  static Map<VirtualFile, VcsLogProvider> getLogProviders(@NotNull Project project) {
    return VcsLogManager.findLogProviders(Arrays.asList(ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()), project);
  }

  public static VcsProjectLog getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsProjectLog.class);
  }

  @Override
  public void dispose() {
  }

  private static <T> T invokeAndWait(@NotNull Computable<T> computable) {
    Ref<T> result = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> result.set(computable.compute()), getModality());
    return result.get();
  }

  @NotNull
  private static ModalityState getModality() {
    return ModalityState.any();
  }

  /**
   * Executes the given action if the VcsProjectLog has been initialized. If not, then schedules the log initialization,
   * waits for it in a background task, and executes the action after the log is ready.
   */
  @CalledInAwt
  public static void runWhenLogIsReady(@NotNull Project project, @NotNull BiConsumer<? super VcsProjectLog, ? super VcsLogManager> action) {
    VcsProjectLog log = getInstance(project);
    VcsLogManager manager = log.getLogManager();
    if (manager != null) {
      action.accept(log, manager);
    }
    else { // schedule showing the log, wait its initialization, and then open the tab
      Future<VcsLogManager> futureLogManager = log.createLogInBackground(true);
      new Task.Backgroundable(project, VcsLogBundle.message("vcs.log.creating.process")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            futureLogManager.get(5, TimeUnit.SECONDS);
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
            action.accept(log, manager);
          }
        }
      }.queue();
    }
  }

  @CalledInBackground
  @Nullable
  public static VcsLogManager getOrCreateLog(@NotNull Project project) {
    VcsProjectLog log = getInstance(project);
    VcsLogManager manager = log.getLogManager();
    if (manager == null) {
      try {
        manager = log.createLogInBackground(true).get();
      }
      catch (InterruptedException ignored) {
      }
      catch (ExecutionException e) {
        LOG.error(e);
      }
    }
    return manager;
  }

  private class LazyVcsLogManager {
    @Nullable private volatile VcsLogManager myValue;

    @NotNull
    @CalledInBackground
    public VcsLogManager getValue(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
      if (myValue == null) {
        LOG.debug("Creating Vcs Log for " + VcsLogUtil.getProvidersMapText(logProviders));
        VcsLogManager value = new VcsLogManager(myProject, myUiProperties, logProviders, false,
                                                VcsProjectLog.this::recreateOnError);
        myValue = value;
        ApplicationManager.getApplication().invokeAndWait(() -> {
          myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logCreated(value);
        }, getModality());
      }
      return myValue;
    }

    @Nullable
    @CalledInAwt
    public VcsLogManager dropValue() {
      LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
      if (myValue != null) {
        VcsLogManager oldValue = myValue;

        LOG.debug("Disposing Vcs Log for " + VcsLogUtil.getProvidersMapText(oldValue.getDataManager().getLogProviders()));
        myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed(oldValue);
        myValue = null;

        return oldValue;
      }
      return null;
    }

    @Nullable
    public VcsLogManager getCached() {
      return myValue;
    }
  }

  static final class InitLogStartupActivity implements StartupActivity {
    public InitLogStartupActivity() {
      Application app = ApplicationManager.getApplication();
      if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
        throw ExtensionNotApplicableException.INSTANCE;
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
    @CalledInAwt
    void logCreated(@NotNull VcsLogManager manager);

    @CalledInAwt
    void logDisposed(@NotNull VcsLogManager manager);
  }
}
