/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.breakpoints.*;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class XDebugSessionImpl implements XDebugSession {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.XDebugSessionImpl");
  private XDebugProcess myDebugProcess;
  private final Map<XBreakpoint<?>, CustomizedBreakpointPresentation> myRegisteredBreakpoints =
    new HashMap<XBreakpoint<?>, CustomizedBreakpointPresentation>();
  private final Set<XBreakpoint<?>> myDisabledSlaveBreakpoints = new HashSet<XBreakpoint<?>>();
  private boolean myBreakpointsMuted;
  private boolean myBreakpointsDisabled;
  private final XDebuggerManagerImpl myDebuggerManager;
  private MyBreakpointListener myBreakpointListener;
  private XSuspendContext mySuspendContext;
  private XStackFrame myCurrentStackFrame;
  private XSourcePosition myCurrentPosition;
  private boolean myPaused;
  private MyDependentBreakpointListener myDependentBreakpointListener;
  private String mySessionName;
  private XDebugSessionTab mySessionTab;
  private XDebugSessionData mySessionData;
  private final EventDispatcher<XDebugSessionListener> myDispatcher = EventDispatcher.create(XDebugSessionListener.class);
  private Project myProject;
  private @Nullable ExecutionEnvironment myEnvironment;
  private ProgramRunner myRunner;
  private boolean myStopped;
  private boolean myPauseActionSupported;
  private boolean myShowTabOnSuspend;
  private ConsoleView myConsoleView;

  public XDebugSessionImpl(final @NotNull ExecutionEnvironment env,
                           final @NotNull ProgramRunner runner,
                           XDebuggerManagerImpl debuggerManager) {
    this(env, runner, debuggerManager, env.getRunProfile().getName(), false);
  }

  public XDebugSessionImpl(final @Nullable ExecutionEnvironment env,
                           final @Nullable ProgramRunner runner,
                           XDebuggerManagerImpl debuggerManager,
                           final @NotNull String sessionName, final boolean showTabOnSuspend) {
    myEnvironment = env;
    myRunner = runner;
    mySessionName = sessionName;
    myDebuggerManager = debuggerManager;
    myShowTabOnSuspend = showTabOnSuspend;
    myProject = debuggerManager.getProject();
    ValueLookupManager.getInstance(myProject).startListening();
  }

  @NotNull
  public String getSessionName() {
    return mySessionName;
  }

  @NotNull
  public RunContentDescriptor getRunContentDescriptor() {
    assertSessionTabInitialized();
    return mySessionTab.getRunContentDescriptor();
  }

  private void assertSessionTabInitialized() {
    if (myShowTabOnSuspend) {
      LOG.error("Debug tool window isn't shown yet because debug process isn't suspended");
    }
    else {
      LOG.assertTrue(mySessionTab != null, "Debug tool window not initialized yet!");
    }
  }

  public void setPauseActionSupported(final boolean isSupported) {
    myPauseActionSupported = isSupported;
  }

  public void rebuildViews() {
    if (!myShowTabOnSuspend) {
      mySessionTab.rebuildViews();
    }
  }

  @Nullable
  public RunProfile getRunProfile() {
    return myEnvironment != null ? myEnvironment.getRunProfile() : null;
  }

  public boolean isPauseActionSupported() {
    return myPauseActionSupported;
  }

  @NotNull
  public Project getProject() {
    return myDebuggerManager.getProject();
  }

  @NotNull
  public XDebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  public boolean isSuspended() {
    return myPaused && mySuspendContext != null;
  }

  public boolean isPaused() {
    return myPaused;
  }

  @Nullable
  public XStackFrame getCurrentStackFrame() {
    return myCurrentStackFrame;
  }

  public XSuspendContext getSuspendContext() {
    return mySuspendContext;
  }

  @Nullable
  public XSourcePosition getCurrentPosition() {
    return myCurrentPosition;
  }

  public XDebugSessionTab init(final XDebugProcess process, @NotNull final XDebugSessionData sessionData) {
    LOG.assertTrue(myDebugProcess == null);
    myDebugProcess = process;
    mySessionData = sessionData;

    XBreakpointManagerImpl breakpointManager = myDebuggerManager.getBreakpointManager();
    XDependentBreakpointManager dependentBreakpointManager = breakpointManager.getDependentBreakpointManager();
    disableSlaveBreakpoints(dependentBreakpointManager);
    processAllBreakpoints(true, false);

    myBreakpointListener = new MyBreakpointListener();
    breakpointManager.addBreakpointListener(myBreakpointListener);
    myDependentBreakpointListener = new MyDependentBreakpointListener();
    dependentBreakpointManager.addListener(myDependentBreakpointListener);

    myDebugProcess.getProcessHandler().addProcessListener(new ProcessAdapter() {
      public void processTerminated(final ProcessEvent event) {
        stopImpl();
      }
    });
    //todo[nik] make 'createConsole()' method return ConsoleView
    myConsoleView = (ConsoleView)myDebugProcess.createConsole();
    if (!myShowTabOnSuspend) {
      initSessionTab();
    }

    return mySessionTab;
  }

  @Override
  public ConsoleView getConsoleView() {
    return myConsoleView;
  }

  public XDebugSessionTab getSessionTab() {
    return mySessionTab;
  }

  @Override
  public RunnerLayoutUi getUI() {
    return mySessionTab.getUi();
  }

  private void initSessionTab() {
    mySessionTab = new XDebugSessionTab(myProject, mySessionName);
    if (myEnvironment != null) {
      mySessionTab.setEnvironment(myEnvironment);
    }
    Disposer.register(myProject, mySessionTab);
    mySessionTab.attachToSession(this, myRunner, myEnvironment, mySessionData, myConsoleView);
    myDebugProcess.sessionInitialized();
  }

  public XDebugSessionData getSessionData() {
    return mySessionData;
  }

  private void disableSlaveBreakpoints(final XDependentBreakpointManager dependentBreakpointManager) {
    Set<XBreakpoint<?>> slaveBreakpoints = dependentBreakpointManager.getAllSlaveBreakpoints();
    Set<XBreakpointType<?, ?>> breakpointTypes = new HashSet<XBreakpointType<?, ?>>();
    for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
      breakpointTypes.add(getBreakpointTypeClass(handler));
    }
    for (XBreakpoint<?> slaveBreakpoint : slaveBreakpoints) {
      if (breakpointTypes.contains(slaveBreakpoint.getType())) {
        myDisabledSlaveBreakpoints.add(slaveBreakpoint);
      }
    }
  }

  public void showSessionTab() {
    RunContentDescriptor descriptor = getRunContentDescriptor();
    ExecutionManager.getInstance(getProject()).getContentManager().showRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), descriptor);
  }

  private static <B extends XBreakpoint<?>> XBreakpointType<?, ?> getBreakpointTypeClass(final XBreakpointHandler<B> handler) {
    return XDebuggerUtil.getInstance().findBreakpointType(handler.getBreakpointTypeClass());
  }

  private <B extends XBreakpoint<?>> void processBreakpoints(final XBreakpointHandler<B> handler,
                                                             boolean register,
                                                             final boolean temporary) {
    XBreakpointType<B, ?> type = XDebuggerUtil.getInstance().findBreakpointType(handler.getBreakpointTypeClass());
    Collection<? extends B> breakpoints = myDebuggerManager.getBreakpointManager().getBreakpoints(type);
    for (B b : breakpoints) {
      handleBreakpoint(handler, b, register, temporary);
    }
  }

  private <B extends XBreakpoint<?>> void handleBreakpoint(final XBreakpointHandler<B> handler, final B b, final boolean register,
                                                           final boolean temporary) {
    if (register && isBreakpointActive(b)) {
      synchronized (myRegisteredBreakpoints) {
        myRegisteredBreakpoints.put(b, new CustomizedBreakpointPresentation());
      }
      handler.registerBreakpoint(b);
    }
    if (!register) {
      boolean removed = false;
      synchronized (myRegisteredBreakpoints) {
        if (myRegisteredBreakpoints.containsKey(b)) {
          myRegisteredBreakpoints.remove(b);
          removed = true;
        }
      }
      if (removed) {
        handler.unregisterBreakpoint(b, temporary);
      }
    }
  }

  @Nullable
  public CustomizedBreakpointPresentation getBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint) {
    synchronized (myRegisteredBreakpoints) {
      return myRegisteredBreakpoints.get(breakpoint);
    }
  }

  private void processAllHandlers(final XBreakpoint<?> breakpoint, final boolean register) {
    for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
      processBreakpoint(breakpoint, handler, register);
    }
  }

  private <B extends XBreakpoint<?>> void processBreakpoint(final XBreakpoint<?> breakpoint,
                                                            final XBreakpointHandler<B> handler,
                                                            boolean register) {
    XBreakpointType<?, ?> type = breakpoint.getType();
    if (handler.getBreakpointTypeClass().equals(type.getClass())) {
      //noinspection unchecked
      B b = (B)breakpoint;
      handleBreakpoint(handler, b, register, false);
    }
  }

  private boolean isBreakpointActive(final XBreakpoint<?> b) {
    return !myBreakpointsMuted && b.isEnabled() && !myDisabledSlaveBreakpoints.contains(b);
  }

  public boolean areBreakpointsMuted() {
    return myBreakpointsMuted;
  }

  public void addSessionListener(@NotNull final XDebugSessionListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeSessionListener(@NotNull final XDebugSessionListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void setBreakpointMuted(boolean muted) {
    if (myBreakpointsMuted == muted) return;
    myBreakpointsMuted = muted;
    processAllBreakpoints(!muted, muted);
    myDebuggerManager.getBreakpointManager().getLineBreakpointManager().queueAllBreakpointsUpdate();
  }

  public void stepOver(final boolean ignoreBreakpoints) {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    if (ignoreBreakpoints) {
      disableBreakpoints();
    }
    doResume();
    myDebugProcess.startStepOver();
  }

  public void stepInto() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    doResume();
    myDebugProcess.startStepInto();
  }

  public void stepOut() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    doResume();
    myDebugProcess.startStepOut();
  }

  public <V extends XSmartStepIntoVariant> void smartStepInto(XSmartStepIntoHandler<V> handler, V variant) {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    doResume();
    handler.startStepInto(variant);
  }

  public void forceStepInto() {
    stepInto();
  }

  public void runToPosition(@NotNull final XSourcePosition position, final boolean ignoreBreakpoints) {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    if (ignoreBreakpoints) {
      disableBreakpoints();
    }
    doResume();
    myDebugProcess.runToPosition(position);
  }

  public void pause() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    myDebugProcess.startPausing();
  }

  private void processAllBreakpoints(final boolean register, final boolean temporary) {
    for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
      processBreakpoints(handler, register, temporary);
    }
  }

  private void disableBreakpoints() {
    myBreakpointsDisabled = true;
    processAllBreakpoints(false, true);
  }

  public void resume() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    doResume();
    myDebugProcess.resume();
  }

  private void doResume() {
    myDispatcher.getMulticaster().beforeSessionResume();
    myDebuggerManager.setActiveSession(this, null, false);
    mySuspendContext = null;
    myCurrentStackFrame = null;
    myCurrentPosition = null;
    myPaused = false;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        mySessionTab.getUi().clearAttractionBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
      }
    });
    myDispatcher.getMulticaster().sessionResumed();
  }

  @Override
  public void updateExecutionPosition() {
    XExecutionStack activeExecutionStack = mySuspendContext.getActiveExecutionStack();
    boolean isTopFrame = activeExecutionStack != null && activeExecutionStack.getTopFrame() == myCurrentStackFrame;
    myDebuggerManager.updateExecutionPoint(myCurrentStackFrame.getSourcePosition(), !isTopFrame);
  }


  public void showExecutionPoint() {
    if (mySuspendContext != null) {
      XExecutionStack executionStack = mySuspendContext.getActiveExecutionStack();
      if (executionStack != null) {
        XStackFrame topFrame = executionStack.getTopFrame();
        if (topFrame != null) {
          setCurrentStackFrame(topFrame);
          myDebuggerManager.showExecutionPosition();
        }
      }
    }
  }

  public void setCurrentStackFrame(@NotNull final XStackFrame frame) {
    if (mySuspendContext == null) return;

    boolean frameChanged = myCurrentStackFrame != frame;
    myCurrentStackFrame = frame;
    activateSession();

    if (frameChanged) {
      myDispatcher.getMulticaster().stackFrameChanged();
    }
  }

  public void activateSession() {
    XSourcePosition position = myCurrentStackFrame != null ? myCurrentStackFrame.getSourcePosition() : null;
    if (position != null) {
      XExecutionStack activeExecutionStack = mySuspendContext.getActiveExecutionStack();
      boolean isTopFrame = activeExecutionStack != null && activeExecutionStack.getTopFrame() == myCurrentStackFrame;
      myDebuggerManager.setActiveSession(this, position, !isTopFrame);
    }
    else {
      myDebuggerManager.setActiveSession(this, null, false);
    }
  }

  public void updateBreakpointPresentation(@NotNull final XLineBreakpoint<?> breakpoint,
                                           @Nullable final Icon icon,
                                           @Nullable final String errorMessage) {
    CustomizedBreakpointPresentation presentation;
    synchronized (myRegisteredBreakpoints) {
      presentation = myRegisteredBreakpoints.get(breakpoint);
      if (presentation != null) {
        presentation.setErrorMessage(errorMessage);
        presentation.setIcon(icon);
      }
    }
    if (presentation != null) {
      myDebuggerManager.getBreakpointManager().getLineBreakpointManager().queueBreakpointUpdate((XLineBreakpointImpl<?>)breakpoint);
    }
  }

  public boolean breakpointReached(@NotNull final XBreakpoint<?> breakpoint, @NotNull final XSuspendContext suspendContext) {
    return breakpointReached(breakpoint, null, suspendContext);
  }

  public boolean breakpointReached(@NotNull XBreakpoint<?> breakpoint, @Nullable String evaluatedLogExpression,
                                   @NotNull XSuspendContext suspendContext) {
    XDebuggerEvaluator evaluator = XDebuggerUtilImpl.getEvaluator(suspendContext);
    String condition = breakpoint.getCondition();
    if (condition != null && evaluator != null) {
      LOG.debug("evaluating condition: " + condition);
      boolean result = evaluator.evaluateCondition(condition);
      LOG.debug("condition evaluates to " + result);
      if (!result) {
        return false;
      }
    }

    if (breakpoint.isLogMessage()) {
      String text = StringUtil.decapitalize(XBreakpointUtil.getDisplayText(breakpoint));
      final XSourcePosition position = breakpoint.getSourcePosition();
      final OpenFileHyperlinkInfo hyperlinkInfo =
        position != null ? new OpenFileHyperlinkInfo(myProject, position.getFile(), position.getLine()) : null;
      printMessage(XDebuggerBundle.message("xbreakpoint.reached.text") + " ", text, hyperlinkInfo);
    }

    if (evaluatedLogExpression != null) {
      printMessage(evaluatedLogExpression, null, null);
    }
    else {
      String expression = breakpoint.getLogExpression();
      if (expression != null && evaluator != null) {
        LOG.debug("evaluating log expression: " + expression);
        final String message = evaluator.evaluateMessage(expression);
        if (message != null) {
          printMessage(message, null, null);
        }
      }
    }

    processDependencies(breakpoint);

    if (breakpoint.getSuspendPolicy() == SuspendPolicy.NONE) {
      return false;
    }

    positionReached(suspendContext);
    return true;
  }

  private void processDependencies(final XBreakpoint<?> breakpoint) {
    XDependentBreakpointManager dependentBreakpointManager = myDebuggerManager.getBreakpointManager().getDependentBreakpointManager();
    if (!dependentBreakpointManager.isMasterOrSlave(breakpoint)) return;

    List<XBreakpoint<?>> breakpoints = dependentBreakpointManager.getSlaveBreakpoints(breakpoint);
    myDisabledSlaveBreakpoints.removeAll(breakpoints);
    for (XBreakpoint<?> slaveBreakpoint : breakpoints) {
      processAllHandlers(slaveBreakpoint, true);
    }

    if (dependentBreakpointManager.getMasterBreakpoint(breakpoint) != null && !dependentBreakpointManager.isLeaveEnabled(breakpoint)) {
      boolean added = myDisabledSlaveBreakpoints.add(breakpoint);
      if (added) {
        processAllHandlers(breakpoint, false);
        myDebuggerManager.getBreakpointManager().getLineBreakpointManager().queueBreakpointUpdate(breakpoint);
      }
    }
  }

  private void printMessage(final String message, final String hyperLinkText, @Nullable final HyperlinkInfo info) {
    DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
      public void run() {
        myConsoleView.print(message, ConsoleViewContentType.SYSTEM_OUTPUT);
        if (info != null) {
          myConsoleView.printHyperlink(hyperLinkText, info);
        }
        else if (hyperLinkText != null) {
          myConsoleView.print(hyperLinkText, ConsoleViewContentType.SYSTEM_OUTPUT);
        }
        myConsoleView.print("\n", ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    });
  }

  public void positionReached(@NotNull final XSuspendContext suspendContext) {
    enableBreakpoints();
    mySuspendContext = suspendContext;
    XExecutionStack executionStack = suspendContext.getActiveExecutionStack();
    myCurrentStackFrame = executionStack != null ? executionStack.getTopFrame() : null;
    myCurrentPosition = myCurrentStackFrame != null ? myCurrentStackFrame.getSourcePosition() : null;

    myPaused = true;
    if (myCurrentPosition != null) {
      myDebuggerManager.setActiveSession(this, myCurrentPosition, false);
    }
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (myShowTabOnSuspend) {
          myShowTabOnSuspend = false;
          initSessionTab();
          showSessionTab();
        }
        mySessionTab.toFront();
        mySessionTab.getUi().attractBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
      }
    });
    myDispatcher.getMulticaster().sessionPaused();
  }

  @Override
  public void sessionResumed() {
    doResume();
  }

  private void enableBreakpoints() {
    if (myBreakpointsDisabled) {
      myBreakpointsDisabled = false;
      new ReadAction() {
        protected void run(final Result result) {
          processAllBreakpoints(true, false);
        }
      }.execute();
    }
  }

  public boolean isStopped() {
    return myStopped;
  }

  private void stopImpl() {
    if (myStopped) return;

    myDebugProcess.stop();
    myCurrentPosition = null;
    myCurrentStackFrame = null;
    mySuspendContext = null;
    myDebuggerManager.setActiveSession(this, null, false);
    XBreakpointManagerImpl breakpointManager = myDebuggerManager.getBreakpointManager();
    breakpointManager.removeBreakpointListener(myBreakpointListener);
    breakpointManager.getDependentBreakpointManager().removeListener(myDependentBreakpointListener);
    myStopped = true;
    myDebuggerManager.removeSession(this);
    myDispatcher.getMulticaster().sessionStopped();
  }

  public boolean isDisabledSlaveBreakpoint(final XBreakpoint<?> breakpoint) {
    return myDisabledSlaveBreakpoints.contains(breakpoint);
  }

  public void stop() {
    ProcessHandler processHandler = myDebugProcess.getProcessHandler();
    if (processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) return;

    if (processHandler.detachIsDefault()) {
      processHandler.detachProcess();
    }
    else {
      processHandler.destroyProcess();
    }
  }

  @Override
  public void reportError(@NotNull final String message) {
    reportMessage(message, MessageType.ERROR);
  }

  @Override
  public void reportMessage(@NotNull final String message, @NotNull final MessageType type) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        ToolWindowManager.getInstance(myProject).notifyByBalloon(ToolWindowId.DEBUG, type, message, null, null);
      }
    });
  }

  private class MyBreakpointListener implements XBreakpointListener<XBreakpoint<?>> {
    public void breakpointAdded(@NotNull final XBreakpoint<?> breakpoint) {
      if (!myBreakpointsDisabled) {
        processAllHandlers(breakpoint, true);
      }
    }

    public void breakpointRemoved(@NotNull final XBreakpoint<?> breakpoint) {
      processAllHandlers(breakpoint, false);
    }

    public void breakpointChanged(@NotNull final XBreakpoint<?> breakpoint) {
      breakpointRemoved(breakpoint);
      breakpointAdded(breakpoint);
    }
  }

  private class MyDependentBreakpointListener implements XDependentBreakpointListener {
    public void dependencySet(final XBreakpoint<?> slave, final XBreakpoint<?> master) {
      boolean added = myDisabledSlaveBreakpoints.add(slave);
      if (added) {
        processAllHandlers(slave, false);
      }
    }

    public void dependencyCleared(final XBreakpoint<?> breakpoint) {
      boolean removed = myDisabledSlaveBreakpoints.remove(breakpoint);
      if (removed) {
        processAllHandlers(breakpoint, true);
      }
    }
  }
}
