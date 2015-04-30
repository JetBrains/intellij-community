/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.xdebugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public abstract class XDebuggerManager {
  public static final Topic<XDebuggerManagerListener> TOPIC =
    new Topic<XDebuggerManagerListener>("XDebuggerManager events", XDebuggerManagerListener.class);

  public static XDebuggerManager getInstance(@NotNull Project project) {
    return project.getComponent(XDebuggerManager.class);
  }

  @NotNull
  public abstract XBreakpointManager getBreakpointManager();


  @NotNull
  public abstract XDebugSession[] getDebugSessions();

  @Nullable
  public abstract XDebugSession getDebugSession(@NotNull ExecutionConsole executionConsole);

  @NotNull
  public abstract <T extends XDebugProcess> List<? extends T> getDebugProcesses(Class<T> processClass);

  @Nullable
  public abstract XDebugSession getCurrentSession();

  /**
   * Start a new debugging session. Use this method only if debugging is started by using standard 'Debug' action i.e. this methods is called
   * from {@link com.intellij.execution.runners.ProgramRunner#execute} method. Otherwise use {@link #startSessionAndShowTab} method
   */
  @NotNull
  public abstract XDebugSession startSession(@NotNull ExecutionEnvironment environment, @NotNull XDebugProcessStarter processStarter) throws ExecutionException;

  /**
   * Start a new debugging session and open 'Debug' tool window
   * @param sessionName title of 'Debug' tool window
   */
  @NotNull
  public abstract XDebugSession startSessionAndShowTab(@NotNull String sessionName,
                                                       @Nullable RunContentDescriptor contentToReuse,
                                                       @NotNull XDebugProcessStarter starter) throws ExecutionException;

  /**
   * Start a new debugging session and open 'Debug' tool window
   * @param sessionName title of 'Debug' tool window
   * @param showToolWindowOnSuspendOnly if {@code true} 'Debug' tool window won't be shown until debug process is suspended on a breakpoint
   */
  @NotNull
  public abstract XDebugSession startSessionAndShowTab(@NotNull String sessionName, @Nullable RunContentDescriptor contentToReuse,
                                                       boolean showToolWindowOnSuspendOnly,
                                                       @NotNull XDebugProcessStarter starter) throws ExecutionException;

  /**
   * Start a new debugging session and open 'Debug' tool window
   * @param sessionName title of 'Debug' tool window
   * @param icon icon of 'Debug' tool window
   * @param showToolWindowOnSuspendOnly if {@code true} 'Debug' tool window won't be shown until debug process is suspended on a breakpoint
   */
  @NotNull
  public abstract XDebugSession startSessionAndShowTab(@NotNull String sessionName, @Nullable Icon icon,
                                                       @Nullable RunContentDescriptor contentToReuse, boolean showToolWindowOnSuspendOnly,
                                                       @NotNull XDebugProcessStarter starter) throws ExecutionException;
}
