// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.ui.WinFocusStealer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This class mutes focus stealing prevention mechanism on Windows, while at least one debug session is active, to make 'Focus application
 * on breakpoint' setting work as expected.
 */
public class DebuggerFocusManager implements ExecutionListener, RegistryValueListener, ProjectManagerListener {
  private static final Logger LOG = Logger.getInstance(DebuggerFocusManager.class);
  private static final Key<List<ExecutionEnvironment>> ACTIVE_EXECUTIONS = Key.create("DebuggerFocusManager.active.executions");
  private final RegistryValue mySetting = Registry.get("debugger.mayBringFrameToFrontOnBreakpoint");
  private int mySessionCount;
  private boolean myFocusStealingEnabled;

  private DebuggerFocusManager() {
    Application application = ApplicationManager.getApplication();
    mySetting.addListener(this, application);
    application.getMessageBus().connect().subscribe(ProjectManager.TOPIC, this);
  }

  @Override
  public void processStartScheduled(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
    if (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) {
      onDebugStarted(env);
    }
  }

  @Override
  public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
    if (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) {
      onDebugFinished(env);
    }
  }

  @Override
  public void processTerminated(@NotNull String executorId,
                                @NotNull ExecutionEnvironment env,
                                @NotNull ProcessHandler handler,
                                int exitCode) {
    if (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) {
      onDebugFinished(env);
    }
  }

  private void onDebugStarted(@NotNull ExecutionEnvironment env) {
    Project project = env.getProject();
    List<ExecutionEnvironment> executions = project.getUserData(ACTIVE_EXECUTIONS);
    if (executions == null) {
      project.putUserData(ACTIVE_EXECUTIONS, executions = new ArrayList<>());
    }
    executions.add(env);
    update(1);
  }

  private void onDebugFinished(@NotNull ExecutionEnvironment env) {
    Project project = env.getProject();
    List<ExecutionEnvironment> executions = Objects.requireNonNull(project.getUserData(ACTIVE_EXECUTIONS));
    if (executions.remove(env)) {
      update(-1);
    }
    else {
      LOG.error("Unexpected event for " + env);
    }
  }

  @Override
  public void afterValueChanged(@NotNull RegistryValue value) {
    update(0);
  }

  @Override
  public void projectClosed(@NotNull Project project) {
    List<ExecutionEnvironment> executions = project.getUserData(ACTIVE_EXECUTIONS);
    if (executions != null) {
      for (ExecutionEnvironment ignored : executions) {
        update(-1);
      }
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
