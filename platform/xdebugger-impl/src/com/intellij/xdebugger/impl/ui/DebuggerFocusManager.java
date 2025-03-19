// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.ui.WinFocusStealer;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * This class mutes focus stealing prevention mechanism on Windows, while at least one debug session is active, to make 'Focus application
 * on breakpoint' setting work as expected.
 */
@ApiStatus.Internal
public class DebuggerFocusManager implements XDebuggerManagerListener, RegistryValueListener, ProjectManagerListener {
  private final RegistryValue myFrameToFrontOnBreakpointSetting = Registry.get("debugger.mayBringFrameToFrontOnBreakpoint");
  private final RegistryValue myBringDebuggeeToFrontAfterResumeSetting = Registry.get("debugger.mayBringDebuggeeWindowToFrontAfterResume");
  private boolean myFocusStealingEnabled;

  private DebuggerFocusManager() {
    myFrameToFrontOnBreakpointSetting.addListener(this, ApplicationManager.getApplication());
    myBringDebuggeeToFrontAfterResumeSetting.addListener(this, ApplicationManager.getApplication());
  }

  @Override
  public void processStarted(@NotNull XDebugProcess debugProcess) {
    update(true);
  }

  @Override
  public void processStopped(@NotNull XDebugProcess debugProcess) {
    update(getDebugSessionsCount() > 1); // stopped session is still counted
  }

  @Override
  public void afterValueChanged(@NotNull RegistryValue value) {
    update(getDebugSessionsCount() > 0);
  }

  @Override
  public void projectOpened(@NotNull Project project) {
    project.getMessageBus().connect().subscribe(XDebuggerManager.TOPIC, this);
  }

  @Override
  public void projectClosed(@NotNull Project project) {
    update(getDebugSessionsCount() > 0);
  }

  private static long getDebugSessionsCount() {
    return Arrays.stream(ProjectManager.getInstance().getOpenProjects())
      .filter(p -> !p.isDisposed())
      .mapToInt(p -> XDebuggerManager.getInstance(p).getDebugSessions().length)
      .sum();
  }

  private synchronized void update(boolean enable) {
    boolean shouldBeEnabled = enable && (myFrameToFrontOnBreakpointSetting.asBoolean() ||
                                         myBringDebuggeeToFrontAfterResumeSetting.asBoolean());
    if (shouldBeEnabled != myFocusStealingEnabled) {
      WinFocusStealer.setFocusStealingEnabled(myFocusStealingEnabled = shouldBeEnabled);
    }
  }
}
