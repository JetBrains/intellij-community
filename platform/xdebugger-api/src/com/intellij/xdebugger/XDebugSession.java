/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;

/**
 * Instances of this class are created by the debugging subsystem when {@link XDebuggerManager#startSession} or
 * {@link XDebuggerManager#startSessionAndShowTab} method is called. It isn't supposed to be implemented by a plugin.
 * <p/>
 * Instance of this class can be obtained from {@link XDebugProcess#getSession()} method and used to control debugging process
 *
 * @author nik
 */
public interface XDebugSession extends AbstractDebuggerSession {
  DataKey<XDebugSession> DATA_KEY = DataKey.create("XDebugSessionTab.XDebugSession");

  @NotNull
  Project getProject();

  @NotNull
  XDebugProcess getDebugProcess();

  boolean isSuspended();

  @Nullable
  XStackFrame getCurrentStackFrame();

  XSuspendContext getSuspendContext();

  /**
   * Position from the current frame
   * @return
   */
  @Nullable
  XSourcePosition getCurrentPosition();

  /**
   * Position from the top frame
   * @return
   */
  @Nullable
  XSourcePosition getTopFramePosition();

  void stepOver(boolean ignoreBreakpoints);

  void stepInto();

  void stepOut();

  void forceStepInto();

  void runToPosition(@NotNull XSourcePosition position, final boolean ignoreBreakpoints);

  void pause();

  void resume();

  void showExecutionPoint();

  void setCurrentStackFrame(@NotNull XExecutionStack executionStack, @NotNull XStackFrame frame, boolean isTopFrame);

  default void setCurrentStackFrame(@NotNull XExecutionStack executionStack, @NotNull XStackFrame frame) {
    setCurrentStackFrame(executionStack, frame, frame.equals(executionStack.getTopFrame()));
  }

  /**
   * Call this method to setup custom icon and/or error message (it will be shown in tooltip) for breakpoint
   *
   * @param breakpoint   breakpoint
   * @param icon         icon ({@code null} if default icon should be used). You can use icons from {@link com.intellij.icons.AllIcons.Debugger}
   * @param errorMessage an error message if breakpoint isn't successfully registered
   */
  void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint, @Nullable Icon icon, @Nullable String errorMessage);

  /**
   * Call this method when a breakpoint is reached if its condition ({@link XBreakpoint#getCondition()}) evaluates to {@code true}.
   * <p/>
   * <strong>The underlying debugging process should be suspended only if the method returns {@code true}. </strong>
   *
   * @param breakpoint             reached breakpoint
   * @param evaluatedLogExpression value of {@link XBreakpoint#getLogExpression()} evaluated in the current context
   * @param suspendContext         context
   * @return {@code true} if the debug process should be suspended
   */
  boolean breakpointReached(@NotNull XBreakpoint<?> breakpoint,
                            @Nullable String evaluatedLogExpression,
                            @NotNull XSuspendContext suspendContext);

  /**
   * @deprecated use {@link #breakpointReached(XBreakpoint, String, XSuspendContext)} instead
   */
  boolean breakpointReached(@NotNull XBreakpoint<?> breakpoint, @NotNull XSuspendContext suspendContext);

  /**
   * Call this method when position is reached (e.g. after "Run to cursor" or "Step over" command)
   *
   * @param suspendContext context
   */
  void positionReached(@NotNull XSuspendContext suspendContext);

  /**
   * Call this method when session resumed because of some external event, e.g. from the debugger console
   */
  void sessionResumed();

  void stop();

  void setBreakpointMuted(boolean muted);

  boolean areBreakpointsMuted();


  void addSessionListener(@NotNull XDebugSessionListener listener, @NotNull Disposable parentDisposable);

  void addSessionListener(@NotNull XDebugSessionListener listener);

  void removeSessionListener(@NotNull XDebugSessionListener listener);

  void reportError(@NotNull String message);

  void reportMessage(@NotNull String message, @NotNull MessageType type);

  void reportMessage(@NotNull String message, @NotNull MessageType type, @Nullable HyperlinkListener listener);

  @NotNull
  String getSessionName();

  @NotNull
  RunContentDescriptor getRunContentDescriptor();

  @Nullable
  RunProfile getRunProfile();

  void setPauseActionSupported(boolean isSupported);

  void rebuildViews();

  <V extends XSmartStepIntoVariant> void smartStepInto(XSmartStepIntoHandler<V> handler, V variant);

  void updateExecutionPosition();

  void initBreakpoints();

  ConsoleView getConsoleView();

  RunnerLayoutUi getUI();
}
