// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.Executor;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

class CtxDefault {
  private static final Logger LOG = Logger.getInstance(CtxDefault.class);
  private static MessageBusConnection ourConnection = null;

  static void initialize() {
    // 1. load default touchbar actions for all opened projects
    for (Project project : ProjectUtil.getOpenProjects()) {
      registerTouchbarActions(project);
    }

    // 2. listen for projects
    ourConnection = ApplicationManager.getApplication().getMessageBus().connect();
    ourConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        registerTouchbarActions(project);
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        LOG.debug("closed project: %s", project);

        final JFrame frame = WindowManager.getInstance().getFrame(project);
        if (frame == null) {
          // can be when frame is closing (and project is disposing)
          LOG.debug("null frame for project: %s", project);
          return;
        }

        TouchBarsManager.unregister(frame); // remove project-default action group
      }
    });

    // 3. schedule to collect run/debug actions
    fillRunDebugGroup();
  }

  static void disable() {
    if (ourConnection != null)
      ourConnection.disconnect();
    ourConnection = null;
    // NOTE: all registered project actions will 'unregister' in manager.clearAll
    // no necessity to do it here
  }

  private static void registerTouchbarActionsImpl(@NotNull Project project) {
    if (project.isDisposed()) {
      return;
    }

    final JFrame frame = WindowManager.getInstance().getFrame(project);
    if (frame == null) {
      LOG.debug("null frame for project: %s", project);
      return;
    }

    final @Nullable Pair<Map<Long, ActionGroup>, Customizer> defaultGroup = ActionsLoader.getProjectDefaultActionGroup();
    if (defaultGroup == null) {
      LOG.debug("can't load default action group for project: %s", project);
      TouchBarsManager.unregister(frame);
      return;
    }

    LOG.debug("register project-default action group %s | frame %s", project, frame);
    TouchBarsManager.registerAndShow(frame, defaultGroup.first, defaultGroup.second);
  }

  private static void registerTouchbarActions(@NotNull Project project) {
    StartupManager.getInstance(project).runAfterOpened(() -> {
      if (project.isDisposed()) {
        return;
      }

      LOG.debug("register touchbar actions for project %s", project);
      registerTouchbarActionsImpl(project);
    });
  }

  static void reloadAllActions() {
    for (Project project : ProjectUtil.getOpenProjects()) {
      registerTouchbarActionsImpl(project);
    }
  }

  private static final String RUN_DEBUG_GROUP_TOUCHBAR = "RunnerActionsTouchbar";

  private static void fillRunDebugGroup() {
    final ActionManager actionManager = ApplicationManager.getApplication().getServiceIfCreated(ActionManager.class);
    if (actionManager == null) {
      LOG.debug("service ActionManager wasn't cerated, schedule next try after 500ms");
      SimpleTimer.getInstance().setUp(() -> fillRunDebugGroup(), 500);
      return;
    }

    AnAction runButtons = actionManager.getAction(RUN_DEBUG_GROUP_TOUCHBAR);
    if (runButtons == null) {
      LOG.debug("RunnersGroup for touchbar is unregistered");
      return;
    }

    if (!(runButtons instanceof DefaultActionGroup)) {
      LOG.debug("RunnersGroup for touchbar isn't a group");
      return;
    }

    if (((DefaultActionGroup)runButtons).getChildrenCount() > 0) {
      LOG.debug("RunnersGroup for touchbar is already filled, skip fill");
      return;
    }

    DefaultActionGroup group = (DefaultActionGroup)runButtons;
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      if (executor.getId().equals(ToolWindowId.RUN) || executor.getId().equals(ToolWindowId.DEBUG)) {
        group.add(actionManager.getAction(executor.getId()), actionManager);
      }
    }
  }
}
