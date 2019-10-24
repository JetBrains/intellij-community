// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.messages.Topic;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

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
  @NotNull private final Disposable myMessageBusConnections = Disposer.newDisposable();
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
    myMessageBus.connect(myMessageBusConnections).subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        if (myProject != project) return;

        myDisposeStarted = true;
        Disposer.dispose(myMessageBusConnections);
        disposeLog(false);
        myExecutor.shutdown();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          try {
            myExecutor.awaitTermination(5, TimeUnit.SECONDS);
          }
          catch (InterruptedException ignored) {
          }
        }, "Closing Vcs Log", false, project);
      }
    });
  }

  private void subscribeToMappingsChanges() {
    myMessageBus.connect(myMessageBusConnections).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, () -> disposeLog(true));
  }

  @Nullable
  public VcsLogData getDataManager() {
    VcsLogManager cached = myLogManager.getCached();
    if (cached == null) return null;
    return cached.getDataManager();
  }

  /**
   * The instance of the {@link VcsLogUiImpl} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getMainLogUi() {
    VcsLogContentProvider logContentProvider = VcsLogContentProvider.getInstance(myProject);
    if (logContentProvider == null) return null;
    return logContentProvider.getUi();
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
  public VcsLogUiImpl openLogTab(@Nullable VcsLogFilterCollection filters) {
    VcsLogManager logManager = getLogManager();
    if (logManager == null) return null;
    return myTabsManager.openAnotherLogTab(logManager, filters);
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
    if ((++myRecreatedLogCount) % RECREATE_LOG_TRIES == 0) {
      String message = String.format("VCS Log was recreated %d times due to data corruption\n" +
                                     "Delete %s directory and restart %s if this happens often.\n%s",
                                     myRecreatedLogCount, LOG_CACHE, ApplicationNamesInfo.getInstance().getFullProductName(),
                                     t.getMessage());
      LOG.error(message, t);

      VcsLogManager manager = getLogManager();
      if (manager != null && manager.isLogVisible()) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, message, MessageType.ERROR);
      }
    }
    else {
      LOG.debug("Recreating VCS Log after storage corruption", t);
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
    }, ModalityState.any());
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
    ApplicationManager.getApplication().invokeAndWait(() -> result.set(computable.compute()), ModalityState.any());
    return result.get();
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
      new Task.Backgroundable(project, "Loading Commits") {
        @Nullable private VcsLogManager myLogManager;

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            myLogManager = futureLogManager.get(5, TimeUnit.SECONDS);
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
          if (myLogManager != null) {
            action.accept(log, myLogManager);
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
        }, ModalityState.any());
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

  public static class InitLogStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) return;

      VcsProjectLog projectLog = getInstance(project);

      projectLog.subscribeToMappingsChanges();
      projectLog.createLogInBackground(false);
    }
  }

  public interface ProjectLogListener {
    @CalledInAwt
    void logCreated(@NotNull VcsLogManager manager);

    @CalledInAwt
    default void logDisposed(@NotNull VcsLogManager manager) {
    }
  }
}
