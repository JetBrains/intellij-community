/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.Consumer;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Utility methods to operate VCS Log tabs as {@link Content}s of the {@link ContentManager} of the VCS toolwindow.
 */
public class VcsLogContentUtil {
  private static final Logger LOG = Logger.getInstance(VcsLogContentUtil.class);

  public static <U extends AbstractVcsLogUi> boolean findAndSelectContent(@NotNull Project project,
                                                                          @NotNull Class<U> clazz,
                                                                          @NotNull Condition<U> condition) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);

    ContentManager manager = toolWindow.getContentManager();
    JComponent component = ContentUtilEx.findContentComponent(manager, c -> {
      VcsLogPanel vcsLogPanel = null;
      if (c instanceof VcsLogPanel) {
        vcsLogPanel = (VcsLogPanel)c;
      }
      else if (c instanceof JPanel) {
        vcsLogPanel = ContainerUtil.findInstance(c.getComponents(), VcsLogPanel.class);
      }

      if (vcsLogPanel != null) {
        AbstractVcsLogUi ui = vcsLogPanel.getUi();
        //noinspection unchecked
        return clazz.isInstance(ui) && condition.value((U)ui);
      }
      return false;
    });
    if (component == null) return false;

    if (!toolWindow.isVisible()) toolWindow.activate(null);
    return ContentUtilEx.selectContent(manager, component, true);
  }

  public static boolean selectLogUi(@NotNull Project project, @NotNull VcsLogUi ui) {
    return findAndSelectContent(project, AbstractVcsLogUi.class, u -> u.equals(ui));
  }

  public static void openAnotherLogTab(@NotNull VcsLogManager logManager, @NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    String name = generateShortName(toolWindow);
    openLogTab(project, logManager, VcsLogContentProvider.TAB_NAME, name, logManager.getMainLogUiFactory(name));
  }

  @NotNull
  private static String generateShortName(@NotNull ToolWindow toolWindow) {
    TabbedContent tabbedContent = ContentUtilEx.findTabbedContent(toolWindow.getContentManager(), VcsLogContentProvider.TAB_NAME);
    if (tabbedContent != null) {
      return String.valueOf(tabbedContent.getTabs().size() + 1);
    }
    else {
      List<Content> contents = ContainerUtil.filter(toolWindow.getContentManager().getContents(),
                                                    content -> VcsLogContentProvider.TAB_NAME
                                                      .equals(content.getUserData(Content.TAB_GROUP_NAME_KEY)));
      return String.valueOf(contents.size() + 1);
    }
  }

  public static <U extends AbstractVcsLogUi> void openLogTab(@NotNull Project project, @NotNull VcsLogManager logManager,
                                                             @NotNull String tabGroupName, @NotNull String shortName,
                                                             @NotNull VcsLogManager.VcsLogUiFactory<U> factory) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);

    String name = ContentUtilEx.getFullName(tabGroupName, shortName);

    U logUi = logManager.createLogUi(name, factory);

    ContentUtilEx.addTabbedContent(toolWindow.getContentManager(),
                                   new VcsLogPanel(logManager, logUi), tabGroupName, shortName, true, logUi);
    toolWindow.activate(null);

    logManager.scheduleInitialization();
  }

  public static void openMainLogAndExecute(@NotNull Project project, @NotNull Consumer<VcsLogUiImpl> consumer) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    if (!selectMainLog(window)) {
      VcsBalloonProblemNotifier.showOverChangesView(project, "Vcs Log is not available", MessageType.WARNING);
      return;
    }

    Runnable runConsumer = () -> ObjectUtils.notNull(VcsLogContentProvider.getInstance(project)).executeOnMainUiCreated(consumer);
    if (!window.isVisible()) {
      window.activate(runConsumer);
    }
    else {
      runConsumer.run();
    }
  }

  private static boolean selectMainLog(@NotNull ToolWindow window) {
    ContentManager cm = window.getContentManager();
    Content[] contents = cm.getContents();
    for (Content content : contents) {
      if (VcsLogContentProvider.TAB_NAME.equals(content.getDisplayName())) {
        cm.setSelectedContent(content);
        return true;
      }
    }
    return false;
  }

  public static void closeLogTabs(@NotNull ToolWindow toolWindow, @NotNull Collection<String> tabs) {
    for (String tabName : tabs) {
      boolean closed = ContentUtilEx.closeContentTab(toolWindow.getContentManager(), tabName);
      LOG.assertTrue(closed, "Could not find content component for tab " + tabName + "\nExisting content: " +
                             Arrays.toString(toolWindow.getContentManager().getContents()) + "\nTabs to close: " + tabs);
    }
  }
}
