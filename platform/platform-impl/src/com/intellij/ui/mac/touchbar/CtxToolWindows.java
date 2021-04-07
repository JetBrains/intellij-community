// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

class CtxToolWindows {
  private static final Logger LOG = Logger.getInstance(CtxToolWindows.class);

  static void initialize() {
    for (Project project : ProjectUtil.getOpenProjects()) {
      subscribeToolWindowTopic(project);
    }

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        subscribeToolWindowTopic(project);
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        forEachToolWindow(project, tw -> {
          if (tw != null)
            TouchBarsManager.unregister(tw.getComponent());
        });
      }
    });
  }

  private static void forEachToolWindow(@NotNull Project project, Consumer<ToolWindow> func) {
    ToolWindowManager twm = ToolWindowManager.getInstance(project);
    if (twm == null) return;
    final String[] ids = twm.getToolWindowIds();
    for (String id: ids) {
      func.accept(twm.getToolWindow(id));
    }
  }

  private static void subscribeToolWindowTopic(@NotNull Project project) {
    if (project.isDisposed()) {
      return;
    }
    LOG.debug("subscribe for ToolWindow topic of project %s", project);
    project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void toolWindowsRegistered(@NotNull List<String> ids, @NotNull ToolWindowManager toolWindowManager) {
        for (String id : ids) {
          final @Nullable Pair<Map<Long, ActionGroup>, Customizer> actions = ActionsLoader.getToolWindowActionGroup(id);
          if (actions == null || actions.first.get(0L) == null) {
            LOG.debug("null action group (or it doesn't contain main-layout) for tool window: %s", id);
            continue;
          }

          ToolWindow toolWindow = toolWindowManager.getToolWindow(id);
          if (toolWindow == null)
            continue;
          TouchBarsManager.register(toolWindow.getComponent(), actions.first, actions.second);
          LOG.debug("register tool-window '%s' for component: %s", id, toolWindow.getComponent());
        }
      }

      @Override
      public void toolWindowUnregistered(@NotNull String id, @NotNull ToolWindow toolWindow) {
        TouchBarsManager.unregister(toolWindow.getComponent());
      }
    });
  }

  static void reloadAllActions() {
    for (Project p : ProjectUtil.getOpenProjects()) {
      if (p.isDisposed()) {
        continue;
      }
      forEachToolWindow(p, tw -> {
        if (tw == null)
          return;

        final @Nullable Pair<Map<Long, ActionGroup>, Customizer> actions = ActionsLoader.getToolWindowActionGroup(tw.getId());
        if (actions == null || actions.first.get(0L) == null) {
          LOG.debug("reloaded null action group (or it doesn't contain main-layout) for tool window: %s", tw.getId());
          return;
        }

        TouchBarsManager.register(tw.getComponent(), actions.first, actions.second);
        LOG.debug("re-register tool-window '%s' for component: %s", tw.getId(), tw.getComponent());
      });
    }
  }
}
