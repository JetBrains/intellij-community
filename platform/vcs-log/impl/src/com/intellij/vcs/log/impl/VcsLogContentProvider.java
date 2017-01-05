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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.ContentsUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 * <p/>
 * Delegates to the VcsLogManager.
 */
public class VcsLogContentProvider implements ChangesViewContentProvider {
  public static final String TAB_NAME = "Log";

  @NotNull private final Project myProject;
  @NotNull private final VcsProjectLog myProjectLog;
  @NotNull private final JPanel myContainer = new JBPanel(new BorderLayout());

  public VcsLogContentProvider(@NotNull Project project, @NotNull VcsProjectLog projectLog) {
    myProject = project;
    myProjectLog = projectLog;

    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, new VcsProjectLog.ProjectLogListener() {
      @Override
      public void logCreated() {
        addLogUi();
      }

      @Override
      public void logDisposed() {
        myContainer.removeAll();
        closeLogTabs();
      }
    });

    if (myProjectLog.getLogManager() != null) {
      addLogUi();
    }
  }

  @CalledInAwt
  private void addLogUi() {
    myContainer.add(myProjectLog.initMainLog(TAB_NAME), BorderLayout.CENTER);
  }

  @Override
  public JComponent initContent() {
    myProjectLog.createLog();
    return myContainer;
  }

  @Override
  public void disposeContent() {
    myContainer.removeAll();
    closeLogTabs();
  }

  public static void openAnotherLogTab(@NotNull VcsLogManager logManager, @NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    openLogTab(logManager, project, generateShortName(toolWindow), null);
  }

  public static VcsLogUiImpl openLogTab(@NotNull VcsLogManager logManager,
                                        @NotNull Project project,
                                        @NotNull String shortName,
                                        @Nullable VcsLogFilter filter) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);

    String name = ContentUtilEx.getFullName(TAB_NAME, shortName);

    VcsLogUiImpl logUi = logManager.createLogUi(name, name, filter);

    ContentUtilEx
      .addTabbedContent(toolWindow.getContentManager(), new VcsLogPanel(logManager, logUi), TAB_NAME, shortName, true, logUi);
    toolWindow.activate(null);

    logManager.scheduleInitialization();
    return logUi;
  }

  @NotNull
  private static String generateShortName(@NotNull ToolWindow toolWindow) {
    TabbedContent tabbedContent = ContentUtilEx.findTabbedContent(toolWindow.getContentManager(), TAB_NAME);
    if (tabbedContent != null) {
      return String.valueOf(tabbedContent.getTabs().size() + 1);
    }
    else {
      List<Content> contents = ContainerUtil.filter(toolWindow.getContentManager().getContents(),
                                                    content -> TAB_NAME.equals(content.getUserData(Content.TAB_GROUP_NAME_KEY)));
      return String.valueOf(contents.size() + 1);
    }
  }

  private void closeLogTabs() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS);

    if (toolWindow != null) {
      for (Content content : toolWindow.getContentManager().getContents()) {
        if (ContentUtilEx.isContentTab(content, TAB_NAME)) {
          ContentsUtil.closeContentTab(toolWindow.getContentManager(), content);
        }
      }
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
