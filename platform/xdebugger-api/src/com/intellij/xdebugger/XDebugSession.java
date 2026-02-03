// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.AsyncProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
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
 * when the {@link XDebugSessionBuilder#startSession()} method is called.
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
   * Call this method when the position is reached (e.g. after "Run to cursor" or "Step over" command)
   */
  default void positionReached(@NotNull XSuspendContext suspendContext, boolean attract) {
    positionReached(suspendContext);
  }

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

  default void reportError(@NotNull @NlsContexts.NotificationContent String message) {
    reportMessage(message, MessageType.ERROR);
  }

  default void reportMessage(@NotNull @NlsContexts.NotificationContent String message, @NotNull MessageType type) {
    reportMessage(message, type, null);
  }

  void reportMessage(@NotNull @NlsContexts.NotificationContent String message, @NotNull MessageType type, @Nullable HyperlinkListener listener);

  @NotNull
  @NlsContexts.TabTitle
  String getSessionName();

  /**
   * @deprecated Do not use.
   * <ul>
   *   <li>Use {@link XSessionStartedResult#getRunContentDescriptor()} to return {@link RunContentDescriptor} instance into {@link AsyncProgramRunner#execute(ExecutionEnvironment, RunProfileState)}.</li>
   *   <li>Use {@link XDebugProcess#getProcessHandler()} to access {@link com.intellij.execution.process.ProcessHandler}.</li>
   *   <li>Use {@link XDebugSession#getExecutionEnvironment()} to access execution ID.</li>
   *   <li>Use {@link XDebugSession#getConsoleView()} as disposable instead.</li>
   *   <li>See {@link XDebugSession#getUI()} to access UI components.</li>
   * </ul>
   */
  @Deprecated
  @NotNull
  RunContentDescriptor getRunContentDescriptor();

  @Nullable
  RunProfile getRunProfile();

  void setPauseActionSupported(boolean isSupported);

  void rebuildViews();

  <V extends XSmartStepIntoVariant> void smartStepInto(XSmartStepIntoHandler<V> handler, V variant);

  // The execution position should be updated by the debugger engine of from the front-end
  @Deprecated
  void updateExecutionPosition();

  void initBreakpoints();

  ConsoleView getConsoleView();

  /**
   * Tab UI should not be configured from a backend session.
   * <p>
   * In monolith, the tab is created asynchronously, so the usages of this method may lead to a race.
   * <p>
   * By using this method in RemDev, the tabs are passed to the frontend as LUXed UI.
   * <p>
   * To migrate, please use one of the following approaches:
   * <ul>
   *   <li>Use {@link XDebugProcess#createTabLayouter()} to create static tabs. Note that this option still uses LUX.</li>
   *   <li>Use {@link com.intellij.xdebugger.impl.XDebugSessionImpl#runWhenUiReady} as a temporary workaround to avoid races.</li>
   *   <li>Use {@link com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy#getSessionTab()} to add a tab on the frontend.</li>
   * </ul>
   */
  @ApiStatus.Obsolete
  @Nullable RunnerLayoutUi getUI();

  @ApiStatus.Internal
  boolean isMixedMode();

  @Nullable ExecutionEnvironment getExecutionEnvironment();
}
