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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.ContentsUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
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
      public void logCreated(@NotNull VcsLogManager logManager) {
        addLogUi(logManager);
      }

      @Override
      public void logDisposed(@NotNull VcsLogManager logManager) {
        dispose(logManager);
      }
    });

    VcsLogManager manager = myProjectLog.getLogManager();
    if (manager != null) {
      addLogUi(manager);
    }
  }

  @CalledInAwt
  private void addLogUi(@NotNull VcsLogManager logManager) {
    if (myProjectLog.getMainLogUi() == null) {
      VcsLogUiImpl ui = logManager.createLogUi(VcsLogTabsProperties.MAIN_LOG_ID, TAB_NAME);
      myProjectLog.setMainUi(ui);
      myContainer.add(new VcsLogPanel(logManager, ui), BorderLayout.CENTER);
    }
  }

  private void dispose(@Nullable VcsLogManager logManager) {
    myContainer.removeAll();
    VcsLogUiImpl ui = myProjectLog.getMainLogUi();
    if (ui != null) Disposer.dispose(ui);
    if (logManager != null) closeLogTabs(logManager);
  }

  @Override
  public JComponent initContent() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> myProjectLog.createLog());
    return myContainer;
  }

  @Override
  public void disposeContent() {
    dispose(myProjectLog.getLogManager());
  }

  public static <U extends AbstractVcsLogUi> boolean findAndSelectContent(@NotNull Project project,
                                                                          @NotNull Class<U> clazz,
                                                                          @NotNull Condition<U> condition) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);

    ContentManager manager = toolWindow.getContentManager();
    JComponent component = ContentUtilEx.findContentComponent(manager, c -> {
      if (c instanceof VcsLogPanel) {
        AbstractVcsLogUi ui = ((VcsLogPanel)c).getUi();
        //noinspection unchecked
        return clazz.isInstance(ui) && condition.value((U)ui);
      }
      return false;
    });
    if (component == null) return false;
    //noinspection unchecked

    if (!toolWindow.isVisible()) toolWindow.activate(null);
    return ContentUtilEx.selectContent(manager, component, true);
  }

  public static void openAnotherLogTab(@NotNull VcsLogManager logManager, @NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    String name = generateShortName(toolWindow);
    openLogTab(project, logManager, TAB_NAME, name, logManager.getMainLogUiFactory(name));
  }

  public static <U extends AbstractVcsLogUi> void openLogTab(@NotNull Project project, @NotNull VcsLogManager logManager,
                                                             @NotNull String tabGroupName, @NotNull String shortName,
                                                             @NotNull VcsLogManager.VcsLogUiFactory<U> factory) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);

    String name = ContentUtilEx.getFullName(tabGroupName, shortName);

    U logUi = logManager.createLogUi(name, factory);

    ContentUtilEx
      .addTabbedContent(toolWindow.getContentManager(), new VcsLogPanel(logManager, logUi), tabGroupName, shortName, true, logUi);
    toolWindow.activate(null);

    logManager.scheduleInitialization();
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

  private void closeLogTabs(@NotNull VcsLogManager logManager) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS);

    if (toolWindow != null) {
      for (String tabName : logManager.getTabNames()) {
        if (!TAB_NAME.equals(tabName)) { // main tab is closed by the ChangesViewContentManager
          Content content = toolWindow.getContentManager().findContent(tabName);
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
