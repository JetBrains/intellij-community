// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @see XDebuggerManagerListener
 */
@ApiStatus.NonExtendable
public abstract class XDebuggerManager {

  @Topic.ProjectLevel
  public static final Topic<XDebuggerManagerListener> TOPIC =
    new Topic<>("XDebuggerManager events", XDebuggerManagerListener.class);

  public static XDebuggerManager getInstance(@NotNull Project project) {
    return project.getService(XDebuggerManager.class);
  }

  public abstract @NotNull XBreakpointManager getBreakpointManager();


  public abstract XDebugSession @NotNull [] getDebugSessions();

  public abstract @Nullable XDebugSession getDebugSession(@NotNull ExecutionConsole executionConsole);

  public abstract @NotNull <T extends XDebugProcess> List<? extends T> getDebugProcesses(Class<T> processClass);

  public abstract @Nullable XDebugSession getCurrentSession();

  /**
   * Start a new debugging session. Use this method only if debugging is started by using standard 'Debug' action i.e. this methods is called
   * from {@link com.intellij.execution.runners.ProgramRunner#execute(ExecutionEnvironment)} method. Otherwise, use {@link #startSessionAndShowTab} method
   */
  public abstract @NotNull XDebugSession startSession(@NotNull ExecutionEnvironment environment, @NotNull XDebugProcessStarter processStarter) throws ExecutionException;

  /**
   * Start a new debugging session and open 'Debug' tool window
   * @param sessionName title of 'Debug' tool window
   */
  public abstract @NotNull XDebugSession startSessionAndShowTab(@NotNull @Nls String sessionName,
                                                       @Nullable RunContentDescriptor contentToReuse,
                                                       @NotNull XDebugProcessStarter starter) throws ExecutionException;

  /**
   * Start a new debugging session and open 'Debug' tool window
   * @param sessionName title of 'Debug' tool window
   */
  public abstract @NotNull XDebugSession startSessionAndShowTab(@NotNull @Nls String sessionName,
                                                       @NotNull XDebugProcessStarter starter,
                                                       @NotNull ExecutionEnvironment environment) throws ExecutionException;

  /**
   * Start a new debugging session and open 'Debug' tool window
   * @param sessionName title of 'Debug' tool window
   * @param showToolWindowOnSuspendOnly if {@code true} 'Debug' tool window won't be shown until debug process is suspended on a breakpoint
   */
  public abstract @NotNull XDebugSession startSessionAndShowTab(@NotNull @Nls String sessionName, @Nullable RunContentDescriptor contentToReuse,
                                                       boolean showToolWindowOnSuspendOnly,
                                                       @NotNull XDebugProcessStarter starter) throws ExecutionException;

  /**
   * Start a new debugging session and open 'Debug' tool window
   * @param sessionName title of 'Debug' tool window
   * @param icon icon of 'Debug' tool window
   * @param showToolWindowOnSuspendOnly if {@code true} 'Debug' tool window won't be shown until debug process is suspended on a breakpoint
   */
  public abstract @NotNull XDebugSession startSessionAndShowTab(@NotNull @Nls String sessionName, @Nullable Icon icon,
                                                       @Nullable RunContentDescriptor contentToReuse, boolean showToolWindowOnSuspendOnly,
                                                       @NotNull XDebugProcessStarter starter) throws ExecutionException;
}
