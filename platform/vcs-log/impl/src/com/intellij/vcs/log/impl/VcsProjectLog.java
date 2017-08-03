/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.vcs.log.util.PersistentUtil.LOG_CACHE;

public class VcsProjectLog implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsProjectLog.class);
  public static final Topic<ProjectLogListener> VCS_PROJECT_LOG_CHANGED =
    Topic.create("Project Vcs Log Created or Disposed", ProjectLogListener.class);
  private static final int RECREATE_LOG_TRIES = 5;
  @NotNull private final Project myProject;
  @NotNull private final MessageBus myMessageBus;
  @NotNull private final VcsLogTabsProperties myUiProperties;

  @NotNull
  private final LazyVcsLogManager myLogManager = new LazyVcsLogManager();
  private int myRecreatedLogCount = 0;

  public VcsProjectLog(@NotNull Project project, @NotNull VcsLogTabsProperties uiProperties) {
    myProject = project;
    myMessageBus = project.getMessageBus();
    myUiProperties = uiProperties;
  }

  @Nullable
  public VcsLogData getDataManager() {
    VcsLogManager cached = myLogManager.getCached();
    if (cached == null) return null;
    return cached.getDataManager();
  }

  @NotNull
  private Collection<VcsRoot> getVcsRoots() {
    return Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots());
  }

  @CalledInAny
  void setMainUi(@NotNull VcsLogUiImpl ui) {
    myLogManager.setLogUi(ui);
  }

  /**
   * The instance of the {@link VcsLogUiImpl} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getMainLogUi() {
    return myLogManager.getLogUi();
  }

  @Nullable
  public VcsLogManager getLogManager() {
    return myLogManager.getCached();
  }

  @CalledInAny
  private void recreateLog() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      disposeLog();
      if (hasDvcsRoots()) {
        createLog();
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

    recreateLog();
  }

  @CalledInBackground
  private void disposeLog() {
    myLogManager.drop();
  }

  @CalledInBackground
  public void createLog() {
    VcsLogManager logManager = myLogManager.getValue();

    ApplicationManager.getApplication().invokeLater(() -> {
      if (logManager.isLogVisible()) {
        logManager.scheduleInitialization();
      }
      else if (PostponableLogRefresher.keepUpToDate()) {
        VcsLogCachesInvalidator invalidator = CachesInvalidator.EP_NAME.findExtension(VcsLogCachesInvalidator.class);
        if (invalidator.isValid()) {
          HeavyAwareExecutor.executeOutOfHeavyProcessLater(logManager::scheduleInitialization, 5000);
        }
      }
    });
  }

  private boolean hasDvcsRoots() {
    return !VcsLogManager.findLogProviders(getVcsRoots(), myProject).isEmpty();
  }

  public static VcsProjectLog getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsProjectLog.class);
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().executeOnPooledThread(this::disposeLog);
  }

  private class LazyVcsLogManager {
    @Nullable private VcsLogManager myValue;
    @Nullable private VcsLogUiImpl myUi;

    @NotNull
    @CalledInBackground
    public synchronized VcsLogManager getValue() {
      if (myValue == null) {
        VcsLogManager value = compute();
        myValue = value;
        ApplicationManager.getApplication().invokeLater(() -> myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logCreated(value));
      }
      return myValue;
    }

    @NotNull
    @CalledInBackground
    protected synchronized VcsLogManager compute() {
      return new VcsLogManager(myProject, myUiProperties, getVcsRoots(), false, VcsProjectLog.this::recreateOnError);
    }

    @CalledInBackground
    public synchronized void drop() {
      myUi = null;
      if (myValue != null) {
        myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed(myValue);
        Disposer.dispose(myValue);
      }
      myValue = null;
    }

    @Nullable
    public synchronized VcsLogManager getCached() {
      return myValue;
    }

    public synchronized void setLogUi(@NotNull VcsLogUiImpl ui) {
      myUi = ui;
    }

    @Nullable
    @CalledInAny
    public synchronized VcsLogUiImpl getLogUi() {
      return myUi;
    }
  }

  public static class InitLogStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) return;

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        VcsProjectLog projectLog = getInstance(project);

        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, projectLog::recreateLog);
        if (projectLog.hasDvcsRoots()) {
          projectLog.createLog();
        }
      });
    }
  }

  public interface ProjectLogListener {
    @CalledInAwt
    void logCreated(@NotNull VcsLogManager manager);

    @CalledInAwt
    void logDisposed(@NotNull VcsLogManager manager);
  }
}
