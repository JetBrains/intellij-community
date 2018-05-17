// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ProjectData {
  private static final Logger LOG = Logger.getInstance(ProjectData.class);

  public static final String DEFAULT = "default";
  public static final String DEBUGGER = ToolWindowId.DEBUG;
  public static final String EDITOR = "editor";

  private final @NotNull Project myProject;
  private final List<BarContainer> myBars = new ArrayList<>();

  private AtomicInteger myActiveDebugSessions = new AtomicInteger(0);

  ProjectData(@NotNull Project project) {
    myProject = project;

    myProject.getMessageBus().connect().subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        // System.out.println("processStarted: " + executorId);
        if (executorId.equals(ToolWindowId.DEBUG))
          myActiveDebugSessions.incrementAndGet();
      }
      @Override
      public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
        // System.out.println("processTerminated: " + executorId);
        if (executorId.equals(ToolWindowId.DEBUG)) {
          final int val = myActiveDebugSessions.decrementAndGet();
          if (val < 0) {
            LOG.error("received 'processTerminated' when no process wasn't started");
            myActiveDebugSessions.incrementAndGet();
          }
        }
      }
    });
  }

  @Nullable BarContainer createBarContainer(@NotNull String type, Component component) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final String barId;
    final String touchBarName;
    final boolean replaceEsc;
    if (type.equals(DEFAULT)) {
      barId = "Default";
      touchBarName = barId;
      replaceEsc = false;
    } else if (type.equals(ToolWindowId.DEBUG)) {
      barId = ToolWindowId.DEBUG;
      touchBarName = ToolWindowId.DEBUG;
      replaceEsc = true;
    } else if (type.equals(EDITOR)) {
      barId = "Default";
      touchBarName = barId;
      replaceEsc = false;
    } else {
      LOG.error("can't create touchbar, unknown context: " + type);
      return null;
    }

    final ActionGroup mainLayout = TouchBarActionBase.getCustomizedGroup(barId);
    if (mainLayout == null) {
      LOG.error("can't create touchbar because corresponding ActionGroup isn't defined, context: " + barId);
      return null;
    }

    final Component targetComponent = type.equals(ToolWindowId.DEBUG) ? null : component; // debugger must use context of focused component (for example, to use selected text in the Editor)
    final MultiBarContainer container = new MultiBarContainer(new TouchBarActionBase(touchBarName, myProject, mainLayout, targetComponent, replaceEsc));
    final Map<String, ActionGroup> alts = type.equals(DEFAULT) ? null : TouchBarActionBase.getAltLayouts(mainLayout);
    if (alts != null && !alts.isEmpty()) {
      for (String modId: alts.keySet()) {
        final long mask = _str2mask(modId);
        if (mask == 0) {
          // System.out.println("ERROR: zero mask for modId="+modId);
          continue;
        }
        container.registerAltByKeyMask(mask, new TouchBarActionBase(touchBarName + "_" + modId, myProject, alts.get(modId), component, replaceEsc));
      }
    }

    myBars.add(container);
    return container;
  }

  void releaseAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myBars.forEach((bc)->bc.release());
    myBars.clear();
  }

  int getDbgSessions() { return myActiveDebugSessions.get(); }

  private static long _str2mask(@NotNull String modifierId) {
    if (!modifierId.contains(".")) {
      if (modifierId.equalsIgnoreCase("alt"))
        return InputEvent.ALT_DOWN_MASK;
      if (modifierId.equalsIgnoreCase("cmd"))
        return InputEvent.META_DOWN_MASK;
      if (modifierId.equalsIgnoreCase("shift"))
        return InputEvent.SHIFT_DOWN_MASK;
      return 0;
    }

    final String[] spl = modifierId.split("\\.");
    if (spl == null)
      return 0;

    long mask = 0;
    for (String sub: spl)
      mask |= _str2mask(sub);
    return mask;
  }
}
