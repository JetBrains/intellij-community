// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.ui.WinFocusStealer;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This class mutes focus stealing prevention mechanism on Windows, while at least one debug session is active, to make 'Focus application
 * on breakpoint' setting work as expected.
 */
public class DebuggerFocusManager implements XDebuggerManagerListener, RegistryValueListener, ProjectManagerListener {
  private static final Logger LOG = Logger.getInstance(DebuggerFocusManager.class);
  private static final Key<List<XDebugProcess>> ACTIVE_EXECUTIONS = Key.create("DebuggerFocusManager.active.executions");
  private final RegistryValue mySetting = Registry.get("debugger.mayBringFrameToFrontOnBreakpoint");
  private int mySessionCount;
  private boolean myFocusStealingEnabled;

  private DebuggerFocusManager() {
    mySetting.addListener(this, ApplicationManager.getApplication());
  }

    @Override
  public void processStarted(@NotNull XDebugProcess debugProcess) {
    Project project = debugProcess.getSession().getProject();
    List<XDebugProcess> executions = project.getUserData(ACTIVE_EXECUTIONS);
    if (executions == null) {
      project.putUserData(ACTIVE_EXECUTIONS, executions = new ArrayList<>());
    }
    executions.add(debugProcess);
    update(1);
  }

  @Override
  public void processStopped(@NotNull XDebugProcess debugProcess) {
    Project project = debugProcess.getSession().getProject();
    List<XDebugProcess> executions = Objects.requireNonNull(project.getUserData(ACTIVE_EXECUTIONS));
    if (executions.remove(debugProcess)) {
      update(-1);
    }
    else {
      LOG.error("Unexpected event for " + debugProcess);
    }
  }

  @Override
  public void afterValueChanged(@NotNull RegistryValue value) {
    update(0);
  }

  @Override
  public void projectOpened(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(XDebuggerManager.TOPIC, this);
  }

  @Override
  public void projectClosed(@NotNull Project project) {
    // verify that everything is closed
    List<XDebugProcess> executions = project.getUserData(ACTIVE_EXECUTIONS);
    if (executions != null) {
      LOG.assertTrue(executions.isEmpty());
      project.putUserData(ACTIVE_EXECUTIONS, null);
    }
  }

  private synchronized void update(int sessionCountDelta) {
    mySessionCount += sessionCountDelta;
    boolean shouldBeEnabled = mySessionCount > 0 && mySetting.asBoolean();
    if (shouldBeEnabled != myFocusStealingEnabled) {
      WinFocusStealer.setFocusStealingEnabled(myFocusStealingEnabled = shouldBeEnabled);
    }
  }
}
