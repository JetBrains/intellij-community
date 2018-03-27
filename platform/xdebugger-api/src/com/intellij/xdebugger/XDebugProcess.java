/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.xdebugger;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.event.HyperlinkListener;

/**
 * Extends this class to provide debugging capabilities for custom language/framework.
 *
 * In order to start debugger by 'Debug' action for a specific run configuration implement {@link com.intellij.execution.runners.ProgramRunner}
 * and call {@link XDebuggerManager#startSession} from {@link com.intellij.execution.runners.ProgramRunner#execute} method
 *
 * Otherwise use method {@link XDebuggerManager#startSessionAndShowTab} to start new debugging session
 *
 * @author nik
 */
public abstract class XDebugProcess {
  private final XDebugSession mySession;
  private ProcessHandler myProcessHandler;

  /**
   * @param session pass {@code session} parameter of {@link XDebugProcessStarter#start} method to this constructor
   */
  protected XDebugProcess(@NotNull XDebugSession session) {
    mySession = session;
  }

  public final XDebugSession getSession() {
    return mySession;
  }

  /**
   * @return breakpoint handlers which will be used to set/clear breakpoints in the underlying debugging process
   */
  @NotNull
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return XBreakpointHandler.EMPTY_ARRAY;
  }

  /**
   * @return editor provider which will be used to produce editors for "Evaluate" and "Set Value" actions
   */
  @NotNull
  public abstract XDebuggerEditorsProvider getEditorsProvider();

  /**
   * Called when {@link XDebugSession} is initialized and breakpoints are registered in
   * {@link XBreakpointHandler}
   */
  public void sessionInitialized() {
  }

  /**
   * Interrupt debugging process and call {@link XDebugSession#positionReached}
   * when next line in current method/function is reached.
   * Do not call this method directly. Use {@link XDebugSession#pause()} instead
   */
  public void startPausing() {
  }

  /**
   * @deprecated Use {@link #startStepOver(XSuspendContext)} instead
   */
  @Deprecated
  public void startStepOver() {
    throw new AbstractMethodError();
  }

  /**
   * Resume execution and call {@link XDebugSession#positionReached}
   * when next line in current method/function is reached.
   * Do not call this method directly. Use {@link XDebugSession#stepOver} instead
   */
  public void startStepOver(@Nullable XSuspendContext context) {
    //noinspection deprecation
    startStepOver();
  }

  /**
   * @deprecated Use {@link #startForceStepInto(XSuspendContext)} instead
   */
  @Deprecated
  public void startForceStepInto(){
    //noinspection deprecation
    startStepInto();
  }

  /**
   * Steps into suppressed call
   * <p>
   * Resume execution and call {@link XDebugSession#positionReached}
   * when next line is reached.
   * Do not call this method directly. Use {@link XDebugSession#forceStepInto} instead
   */
  public void startForceStepInto(@Nullable XSuspendContext context) {
    startStepInto(context);
  }

  /**
   * @deprecated Use {@link #startStepInto(XSuspendContext)} instead
   */
  @Deprecated
  public void startStepInto() {
    throw new AbstractMethodError();
  }

  /**
   * Resume execution and call {@link XDebugSession#positionReached}
   * when next line is reached.
   * Do not call this method directly. Use {@link XDebugSession#stepInto} instead
   */
  public void startStepInto(@Nullable XSuspendContext context) {
    //noinspection deprecation
    startStepInto();
  }

  /**
   * @deprecated Use {@link #startStepOut(XSuspendContext)} instead
   */
  @Deprecated
  public void startStepOut() {
    throw new AbstractMethodError();
  }

  /**
   * Resume execution and call {@link XDebugSession#positionReached}
   * after returning from current method/function.
   * Do not call this method directly. Use {@link XDebugSession#stepOut} instead
   */
  public void startStepOut(@Nullable XSuspendContext context) {
    //noinspection deprecation
    startStepOut();
  }

  /**
   * Implement {@link XSmartStepIntoHandler} and return its instance from this method to enable Smart Step Into action
   * @return {@link XSmartStepIntoHandler} instance
   */
  @Nullable
  public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
    return null;
  }

  /**
   * Stop debugging and dispose resources.
   * Do not call this method directly. Use {@link XDebugSession#stop} instead
   */
  public void stop() {
    throw new AbstractMethodError();
  }

  @NotNull
  public Promise stopAsync() {
    stop();
    return Promises.resolvedPromise();
  }

  /**
   * @deprecated Use {@link #resume(XSuspendContext)} instead
   */
  @Deprecated
  public void resume() {
    throw new AbstractMethodError();
  }

  /**
   * Resume execution.
   * Do not call this method directly. Use {@link XDebugSession#resume} instead
   */
  public void resume(@Nullable XSuspendContext context) {
    //noinspection deprecation
    resume();
  }

  /**
   * @deprecated Use {@link #runToPosition(XSourcePosition, XSuspendContext)} instead
   */
  @Deprecated
  public void runToPosition(@NotNull XSourcePosition position) {
    throw new AbstractMethodError();
  }

  /**
   * Resume execution and call {@link XDebugSession#positionReached(XSuspendContext)}
   * when {@code position} is reached.
   * Do not call this method directly. Use {@link XDebugSession#runToPosition} instead
   *
   * @param position position in source code
   */
  public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
    //noinspection deprecation
    runToPosition(position);
  }

  /**
   * Check is it is possible to perform commands such as resume, step etc. And notify user if necessary
   * @return {@code true} if process can actually perform user requests at this moment
   */
  public boolean checkCanPerformCommands() {
    return true;
  }

  /**
   * Check is it is possible to init breakpoints. Otherwise you should call {@link XDebugSession#initBreakpoints()} at the appropriate time
   */
  public boolean checkCanInitBreakpoints() {
    return true;
  }

  @Nullable
  protected ProcessHandler doGetProcessHandler() {
    return null;
  }

  @NotNull
  public final ProcessHandler getProcessHandler() {
    if (myProcessHandler == null) {
      myProcessHandler = doGetProcessHandler();
      if (myProcessHandler == null) {
        myProcessHandler = new DefaultDebugProcessHandler();
      }
    }
    return myProcessHandler;
  }

  @NotNull
  public ExecutionConsole createConsole() {
    return TextConsoleBuilderFactory.getInstance().createBuilder(getSession().getProject()).getConsole();
  }

  /**
   * Override this method to enable 'Mark Object' action
   * @return new instance of {@link XValueMarkerProvider}'s implementation or {@code null} if 'Mark Object' feature isn't supported
   */
  @Nullable
  public XValueMarkerProvider<?,?> createValueMarkerProvider() {
    return null;
  }

  /**
   * Override this method to provide additional actions in 'Debug' tool window
   */
  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar, @NotNull DefaultActionGroup topToolbar, @NotNull DefaultActionGroup settings) {
  }

  /**
   * @return message to show in Variables View when debugger isn't paused
   */
  public String getCurrentStateMessage() {
    return mySession.isStopped() ? XDebuggerBundle.message("debugger.state.message.disconnected") : XDebuggerBundle.message("debugger.state.message.connected");
  }

  @Nullable
  public HyperlinkListener getCurrentStateHyperlinkListener() {
    return null;
  }

  /**
   * Override this method to customize content of tab in 'Debug' tool window
   */
  @NotNull
  public XDebugTabLayouter createTabLayouter() {
    return new XDebugTabLayouter() {
    };
  }

  /**
   * Add or not SortValuesAction (alphabetically sort)
   * @todo this action should be moved to "Variables" as gear action
   */
  public boolean isValuesCustomSorted() {
    return false;
  }

  @Nullable
  public XDebuggerEvaluator getEvaluator() {
    XStackFrame frame = getSession().getCurrentStackFrame();
    return frame == null ? null : frame.getEvaluator();
  }

  /**
   * Is "isShowLibraryStackFrames" setting respected. If true, ShowLibraryFramesAction will be shown, for example.
   */
  public boolean isLibraryFrameFilterSupported() {
    return false;
  }

  /**
   * Called to log the stacktrace on breakpoint hit if {@link XBreakpoint#isLogStack()} is true
   */
  public void logStack(@NotNull XSuspendContext suspendContext, @NotNull XDebugSession session) {
    XDebuggerUtil.getInstance().logStack(suspendContext, session);
  }
}
