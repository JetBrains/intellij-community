// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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
  @NotNull private final Disposable myMappingChangesDisposable = Disposer.newDisposable();
  @NotNull private final ExecutorService myExecutor;
  private int myRecreatedLogCount = 0;

  public VcsProjectLog(@NotNull Project project,
                       @NotNull MessageBus messageBus,
                       @NotNull VcsLogProjectTabsProperties uiProperties) {
    myProject = project;
    myMessageBus = messageBus;
    myUiProperties = uiProperties;
    myTabsManager = new VcsLogTabsManager(project, messageBus, uiProperties, this);

    myExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Vcs Log Initialization/Dispose", 1);
    Disposer.register(this, myMappingChangesDisposable);
  }

  private void subscribeToMappingsChanges() {
    myMessageBus.connect(myMappingChangesDisposable).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this::recreateLog);
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

  @CalledInAny
  private void recreateLog() {
    GuiUtils.invokeLaterIfNeeded(() -> disposeLog(() -> {
      if (myProject.isDisposed()) return;
      createLog(false);
    }), ModalityState.any());
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

    recreateLog();
  }

  @NotNull
  @CalledInAwt
  Future<VcsLogManager> createLogInBackground(boolean forceInit) {
    return myExecutor.submit(() -> createLog(forceInit));
  }

  @Nullable
  @CalledInBackground
  private VcsLogManager createLog(boolean forceInit) {
    Map<VirtualFile, VcsLogProvider> logProviders = getLogProviders();
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

  @CalledInAwt
  private void disposeLog(@Nullable Runnable callback) {
    VcsLogManager logManager = myLogManager.dropValue();
    if (logManager != null) {
      logManager.disposeUi();
      myExecutor.submit(() -> {
          Disposer.dispose(logManager);
          if (callback != null) {
            callback.run();
          }
        });
    }

    else if (callback != null) {
      myExecutor.submit(callback);
    }
  }

  @NotNull
  private Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return VcsLogManager.findLogProviders(Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots()), myProject);
  }

  public static VcsProjectLog getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsProjectLog.class);
  }

  public void addProjectLogListener(@NotNull ProjectLogListener listener, @NotNull Disposable disposable) {
    UIUtil.invokeLaterIfNeeded(() -> {
      synchronized (myLogManager) {
        VcsLogManager cached = myLogManager.getCached();
        myMessageBus.connect(disposable).subscribe(VCS_PROJECT_LOG_CHANGED, listener);
        if (cached != null) {
          listener.logCreated(cached);
        }
      }
    });
  }

  @Override
  public void dispose() {
    disposeLog(null);
  }

  private class LazyVcsLogManager {
    @Nullable private VcsLogManager myValue;

    @NotNull
    @CalledInBackground
    public synchronized VcsLogManager getValue(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
      if (myValue == null) {
        LOG.debug("Creating Vcs Log for " + VcsLogUtil.getProvidersMapText(logProviders));
        VcsLogManager value = new VcsLogManager(myProject, myUiProperties, logProviders, false,
                                                VcsProjectLog.this::recreateOnError);
        myValue = value;
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!myProject.isDisposed()) myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logCreated(value);
        }, ModalityState.any());
      }
      return myValue;
    }

    @Nullable
    @CalledInAwt
    public synchronized VcsLogManager dropValue() {
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
    public synchronized VcsLogManager getCached() {
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
