/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.impl.breakpoints.*;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsManager;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nik
 */
public class XDebugSessionImpl implements XDebugSession {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.XDebugSessionImpl");
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("Debugger messages", ToolWindowId.DEBUG,
                                                                                               false);
  private XDebugProcess myDebugProcess;
  private final Map<XBreakpoint<?>, CustomizedBreakpointPresentation> myRegisteredBreakpoints =
    new THashMap<XBreakpoint<?>, CustomizedBreakpointPresentation>();
  private final Set<XBreakpoint<?>> myInactiveSlaveBreakpoints = Collections.synchronizedSet(new SmartHashSet<XBreakpoint<?>>());
  private boolean myBreakpointsDisabled;
  private final XDebuggerManagerImpl myDebuggerManager;
  private MyBreakpointListener myBreakpointListener;
  private XSuspendContext mySuspendContext;
  private XExecutionStack myCurrentExecutionStack;
  private XStackFrame myCurrentStackFrame;
  private XSourcePosition myCurrentPosition;
  private final AtomicBoolean myPaused = new AtomicBoolean();
  private MyDependentBreakpointListener myDependentBreakpointListener;
  private XValueMarkers<?, ?> myValueMarkers;
  private final String mySessionName;
  private @Nullable XDebugSessionTab mySessionTab;
  private XDebugSessionData mySessionData;
  private XBreakpoint<?> myActiveNonLineBreakpoint;
  private final EventDispatcher<XDebugSessionListener> myDispatcher = EventDispatcher.create(XDebugSessionListener.class);
  private final Project myProject;
  private final @Nullable ExecutionEnvironment myEnvironment;
  private boolean myStopped;
  private boolean myPauseActionSupported;
  private final AtomicBoolean myShowTabOnSuspend;
  private final List<AnAction> myRestartActions = new SmartList<AnAction>();
  private final List<AnAction> myExtraStopActions = new SmartList<AnAction>();
  private final List<AnAction> myExtraActions = new SmartList<AnAction>();
  private ConsoleView myConsoleView;
  private final Icon myIcon;

  private volatile boolean breakpointsInitialized;
  private boolean autoInitBreakpoints = true;

  public XDebugSessionImpl(@NotNull ExecutionEnvironment environment, @NotNull XDebuggerManagerImpl debuggerManager) {
    this(environment, debuggerManager, environment.getRunProfile().getName(), environment.getRunProfile().getIcon(), false);
  }

  public XDebugSessionImpl(@Nullable ExecutionEnvironment environment,
                           @NotNull XDebuggerManagerImpl debuggerManager,
                           @NotNull String sessionName,
                           @Nullable Icon icon,
                           boolean showTabOnSuspend) {
    myEnvironment = environment;
    mySessionName = sessionName;
    myDebuggerManager = debuggerManager;
    myShowTabOnSuspend = new AtomicBoolean(showTabOnSuspend);
    myProject = debuggerManager.getProject();
    ValueLookupManager.getInstance(myProject).startListening();
    myIcon = icon;
  }

  @Override
  @NotNull
  public String getSessionName() {
    return mySessionName;
  }

  @Override
  @NotNull
  public RunContentDescriptor getRunContentDescriptor() {
    assertSessionTabInitialized();
    //noinspection ConstantConditions
    return mySessionTab.getRunContentDescriptor();
  }

  private void assertSessionTabInitialized() {
    if (myShowTabOnSuspend.get()) {
      LOG.error("Debug tool window isn't shown yet because debug process isn't suspended");
    }
    else {
      LOG.assertTrue(mySessionTab != null, "Debug tool window not initialized yet!");
    }
  }

  @Override
  public void setAutoInitBreakpoints(boolean value) {
    autoInitBreakpoints = value;
  }

  @Override
  public void setPauseActionSupported(final boolean isSupported) {
    myPauseActionSupported = isSupported;
  }

  @NotNull
  public List<AnAction> getRestartActions() {
    return myRestartActions;
  }

  public void addRestartActions(AnAction... restartActions) {
    if (restartActions != null) {
      Collections.addAll(myRestartActions, restartActions);
    }
  }

  @NotNull
  public List<AnAction> getExtraActions() {
    return myExtraActions;
  }

  public void addExtraActions(AnAction... extraActions) {
    if (extraActions != null) {
      Collections.addAll(myExtraActions, extraActions);
    }
  }

  public List<AnAction> getExtraStopActions() {
    return myExtraStopActions;
  }

  public void addExtraStopActions(AnAction... extraStopActions) {
    if (extraStopActions != null) {
      Collections.addAll(myExtraStopActions, extraStopActions);
    }
  }

  @Override
  public void rebuildViews() {
    if (!myShowTabOnSuspend.get() && mySessionTab != null) {
      mySessionTab.rebuildViews();
    }
  }

  @Override
  @Nullable
  public RunProfile getRunProfile() {
    return myEnvironment != null ? myEnvironment.getRunProfile() : null;
  }

  public boolean isPauseActionSupported() {
    return myPauseActionSupported;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myDebuggerManager.getProject();
  }

  @Override
  @NotNull
  public XDebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @Override
  public boolean isSuspended() {
    return myPaused.get() && mySuspendContext != null;
  }

  @Override
  public boolean isPaused() {
    return myPaused.get();
  }

  @Override
  @Nullable
  public XStackFrame getCurrentStackFrame() {
    return myCurrentStackFrame;
  }

  @Override
  public XSuspendContext getSuspendContext() {
    return mySuspendContext;
  }

  @Override
  @Nullable
  public XSourcePosition getCurrentPosition() {
    return myCurrentPosition;
  }

  public XDebugSessionTab init(@NotNull XDebugProcess process, @NotNull XDebugSessionData sessionData, @Nullable RunContentDescriptor contentToReuse) {
    LOG.assertTrue(myDebugProcess == null);
    myDebugProcess = process;
    mySessionData = sessionData;

    if (autoInitBreakpoints && myDebugProcess.checkCanInitBreakpoints()) {
      initBreakpoints();
    }

    myDebugProcess.getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        stopImpl();
      }
    });
    //todo[nik] make 'createConsole()' method return ConsoleView
    myConsoleView = (ConsoleView)myDebugProcess.createConsole();
    if (!myShowTabOnSuspend.get()) {
      initSessionTab(contentToReuse);
    }

    return mySessionTab;
  }

  @Override
  public void initBreakpoints() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LOG.assertTrue(!breakpointsInitialized);
    breakpointsInitialized = true;

    XBreakpointManagerImpl breakpointManager = myDebuggerManager.getBreakpointManager();
    XDependentBreakpointManager dependentBreakpointManager = breakpointManager.getDependentBreakpointManager();
    disableSlaveBreakpoints(dependentBreakpointManager);
    processAllBreakpoints(true, false);

    myBreakpointListener = new MyBreakpointListener();
    breakpointManager.addBreakpointListener(myBreakpointListener);
    myDependentBreakpointListener = new MyDependentBreakpointListener();
    dependentBreakpointManager.addListener(myDependentBreakpointListener);
  }

  @Override
  public ConsoleView getConsoleView() {
    return myConsoleView;
  }

  @Nullable
  public XDebugSessionTab getSessionTab() {
    return mySessionTab;
  }

  @Override
  public RunnerLayoutUi getUI() {
    assertSessionTabInitialized();
    assert mySessionTab != null;
    return mySessionTab.getUi();
  }

  private void initSessionTab(@Nullable RunContentDescriptor contentToReuse) {
    mySessionTab = XDebugSessionTab.create(this, myIcon, myEnvironment, contentToReuse);
    myDebugProcess.sessionInitialized();
  }

  public XDebugSessionData getSessionData() {
    return mySessionData;
  }

  private void disableSlaveBreakpoints(final XDependentBreakpointManager dependentBreakpointManager) {
    Set<XBreakpoint<?>> slaveBreakpoints = dependentBreakpointManager.getAllSlaveBreakpoints();
    if (slaveBreakpoints.isEmpty()) {
      return;
    }

    Set<XBreakpointType<?, ?>> breakpointTypes = new THashSet<XBreakpointType<?, ?>>();
    for (XBreakpointHandler<?> handler : myDebugProcess.getBreakpointHandlers()) {
      breakpointTypes.add(getBreakpointTypeClass(handler));
    }
    for (XBreakpoint<?> slaveBreakpoint : slaveBreakpoints) {
      if (breakpointTypes.contains(slaveBreakpoint.getType())) {
        myInactiveSlaveBreakpoints.add(slaveBreakpoint);
      }
    }
  }

  public void showSessionTab() {
    RunContentDescriptor descriptor = getRunContentDescriptor();
    ExecutionManager.getInstance(getProject()).getContentManager()
      .showRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), descriptor);
  }

  @Nullable
  public XValueMarkers<?, ?> getValueMarkers() {
    if (myValueMarkers == null) {
      XValueMarkerProvider<?, ?> provider = myDebugProcess.createValueMarkerProvider();
      if (provider != null) {
        myValueMarkers = XValueMarkers.createValueMarkers(provider);
      }
    }
    return myValueMarkers;
  }

  @SuppressWarnings("unchecked") //need to compile under 1.8, please do not remove before checking
  private static XBreakpointType getBreakpointTypeClass(final XBreakpointHandler handler) {
    return XDebuggerUtil.getInstance().findBreakpointType(handler.getBreakpointTypeClass());
  }

  private <B extends XBreakpoint<?>> void processBreakpoints(final XBreakpointHandler<B> handler,
                                                             boolean register,
                                                             final boolean temporary) {
    Collection<? extends B> breakpoints = myDebuggerManager.getBreakpointManager().getBreakpoints(handler.getBreakpointTypeClass());
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
      boolean removed;
      synchronized (myRegisteredBreakpoints) {
        removed = myRegisteredBreakpoints.remove(b) != null;
      }
      if (removed) {
        handler.unregisterBreakpoint(b, temporary);
      }
    }
  }

  @Nullable
  public CustomizedBreakpointPresentation getBreakpointPresentation(@NotNull XBreakpoint<?> breakpoint) {
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

  public boolean isBreakpointActive(final XBreakpoint<?> b) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return !areBreakpointsMuted() && b.isEnabled() && !isInactiveSlaveBreakpoint(b);
  }

  @Override
  public boolean areBreakpointsMuted() {
    return mySessionData.isBreakpointsMuted();
  }

  @Override
  public void addSessionListener(@NotNull XDebugSessionListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public void addSessionListener(@NotNull final XDebugSessionListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeSessionListener(@NotNull final XDebugSessionListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public void setBreakpointMuted(boolean muted) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (areBreakpointsMuted() == muted) return;
    mySessionData.setBreakpointsMuted(muted);
    processAllBreakpoints(!muted, muted);
    myDebuggerManager.getBreakpointManager().getLineBreakpointManager().queueAllBreakpointsUpdate();
  }

  @Override
  public void stepOver(final boolean ignoreBreakpoints) {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    if (ignoreBreakpoints) {
      disableBreakpoints();
    }
    doResume();
    myDebugProcess.startStepOver();
  }

  @Override
  public void stepInto() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    doResume();
    myDebugProcess.startStepInto();
  }

  @Override
  public void stepOut() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    doResume();
    myDebugProcess.startStepOut();
  }

  @Override
  public <V extends XSmartStepIntoVariant> void smartStepInto(XSmartStepIntoHandler<V> handler, V variant) {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    doResume();
    handler.startStepInto(variant);
  }

  @Override
  public void forceStepInto() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    doResume();
    myDebugProcess.startForceStepInto();
  }

  @Override
  public void runToPosition(@NotNull final XSourcePosition position, final boolean ignoreBreakpoints) {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    if (ignoreBreakpoints) {
      disableBreakpoints();
    }
    doResume();
    myDebugProcess.runToPosition(position);
  }

  @Override
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

  @Override
  public void resume() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    doResume();
    myDebugProcess.resume();
  }

  private void doResume() {
    if (!myPaused.getAndSet(false)) return;

    myDispatcher.getMulticaster().beforeSessionResume();
    myDebuggerManager.setActiveSession(this, null, false, null);
    mySuspendContext = null;
    myCurrentExecutionStack = null;
    myCurrentStackFrame = null;
    adjustMouseTrackingCounter(myCurrentPosition, -1);
    myCurrentPosition = null;
    myActiveNonLineBreakpoint = null;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (mySessionTab != null) {
          mySessionTab.getUi().clearAttractionBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
        }
      }
    });
    myDispatcher.getMulticaster().sessionResumed();
  }

  @Override
  public void updateExecutionPosition() {
    boolean isTopFrame = isTopFrameSelected();
    myDebuggerManager.updateExecutionPoint(myCurrentStackFrame.getSourcePosition(), !isTopFrame, getPositionIconRenderer(isTopFrame));
  }

  private boolean isTopFrameSelected() {
    return myCurrentExecutionStack != null && myCurrentExecutionStack.getTopFrame() == myCurrentStackFrame;
  }


  @Override
  public void showExecutionPoint() {
    if (mySuspendContext != null) {
      XExecutionStack executionStack = mySuspendContext.getActiveExecutionStack();
      if (executionStack != null) {
        XStackFrame topFrame = executionStack.getTopFrame();
        if (topFrame != null) {
          setCurrentStackFrame(executionStack, topFrame);
          myDebuggerManager.showExecutionPosition();
        }
      }
    }
  }

  @Override
  public void setCurrentStackFrame(@NotNull final XStackFrame frame) {
    setCurrentStackFrame(myCurrentExecutionStack, frame);
  }

  @Override
  public void setCurrentStackFrame(@NotNull XExecutionStack executionStack, @NotNull XStackFrame frame) {
    if (mySuspendContext == null) return;

    boolean frameChanged = myCurrentStackFrame != frame;
    myCurrentExecutionStack = executionStack;
    myCurrentStackFrame = frame;
    activateSession();

    if (frameChanged) {
      myDispatcher.getMulticaster().stackFrameChanged();
    }
  }

  public void activateSession() {
    XSourcePosition position = myCurrentStackFrame != null ? myCurrentStackFrame.getSourcePosition() : null;
    if (position != null) {
      boolean isTopFrame = isTopFrameSelected();
      myDebuggerManager.setActiveSession(this, position, !isTopFrame, getPositionIconRenderer(isTopFrame));
    }
    else {
      myDebuggerManager.setActiveSession(this, null, false, null);
    }
  }

  public XBreakpoint<?> getActiveNonLineBreakpoint() {
    return myActiveNonLineBreakpoint;
  }

  @Nullable
  private GutterIconRenderer getPositionIconRenderer(boolean isTopFrame) {
    if (!isTopFrame) {
      return null;
    }
    if (myActiveNonLineBreakpoint != null) {
      return ((XBreakpointBase<?, ?, ?>)myActiveNonLineBreakpoint).createGutterIconRenderer();
    }
    if (myCurrentExecutionStack != null) {
      return myCurrentExecutionStack.getExecutionLineIconRenderer();
    }
    return null;
  }

  @Override
  public void updateBreakpointPresentation(@NotNull final XLineBreakpoint<?> breakpoint,
                                           @Nullable final Icon icon,
                                           @Nullable final String errorMessage) {
    CustomizedBreakpointPresentation presentation;
    synchronized (myRegisteredBreakpoints) {
      presentation = myRegisteredBreakpoints.get(breakpoint);
      if (presentation == null ||
          (Comparing.equal(presentation.getIcon(), icon) && Comparing.strEqual(presentation.getErrorMessage(), errorMessage))) {
        return;
      }

      presentation.setErrorMessage(errorMessage);
      presentation.setIcon(icon);
    }
    myDebuggerManager.getBreakpointManager().getLineBreakpointManager().queueBreakpointUpdate((XLineBreakpointImpl<?>)breakpoint);
  }

  @Override
  public boolean breakpointReached(@NotNull final XBreakpoint<?> breakpoint, @NotNull final XSuspendContext suspendContext) {
    return breakpointReached(breakpoint, null, suspendContext);
  }

  @Override
  public boolean breakpointReached(@NotNull final XBreakpoint<?> breakpoint, @Nullable String evaluatedLogExpression,
                                   @NotNull XSuspendContext suspendContext) {
    return breakpointReached(breakpoint, evaluatedLogExpression, suspendContext, true);
  }

  public void breakpointReachedNoProcessing(@NotNull final XBreakpoint<?> breakpoint, @NotNull XSuspendContext suspendContext) {
    breakpointReached(breakpoint, null, suspendContext, false);
  }

  private boolean breakpointReached(@NotNull final XBreakpoint<?> breakpoint, @Nullable String evaluatedLogExpression,
                                   @NotNull XSuspendContext suspendContext, boolean doProcessing) {
    if (doProcessing) {
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
    }

    myActiveNonLineBreakpoint = !(breakpoint instanceof XLineBreakpoint<?>) ? breakpoint : null;
    positionReached(suspendContext);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (mySessionTab != null) {
          mySessionTab.toFront(true);
          mySessionTab.getUi().attractBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
        }
      }
    });


    if (doProcessing && breakpoint instanceof XLineBreakpoint<?> && ((XLineBreakpoint)breakpoint).isTemporary()) {
      handleTemporaryBreakpointHit(breakpoint);
    }
    return true;
  }

  private void handleTemporaryBreakpointHit(final XBreakpoint<?> breakpoint) {
    addSessionListener(new XDebugSessionAdapter() {
      private void removeBreakpoint() {
        XDebuggerUtil.getInstance().removeBreakpoint(myProject, breakpoint);
        removeSessionListener(this);
      }

      @Override
      public void sessionResumed() {
        removeBreakpoint();
      }

      @Override
      public void sessionStopped() {
        removeBreakpoint();
      }
    });
  }

  public void processDependencies(final XBreakpoint<?> breakpoint) {
    XDependentBreakpointManager dependentBreakpointManager = myDebuggerManager.getBreakpointManager().getDependentBreakpointManager();
    if (!dependentBreakpointManager.isMasterOrSlave(breakpoint)) return;

    List<XBreakpoint<?>> breakpoints = dependentBreakpointManager.getSlaveBreakpoints(breakpoint);
    myInactiveSlaveBreakpoints.removeAll(breakpoints);
    for (XBreakpoint<?> slaveBreakpoint : breakpoints) {
      processAllHandlers(slaveBreakpoint, true);
    }

    if (dependentBreakpointManager.getMasterBreakpoint(breakpoint) != null && !dependentBreakpointManager.isLeaveEnabled(breakpoint)) {
      boolean added = myInactiveSlaveBreakpoints.add(breakpoint);
      if (added) {
        processAllHandlers(breakpoint, false);
        myDebuggerManager.getBreakpointManager().getLineBreakpointManager().queueBreakpointUpdate(breakpoint);
      }
    }
  }

  private void printMessage(final String message, final String hyperLinkText, @Nullable final HyperlinkInfo info) {
    AppUIUtil.invokeOnEdt(new Runnable() {
      @Override
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

  @Override
  public void positionReached(@NotNull final XSuspendContext suspendContext) {
    enableBreakpoints();
    mySuspendContext = suspendContext;
    myCurrentExecutionStack = suspendContext.getActiveExecutionStack();
    myCurrentStackFrame = myCurrentExecutionStack != null ? myCurrentExecutionStack.getTopFrame() : null;
    myCurrentPosition = myCurrentStackFrame != null ? myCurrentStackFrame.getSourcePosition() : null;

    myPaused.set(true);

    if (myCurrentPosition != null) {
      myDebuggerManager.setActiveSession(this, myCurrentPosition, false, getPositionIconRenderer(true));
    }
    adjustMouseTrackingCounter(myCurrentPosition, 1);

    if (myShowTabOnSuspend.compareAndSet(true, false)) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          initSessionTab(null);
          showSessionTab();
        }
      });
    }

    myDispatcher.getMulticaster().sessionPaused();
  }

  private void adjustMouseTrackingCounter(final XSourcePosition position, final int increment) {
    if (position == null || ApplicationManager.getApplication().isUnitTestMode()) return;

    // need to always invoke later to maintain order of increment/decrement
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        Editor editor = XDebuggerUtilImpl.createEditor(new OpenFileDescriptor(myProject, position.getFile()));
        if (editor != null) {
          JComponent component = editor.getComponent();
          Object o = component.getClientProperty(EditorImpl.IGNORE_MOUSE_TRACKING);
          Integer value = ((o instanceof Integer) ? (Integer)o : 0) + increment;
          component.putClientProperty(EditorImpl.IGNORE_MOUSE_TRACKING, value > 0 ? value : null);
        }
      }
    });
  }

  @Override
  public void sessionResumed() {
    doResume();
  }

  private void enableBreakpoints() {
    if (myBreakpointsDisabled) {
      myBreakpointsDisabled = false;
      new ReadAction() {
        @Override
        protected void run(@NotNull Result result) {
          processAllBreakpoints(true, false);
        }
      }.execute();
    }
  }

  @Override
  public boolean isStopped() {
    return myStopped;
  }

  private void stopImpl() {
    if (myStopped) return;

    myDebugProcess.stop();
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(XDebuggerManager.TOPIC).processStopped(myDebugProcess);
    }

    if (mySessionTab != null) {
      mySessionTab.detachFromSession();
    }

    adjustMouseTrackingCounter(myCurrentPosition, -1);
    myCurrentPosition = null;
    myCurrentExecutionStack = null;
    myCurrentStackFrame = null;
    mySuspendContext = null;
    myDebuggerManager.setActiveSession(this, null, false, null);
    if (breakpointsInitialized) {
      XBreakpointManagerImpl breakpointManager = myDebuggerManager.getBreakpointManager();
      if (myBreakpointListener != null) {
        breakpointManager.removeBreakpointListener(myBreakpointListener);
      }
      if (myDependentBreakpointListener != null) {
        breakpointManager.getDependentBreakpointManager().removeListener(myDependentBreakpointListener);
      }
    }
    if (myValueMarkers != null) {
      myValueMarkers.clear();
    }
    if (XDebuggerSettingsManager.getInstanceImpl().getGeneralSettings().isUnmuteOnStop()) {
      mySessionData.setBreakpointsMuted(false);
    }
    myStopped = true;
    myDebuggerManager.removeSession(this);
    myDispatcher.getMulticaster().sessionStopped();
  }

  public boolean isInactiveSlaveBreakpoint(final XBreakpoint<?> breakpoint) {
    return myInactiveSlaveBreakpoints.contains(breakpoint);
  }

  @Override
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
    reportMessage(message, type, null);
  }

  @Override
  public void reportMessage(@NotNull final String message, @NotNull final MessageType type, @Nullable final HyperlinkListener listener) {
    NotificationListener notificationListener = listener == null ? null : new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        listener.hyperlinkUpdate(event);
      }
    };
    NOTIFICATION_GROUP.createNotification("", message, type.toNotificationType(), notificationListener).notify(myProject);
  }

  private class MyBreakpointListener implements XBreakpointListener<XBreakpoint<?>> {
    @Override
    public void breakpointAdded(@NotNull final XBreakpoint<?> breakpoint) {
      if (!myBreakpointsDisabled) {
        processAllHandlers(breakpoint, true);
      }
    }

    @Override
    public void breakpointRemoved(@NotNull final XBreakpoint<?> breakpoint) {
      processAllHandlers(breakpoint, false);
    }

    @Override
    public void breakpointChanged(@NotNull final XBreakpoint<?> breakpoint) {
      breakpointRemoved(breakpoint);
      breakpointAdded(breakpoint);
    }
  }

  private class MyDependentBreakpointListener implements XDependentBreakpointListener {
    @Override
    public void dependencySet(final XBreakpoint<?> slave, final XBreakpoint<?> master) {
      boolean added = myInactiveSlaveBreakpoints.add(slave);
      if (added) {
        processAllHandlers(slave, false);
      }
    }

    @Override
    public void dependencyCleared(final XBreakpoint<?> breakpoint) {
      boolean removed = myInactiveSlaveBreakpoints.remove(breakpoint);
      if (removed) {
        processAllHandlers(breakpoint, true);
      }
    }
  }

  private String getWatchesKey() {
    if (myEnvironment != null) {
      RunProfile profile = myEnvironment.getRunProfile();
      if (profile instanceof RunConfiguration) {
        return ((RunConfiguration)profile).getType().getId();
      }
    }
    return getSessionName();
  }

  public void setWatchExpressions(@NotNull XExpression[] watchExpressions) {
    mySessionData.setWatchExpressions(watchExpressions);
    myDebuggerManager.getWatchesManager().setWatches(getWatchesKey(), watchExpressions);
  }

  XExpression[] getWatchExpressions() {
    return myDebuggerManager.getWatchesManager().getWatches(getWatchesKey());
  }
}
