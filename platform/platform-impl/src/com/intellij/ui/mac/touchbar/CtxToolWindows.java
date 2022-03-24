// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.impl.ProjectUtilCore;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.util.containers.WeakList;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class CtxToolWindows {
  private static final Logger LOG = Logger.getInstance(CtxToolWindows.class);
  private static MessageBusConnection ourConnection = null;
  private static final WeakList<MessageBusConnection> ourProjConnections = new WeakList<>();

  static void initialize() {
    for (Project project : ProjectUtilCore.getOpenProjects()) {
      subscribeToolWindowTopic(project);
    }

    ourConnection = ApplicationManager.getApplication().getMessageBus().connect();
    ourConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
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

  synchronized
  static void disable() {
    if (ourConnection != null)
      ourConnection.disconnect();
    ourConnection = null;

    ourProjConnections.forEach(mbc -> mbc.disconnect());
    ourProjConnections.clear();

    // NOTE: all registered project actions will 'unregister' in manager.clearAll
    // no necessity to do it here
  }

  private static void forEachToolWindow(@NotNull Project project, Consumer<ToolWindow> func) {
    ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    for (ToolWindow window : toolWindowManager.getToolWindows()) {
      func.accept(window);
    }
  }

  private static void subscribeToolWindowTopic(@NotNull Project project) {
    if (project.isDisposed()) {
      return;
    }


    LOG.debug("subscribe for ToolWindow topic of project %s", project);
    MessageBusConnection pbc = project.getMessageBus().connect();
    pbc.subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
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

    ourProjConnections.add(pbc);
  }

  static void reloadAllActions() {
    for (Project p : ProjectUtilCore.getOpenProjects()) {
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
