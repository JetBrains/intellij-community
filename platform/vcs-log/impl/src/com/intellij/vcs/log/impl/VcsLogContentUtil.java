// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.*;
import com.intellij.util.Consumer;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;

/**
 * Utility methods to operate VCS Log tabs as {@link Content}s of the {@link ContentManager} of the VCS toolwindow.
 */
public final class VcsLogContentUtil {
  @Nullable
  public static VcsLogUiEx getLogUi(@NotNull JComponent c) {
    return ContainerUtil.getFirstItem(getLogUis(c));
  }

  public static List<VcsLogUiEx> getLogUis(@NotNull JComponent c) {
    Set<VcsLogPanel> panels = new HashSet<>();
    collectLogPanelInstances(c, panels);

    return ContainerUtil.map(panels, VcsLogPanel::getUi);
  }

  private static void collectLogPanelInstances(@NotNull JComponent component, @NotNull Set<VcsLogPanel> result) {
    if (component instanceof VcsLogPanel) {
      result.add((VcsLogPanel)component);
      return;
    }
    for (Component childComponent : component.getComponents()) {
      if (childComponent instanceof JComponent) {
        collectLogPanelInstances((JComponent)childComponent, result);
      }
    }
  }

  @Nullable
  public static <U extends VcsLogUiEx> U findAndSelect(@NotNull Project project,
                                                       @NotNull Class<U> clazz,
                                                       @NotNull Condition<? super U> condition) {
    return find(project, clazz, true, condition);
  }

  @Nullable
  public static <U extends VcsLogUiEx> U find(@NotNull Project project,
                                              @NotNull Class<U> clazz, boolean select,
                                              @NotNull Condition<? super U> condition) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    if (toolWindow == null) {
      return null;
    }

    ContentManager manager = toolWindow.getContentManager();
    JComponent component = ContentUtilEx.findContentComponent(manager, c -> {
      VcsLogUiEx ui = getLogUi(c);
      if (ui != null) {
        //noinspection unchecked
        return clazz.isInstance(ui) && condition.value((U)ui);
      }
      return false;
    });
    if (component == null) {
      return null;
    }

    if (select) {
      if (!toolWindow.isVisible()) {
        toolWindow.activate(null);
      }
      if (!ContentUtilEx.selectContent(manager, component, true)) {
        return null;
      }
    }
    //noinspection unchecked
    return (U)getLogUi(component);
  }

  @Nullable
  public static String getId(@NotNull Content content) {
    VcsLogUiEx ui = getLogUi(content.getComponent());
    if (ui == null) return null;
    return ui.getId();
  }

  @NotNull
  public static Set<String> getExistingLogIds(@NotNull Project project) {
    Set<String> existingIds;

    ContentManager contentManager = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID).getContentManager();
    TabbedContent tabbedContent = ContentUtilEx.findTabbedContent(contentManager, VcsLogContentProvider.TAB_NAME);
    if (tabbedContent != null) {
      existingIds = ContainerUtil.map2SetNotNull(tabbedContent.getTabs(), pair -> {
        VcsLogUiEx ui = getLogUi(pair.second);
        if (ui == null) return null;
        return ui.getId();
      });
    }
    else {
      existingIds = ContainerUtil.map2SetNotNull(Arrays.asList(contentManager.getContents()), content -> {
        TabGroupId groupId = content.getUserData(Content.TAB_GROUP_ID_KEY);
        if (groupId == null || !VcsLogContentProvider.TAB_NAME.equals(groupId.getId())) return null;
        return getId(content);
      });
    }

    return existingIds;
  }


  public static <U extends VcsLogUiEx> U openLogTab(@NotNull Project project,
                                                    @NotNull VcsLogManager logManager,
                                                    @NotNull TabGroupId tabGroupId,
                                                    @NotNull Function<U, @NlsContexts.TabTitle String> tabDisplayName,
                                                    @NotNull VcsLogManager.VcsLogUiFactory<U> factory,
                                                    boolean focus) {
    U logUi = logManager.createLogUi(factory, VcsLogManager.LogWindowKind.TOOL_WINDOW);

    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    ContentUtilEx.addTabbedContent(toolWindow.getContentManager(), tabGroupId,
                                   new TabDescriptor(new VcsLogPanel(logManager, logUi), () -> tabDisplayName.apply(logUi), logUi), focus);
    if (focus) {
      toolWindow.activate(null);
    }
    return logUi;
  }

  public static boolean closeLogTab(@NotNull ContentManager manager, @NotNull String tabId) {
    return ContentUtilEx.closeContentTab(manager, c -> {
      VcsLogUiEx ui = getLogUi(c);
      if (ui != null) {
        return ui.getId().equals(tabId);
      }
      return false;
    });
  }

  /**
   * @deprecated replaced by {@link VcsLogContentUtil#runInMainLog(Project, Consumer)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static void openMainLogAndExecute(@NotNull Project project, @NotNull Consumer<? super VcsLogUiImpl> consumer) {
    runInMainLog(project, ui -> consumer.consume((VcsLogUiImpl)ui));
  }

  public static void runInMainLog(@NotNull Project project, @NotNull Consumer<? super MainVcsLogUi> consumer) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    if (window == null || !selectMainLog(window.getContentManager())) {
      showLogIsNotAvailableMessage(project);
      return;
    }

    Runnable runConsumer = () -> Objects.requireNonNull(VcsLogContentProvider.getInstance(project)).executeOnMainUiCreated(consumer);
    if (!window.isVisible()) {
      window.activate(runConsumer);
    }
    else {
      runConsumer.run();
    }
  }

  @RequiresEdt
  public static void showLogIsNotAvailableMessage(@NotNull Project project) {
    VcsBalloonProblemNotifier.showOverChangesView(project, VcsLogBundle.message("vcs.log.is.not.available"), MessageType.WARNING);
  }

  public static boolean selectMainLog(@NotNull ContentManager cm) {
    Content[] contents = cm.getContents();
    for (Content content : contents) {
      // here tab name is used instead of log ui id to select the correct tab
      // it's done this way since main log ui may not be created when this method is called
      if (VcsLogContentProvider.TAB_NAME.equals(content.getTabName())) {
        cm.setSelectedContent(content);
        return true;
      }
    }
    return false;
  }

  public static void updateLogUiName(@NotNull Project project, @NotNull VcsLogUi ui) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
    if (toolWindow == null) return;

    ContentManager manager = toolWindow.getContentManager();
    JComponent component = ContentUtilEx.findContentComponent(manager, c -> ui == getLogUi(c));
    if (component == null) return;
    ContentUtilEx.updateTabbedContentDisplayName(manager, component);
  }

  /**
   * @deprecated use {@link VcsProjectLog#runWhenLogIsReady(Project, Consumer)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @RequiresBackgroundThread
  @Nullable
  public static VcsLogManager getOrCreateLog(@NotNull Project project) {
    VcsProjectLog.ensureLogCreated(project);
    return VcsProjectLog.getInstance(project).getLogManager();
  }
}
