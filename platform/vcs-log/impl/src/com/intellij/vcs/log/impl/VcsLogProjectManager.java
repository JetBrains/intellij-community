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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.vcs.log.data.VcsLogDataManager;
import com.intellij.vcs.log.data.VcsLogTabsProperties;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

public class VcsLogProjectManager {
  public static final Topic<ProjectLogListener> VCS_PROJECT_LOG_CHANGED =
    Topic.create("Project Vcs Log Created or Disposed", ProjectLogListener.class);
  @NotNull private final Project myProject;
  @NotNull private final MessageBus myMessageBus;
  @NotNull private final VcsLogTabsProperties myUiProperties;

  @NotNull
  private final LazyVcsLogManager myLogManager = new LazyVcsLogManager();
  private volatile VcsLogUiImpl myUi;

  public VcsLogProjectManager(@NotNull Project project, @NotNull VcsLogTabsProperties uiProperties) {
    myProject = project;
    myMessageBus = project.getMessageBus();
    myUiProperties = uiProperties;
  }

  @Nullable
  public VcsLogDataManager getDataManager() {
    VcsLogManager cached = myLogManager.getCached();
    if (cached == null) return null;
    return cached.getDataManager();
  }

  @NotNull
  private Collection<VcsRoot> getVcsRoots() {
    return Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots());
  }

  @NotNull
  public JComponent initMainLog(@NotNull String contentTabName) {
    myUi = myLogManager.getValue().createLogUi(VcsLogTabsProperties.MAIN_LOG_ID, contentTabName);
    return new VcsLogPanel(myLogManager.getValue(), myUi);
  }

  /**
   * The instance of the {@link VcsLogUiImpl} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getMainLogUi() {
    return myUi;
  }

  @Nullable
  public VcsLogManager getLogManager() {
    return myLogManager.getCached();
  }

  @CalledInAny
  private void recreateLog() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        disposeLog();

        if (hasDvcsRoots()) {
          createLog();
        }
      }
    });
  }

  @CalledInAwt
  private void disposeLog() {
    if (myLogManager.getCached() != null) myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed();
    myUi = null;
    myLogManager.drop();
  }

  @CalledInAwt
  private void createLog() {
    VcsLogManager logManager = myLogManager.getValue();

    myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logCreated();

    if (PostponableLogRefresher.keepUpToDate()) {
      new HeavyAwareExecutor(myProject).execute(new Runnable() {
        @Override
        public void run() {
          logManager.scheduleInitialization();
        }
      });
    }
  }

  public void scheduleInitialization() {
    VcsLogManager cached = myLogManager.getCached();
    if (cached != null) cached.scheduleInitialization();
  }

  private boolean hasDvcsRoots() {
    return !VcsLogManager.findLogProviders(getVcsRoots(), myProject).isEmpty();
  }

  public static VcsLogProjectManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsLogProjectManager.class);
  }

  @SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
  private class LazyVcsLogManager extends ClearableLazyValue<VcsLogManager> {

    @NotNull
    @CalledInAwt
    @Override
    public synchronized VcsLogManager getValue() {
      return super.getValue();
    }

    @NotNull
    @CalledInAwt
    @Override
    protected synchronized VcsLogManager compute() {
      return new VcsLogManager(myProject, myUiProperties, getVcsRoots(), false, new Runnable() {
        @Override
        public void run() {
          recreateLog();
        }
      });
    }

    @CalledInAwt
    @Override
    public synchronized void drop() {
      if (myValue != null) Disposer.dispose(myValue);
      super.drop();
    }

    @Nullable
    public synchronized VcsLogManager getCached() {
      return myValue;
    }
  }

  public static class InitLogStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      VcsLogProjectManager logProjectManager = getInstance(project);

      MessageBusConnection connection = project.getMessageBus().connect(project);
      connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, new VcsListener() {
        @Override
        public void directoryMappingChanged() {
          logProjectManager.recreateLog();
        }
      });
      if (logProjectManager.hasDvcsRoots()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            logProjectManager.createLog();
          }
        });
      }
    }
  }

  public interface ProjectLogListener {
    @CalledInAwt
    void logCreated();

    @CalledInAwt
    void logDisposed();
  }
}
