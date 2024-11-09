// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;

/**
 * Instances of this class are created by the debugging subsystem
 * when the {@link XDebuggerManager#startSession} or {@link XDebuggerManager#startSessionAndShowTab} method is called.
 * It isn't supposed to be implemented by a plugin.
 * <p>
 * An instance of this class can be obtained from the {@link XDebugProcess#getSession()} method
 * and can then be used to control the debugging process.
 */
@ApiStatus.NonExtendable
public interface XDebugSession extends AbstractDebuggerSession {
  DataKey<XDebugSession> DATA_KEY = DataKey.create("XDebugSessionTab.XDebugSession");

  @NotNull
  Project getProject();

  @NotNull
  XDebugProcess getDebugProcess();

  boolean isSuspended();

  @Nullable
  XStackFrame getCurrentStackFrame();

  @Nullable
  XSuspendContext getSuspendContext();

  /**
   * Position of the current frame
   */
  @Nullable
  XSourcePosition getCurrentPosition();

  /**
   * Position of the top frame
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
   * Call this method to set up a custom icon and/or error message (it will be shown in tooltip) for a breakpoint.
   * Usually in your breakpoint handler you need {@link #setBreakpointVerified(XLineBreakpoint)}
   * or {@link #setBreakpointInvalid(XLineBreakpoint, String)} instead.
   *
   * @param icon         icon ({@code null} if default icon should be used). You can use icons from {@link com.intellij.icons.AllIcons.Debugger}
   * @param errorMessage an error message if breakpoint isn't successfully registered
   * @see #setBreakpointVerified(XLineBreakpoint)
   * @see #setBreakpointInvalid(XLineBreakpoint, String)
   */
  void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint, @Nullable Icon icon, @Nullable String errorMessage);

  /**
   * Marks the given breakpoint as verified in the current session.
   */
  void setBreakpointVerified(@NotNull XLineBreakpoint<?> breakpoint);

  /**
   * Marks the given breakpoint as invalid in the current session.
   */
  void setBreakpointInvalid(@NotNull XLineBreakpoint<?> breakpoint, @Nullable String errorMessage);

  /**
   * Call this method when a breakpoint is reached if its condition ({@link XBreakpoint#getConditionExpression()}) evaluates to {@code true}.
   * <p>
   * <strong>The underlying debugging process should be suspended only if the method returns {@code true}.</strong>
   *
   * @param breakpoint             reached breakpoint
   * @param evaluatedLogExpression value of {@link XBreakpoint#getLogExpressionObject()} evaluated in the current context
   * @param suspendContext         context
   * @return {@code true} if the debug process should be suspended
   */
  boolean breakpointReached(@NotNull XBreakpoint<?> breakpoint,
                            @Nullable String evaluatedLogExpression,
                            @NotNull XSuspendContext suspendContext);

  /**
   * Call this method when the position is reached (e.g. after "Run to cursor" or "Step over" command)
   */
  void positionReached(@NotNull XSuspendContext suspendContext);

  /**
   * Call this method when the session was resumed because of some external event, e.g. from the debugger console
   */
  void sessionResumed();

  void stop();

  void setBreakpointMuted(boolean muted);

  boolean areBreakpointsMuted();


  void addSessionListener(@NotNull XDebugSessionListener listener, @NotNull Disposable parentDisposable);

  void addSessionListener(@NotNull XDebugSessionListener listener);

  void removeSessionListener(@NotNull XDebugSessionListener listener);

  void reportError(@NotNull @NlsContexts.NotificationContent String message);

  void reportMessage(@NotNull @NlsContexts.NotificationContent String message, @NotNull MessageType type);

  void reportMessage(@NotNull @NlsContexts.NotificationContent String message, @NotNull MessageType type, @Nullable HyperlinkListener listener);

  @NotNull
  @NlsContexts.TabTitle
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
