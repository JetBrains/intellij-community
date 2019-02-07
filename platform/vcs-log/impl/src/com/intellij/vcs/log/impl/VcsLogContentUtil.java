// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Set;

/**
 * Utility methods to operate VCS Log tabs as {@link Content}s of the {@link ContentManager} of the VCS toolwindow.
 */
public class VcsLogContentUtil {

  @Nullable
  private static AbstractVcsLogUi getLogUi(@NotNull JComponent c) {
    VcsLogPanel vcsLogPanel = null;
    if (c instanceof VcsLogPanel) {
      vcsLogPanel = (VcsLogPanel)c;
    }
    else if (c instanceof JPanel) {
      vcsLogPanel = ContainerUtil.findInstance(c.getComponents(), VcsLogPanel.class);
    }

    if (vcsLogPanel != null) {
      return vcsLogPanel.getUi();
    }
    return null;
  }

  public static <U extends AbstractVcsLogUi> boolean findAndSelectContent(@NotNull Project project,
                                                                          @NotNull Class<U> clazz,
                                                                          @NotNull Condition<? super U> condition) {
    return findAndSelect(project, clazz, condition) != null;
  }

  @Nullable
  public static <U extends AbstractVcsLogUi> U findAndSelect(@NotNull Project project,
                                                             @NotNull Class<U> clazz,
                                                             @NotNull Condition<? super U> condition) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    if (toolWindow == null) return null;

    ContentManager manager = toolWindow.getContentManager();
    JComponent component = ContentUtilEx.findContentComponent(manager, c -> {
      AbstractVcsLogUi ui = getLogUi(c);
      if (ui != null) {
        //noinspection unchecked
        return clazz.isInstance(ui) && condition.value((U)ui);
      }
      return false;
    });
    if (component == null) return null;

    if (!toolWindow.isVisible()) toolWindow.activate(null);
    if (!ContentUtilEx.selectContent(manager, component, true)) return null;
    //noinspection unchecked
    return (U)getLogUi(component);
  }

  public static boolean selectLogUi(@NotNull Project project, @NotNull VcsLogUi ui) {
    return findAndSelectContent(project, AbstractVcsLogUi.class, u -> u.equals(ui));
  }

  @Nullable
  public static String getId(@NotNull Content content) {
    AbstractVcsLogUi ui = getLogUi(content.getComponent());
    if (ui == null) return null;
    return ui.getId();
  }

  @NotNull
  public static String generateTabId(@NotNull Project project) {
    Set<String> existingIds;

    ContentManager contentManager = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS).getContentManager();
    TabbedContent tabbedContent = ContentUtilEx.findTabbedContent(contentManager, VcsLogContentProvider.TAB_NAME);
    if (tabbedContent != null) {
      existingIds = ContainerUtil.map2SetNotNull(tabbedContent.getTabs(), pair -> {
        AbstractVcsLogUi ui = getLogUi(pair.second);
        if (ui == null) return null;
        return ui.getId();
      });
    }
    else {
      existingIds = ContainerUtil.map2SetNotNull(Arrays.asList(contentManager.getContents()), content -> {
        if (!VcsLogContentProvider.TAB_NAME.equals(content.getUserData(Content.TAB_GROUP_NAME_KEY))) return null;
        return getId(content);
      });
    }

    for (int i = 1; ; i++) {
      String idString = Integer.toString(i);
      if (!existingIds.contains(idString)) {
        return idString;
      }
    }
  }

  public static <U extends AbstractVcsLogUi> U openLogTab(@NotNull Project project, @NotNull VcsLogManager logManager,
                                                          @NotNull String tabGroupName, @NotNull String shortName,
                                                          @NotNull VcsLogManager.VcsLogUiFactory<U> factory, boolean focus) {
    U logUi = logManager.createLogUi(factory, true);

    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    ContentUtilEx.addTabbedContent(toolWindow.getContentManager(),
                                   new VcsLogPanel(logManager, logUi), tabGroupName, shortName, focus, logUi);
    if (focus) toolWindow.activate(null);
    logManager.scheduleInitialization();
    return logUi;
  }

  public static boolean closeLogTab(@NotNull ContentManager manager, @NotNull String tabId) {
    return ContentUtilEx.closeContentTab(manager, c -> {
      AbstractVcsLogUi ui = getLogUi(c);
      if (ui != null) {
        return ui.getId().equals(tabId);
      }
      return false;
    });
  }

  public static void openMainLogAndExecute(@NotNull Project project, @NotNull Consumer<? super VcsLogUiImpl> consumer) {
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

  public static void renameLogUi(@NotNull Project project, @NotNull VcsLogUiImpl ui, @NotNull String newName) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS);
    if (toolWindow == null) return;

    ContentManager manager = toolWindow.getContentManager();
    JComponent component = ContentUtilEx.findContentComponent(manager, c -> ui == getLogUi(c));
    if (component == null) return;
    ContentUtilEx.renameTabbedContent(manager, component, newName);
  }
}
