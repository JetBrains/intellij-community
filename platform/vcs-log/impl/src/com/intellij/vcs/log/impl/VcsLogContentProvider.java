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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.NotNullFunction;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 * <p/>
 * Delegates to the VcsLogManager.
 */
public class VcsLogContentProvider implements ChangesViewContentProvider {

  public static final String TAB_NAME = "Log";

  @NotNull private final Project myProject;
  @NotNull private final VcsLogManager myLogManager;
  @NotNull private final JPanel myContainer = new JBPanel(new BorderLayout());
  private MessageBusConnection myConnection;

  public VcsLogContentProvider(@NotNull Project project, @NotNull VcsLogManager logManager) {
    myProject = project;
    myLogManager = logManager;
  }

  @Override
  public JComponent initContent() {
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, new MyVcsListener());
    initContentInternal();
    return myContainer;
  }

  private void initContentInternal() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myContainer.add(myLogManager.initMainLog(TAB_NAME), BorderLayout.CENTER);
  }

  @Override
  public void disposeContent() {
    myConnection.disconnect();
    myContainer.removeAll();
    myLogManager.disposeLog();
  }

  private class MyVcsListener implements VcsListener {
    @Override
    public void directoryMappingChanged() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myContainer.removeAll();
          myLogManager.disposeLog();

          initContentInternal();
        }
      });
    }
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
