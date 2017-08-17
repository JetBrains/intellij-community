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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.NotNullFunction;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 * <p/>
 * Delegates to the VcsLogManager.
 */
public class VcsLogContentProvider implements ChangesViewContentProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogContentProvider.class);
  public static final String TAB_NAME = "Log";

  @NotNull private final Project myProject;
  @NotNull private final VcsProjectLog myProjectLog;
  @NotNull private final JPanel myContainer = new JBPanel(new BorderLayout());

  @Nullable private volatile VcsLogUiImpl myUi;

  public VcsLogContentProvider(@NotNull Project project, @NotNull VcsProjectLog projectLog) {
    myProject = project;
    myProjectLog = projectLog;

    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, new VcsProjectLog.ProjectLogListener() {
      @Override
      public void logCreated(@NotNull VcsLogManager logManager) {
        addLogUi(logManager);
      }

      @Override
      public void logDisposed(@NotNull VcsLogManager logManager) {
        disposeLogUi(logManager);
      }
    });

    VcsLogManager manager = myProjectLog.getLogManager();
    if (manager != null) {
      addLogUi(manager);
    }
  }

  @Nullable
  public VcsLogUiImpl getUi() {
    return myUi;
  }

  @CalledInAwt
  private void addLogUi(@NotNull VcsLogManager logManager) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (myUi == null) {
      myUi = logManager.createLogUi(VcsLogTabsProperties.MAIN_LOG_ID, TAB_NAME);
      myContainer.add(new VcsLogPanel(logManager, myUi), BorderLayout.CENTER);
    }
  }

  @CalledInAwt
  private void disposeLogUi(@Nullable VcsLogManager logManager) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    // main ui
    myContainer.removeAll();
    if (myUi != null) {
      VcsLogUiImpl ui = myUi;
      myUi = null;
      Disposer.dispose(ui);
    }

    // other tabs
    if (logManager != null) {
      VcsLogContentUtil.closeLogTabs(myProject, logManager.getTabNames());
    }
  }

  @Override
  public JComponent initContent() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> myProjectLog.createLog());
    return myContainer;
  }

  @Override
  public void disposeContent() {
    disposeLogUi(myProjectLog.getLogManager());
  }

  @Nullable
  public static VcsLogContentProvider getInstance(@NotNull Project project) {
    ChangesViewContentEP[] extensions = project.getExtensions(ChangesViewContentEP.EP_NAME);
    for (ChangesViewContentEP ep: extensions) {
      if (ep.getClassName().equals(VcsLogContentProvider.class.getName())) {
        return (VcsLogContentProvider)ep.getInstance(project);
      }
    }
    return null;
  }

  public static class VcsLogVisibilityPredicate implements NotNullFunction<Project, Boolean> {
    @NotNull
    @Override
    public Boolean fun(Project project) {
      return !VcsLogManager.findLogProviders(Arrays.asList(ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()), project)
        .isEmpty();
    }
  }
}
