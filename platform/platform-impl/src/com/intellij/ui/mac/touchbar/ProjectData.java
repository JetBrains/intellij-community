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

import java.awt.event.InputEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class ProjectData {
  private static final Logger LOG = Logger.getInstance(ProjectData.class);

  private final @NotNull Project myProject;
  private final Map<BarType, BarContainer> myBars = new HashMap<>();

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

  @Nullable BarContainer get(BarType type) {
    BarContainer result = myBars.get(type);
    if (result == null) {
      result = new BarContainer(type, TouchBar.EMPTY, null);
      _fillBarContainer(result);
      myBars.put(type, result);
    }
    return result;
  }

  private void _fillBarContainer(@NotNull BarContainer container) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final @NotNull BarType type = container.getType();

    final String barId;
    final boolean replaceEsc;
    if (type == BarType.DEFAULT) {
      barId = "Default";
      replaceEsc = false;
    } else if (type == BarType.DEBUGGER) {
      barId = ToolWindowId.DEBUG;
      replaceEsc = true;
    } else {
      LOG.error("can't create touchbar, unknown context: " + type);
      return;
    }

    final ActionGroup mainLayout = TouchBarActionBase.getCustomizedGroup(barId);
    if (mainLayout == null) {
      LOG.error("can't create touchbar because corresponding ActionGroup isn't defined, context: " + barId);
      return;
    }

    final Map<String, ActionGroup> strmod2alt = TouchBarActionBase.getAltLayouts(mainLayout);
    final Map<Long, TouchBar> alts = new HashMap<>();
    if (strmod2alt != null && !strmod2alt.isEmpty()) {
      for (String modId: strmod2alt.keySet()) {
        final long mask = _str2mask(modId);
        if (mask == 0) {
          // System.out.println("ERROR: zero mask for modId="+modId);
          continue;
        }
        alts.put(mask, new TouchBarActionBase(type.name() + "_" + modId, myProject, strmod2alt.get(modId), replaceEsc));
      }
    }

    container.set(new TouchBarActionBase(type.name(), myProject, mainLayout, replaceEsc), alts);
  }

  void releaseAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myBars.forEach((t, bc)->bc.release());
    myBars.clear();
  }

  void reloadAll() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myBars.forEach((t, bc)->{
      bc.release();
      _fillBarContainer(bc);
    });
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
