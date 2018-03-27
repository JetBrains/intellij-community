/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ide.DataManager;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.impl.breakpoints.*;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEditorLinePainter;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.frame.XWatchesViewImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
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

  /** @deprecated Use {@link XDebuggerManagerImpl#NOTIFICATION_GROUP} */
  @Deprecated
  public static final NotificationGroup NOTIFICATION_GROUP = XDebuggerManagerImpl.NOTIFICATION_GROUP;

  private XDebugProcess myDebugProcess;
  private final Map<XBreakpoint<?>, CustomizedBreakpointPresentation> myRegisteredBreakpoints =
    new THashMap<>();
  private final Set<XBreakpoint<?>> myInactiveSlaveBreakpoints = Collections.synchronizedSet(new SmartHashSet<>());
  private boolean myBreakpointsDisabled;
  private final XDebuggerManagerImpl myDebuggerManager;
  private MyBreakpointListener myBreakpointListener;
  private XSuspendContext mySuspendContext;
  private XExecutionStack myCurrentExecutionStack;
  private XStackFrame myCurrentStackFrame;
  private boolean myIsTopFrame;
  private volatile XSourcePosition myTopFramePosition;
  private final AtomicBoolean myPaused = new AtomicBoolean();
  private MyDependentBreakpointListener myDependentBreakpointListener;
  private XValueMarkers<?, ?> myValueMarkers;
  private final String mySessionName;
  private @Nullable XDebugSessionTab mySessionTab;
  private @NotNull final XDebugSessionData mySessionData;
  private XBreakpoint<?> myActiveNonLineBreakpoint;
  private final EventDispatcher<XDebugSessionListener> myDispatcher = EventDispatcher.create(XDebugSessionListener.class);
  private final Project myProject;
  private final @Nullable ExecutionEnvironment myEnvironment;
  private final AtomicBoolean myStopped = new AtomicBoolean();
  private boolean myPauseActionSupported;
  private final AtomicBoolean myShowTabOnSuspend;
  private final List<AnAction> myRestartActions = new SmartList<>();
  private final List<AnAction> myExtraStopActions = new SmartList<>();
  private final List<AnAction> myExtraActions = new SmartList<>();
  private ConsoleView myConsoleView;
  private final Icon myIcon;

  private volatile boolean breakpointsInitialized;

  public XDebugSessionImpl(@NotNull ExecutionEnvironment environment, @NotNull XDebuggerManagerImpl debuggerManager) {
    this(environment, debuggerManager, environment.getRunProfile().getName(), environment.getRunProfile().getIcon(), false, null);
  }

  public XDebugSessionImpl(@Nullable ExecutionEnvironment environment,
                           @NotNull XDebuggerManagerImpl debuggerManager,
                           @NotNull String sessionName,
                           @Nullable Icon icon,
                           boolean showTabOnSuspend,
                           @Nullable RunContentDescriptor contentToReuse) {
    myEnvironment = environment;
    mySessionName = sessionName;
    myDebuggerManager = debuggerManager;
    myShowTabOnSuspend = new AtomicBoolean(showTabOnSuspend);
    myProject = debuggerManager.getProject();
    ValueLookupManager.getInstance(myProject).startListening();
    myIcon = icon;

    XDebugSessionData oldSessionData = null;
    if (contentToReuse == null) {
      contentToReuse = environment != null ? environment.getContentToReuse() : null;
    }
    if (contentToReuse != null) {
      JComponent component = contentToReuse.getComponent();
      if (component != null) {
        oldSessionData = XDebugSessionData.DATA_KEY.getData(DataManager.getInstance().getDataContext(component));
      }
    }

    String currentConfigurationName = getConfigurationName();
    if (oldSessionData == null || !oldSessionData.getConfigurationName().equals(currentConfigurationName)) {
      oldSessionData = new XDebugSessionData(getWatchExpressions(), currentConfigurationName);
    }
    mySessionData = oldSessionData;
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

  public XExecutionStack getCurrentExecutionStack() {
    return myCurrentExecutionStack;
  }

  @Override
  public XSuspendContext getSuspendContext() {
    return mySuspendContext;
  }

  @Override
  @Nullable
  public XSourcePosition getCurrentPosition() {
    return myCurrentStackFrame != null ? myCurrentStackFrame.getSourcePosition() : null;
  }

  @Nullable
  @Override
  public XSourcePosition getTopFramePosition() {
    return myTopFramePosition;
  }

  XDebugSessionTab init(@NotNull XDebugProcess process, @Nullable RunContentDescriptor contentToReuse) {
    LOG.assertTrue(myDebugProcess == null);
    myDebugProcess = process;

    if (myDebugProcess.checkCanInitBreakpoints()) {
      initBreakpoints();
    }

    myDebugProcess.getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull final ProcessEvent event) {
        stopImpl();
        myDebugProcess.getProcessHandler().removeProcessListener(this);
      }
    });
    //todo[nik] make 'createConsole()' method return ConsoleView
    myConsoleView = (ConsoleView)myDebugProcess.createConsole();
    if (!myShowTabOnSuspend.get()) {
      initSessionTab(contentToReuse);
    }

    return mySessionTab;
  }

  public void reset() {
    breakpointsInitialized = false;
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

    if (myBreakpointListener == null) {
      myBreakpointListener = new MyBreakpointListener();
      breakpointManager.addBreakpointListener(myBreakpointListener);
    }
    if (myDependentBreakpointListener == null) {
      myDependentBreakpointListener = new MyDependentBreakpointListener();
      dependentBreakpointManager.addListener(myDependentBreakpointListener);
    }
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

  @NotNull
  public XDebugSessionData getSessionData() {
    return mySessionData;
  }

  private void disableSlaveBreakpoints(final XDependentBreakpointManager dependentBreakpointManager) {
    Set<XBreakpoint<?>> slaveBreakpoints = dependentBreakpointManager.getAllSlaveBreakpoints();
    if (slaveBreakpoints.isEmpty()) {
      return;
    }

    Set<XBreakpointType<?, ?>> breakpointTypes = new THashSet<>();
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
    if (register) {
      boolean active = ReadAction.compute(() -> isBreakpointActive(b));
      if (active) {
        synchronized (myRegisteredBreakpoints) {
          myRegisteredBreakpoints.put(b, new CustomizedBreakpointPresentation());
        }
        handler.registerBreakpoint(b);
      }
    }
    else {
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

  public boolean isBreakpointActive(@NotNull XBreakpoint<?> b) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return !areBreakpointsMuted() && b.isEnabled() && !isInactiveSlaveBreakpoint(b) && !((XBreakpointBase)b).isDisposed();
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
    myDebugProcess.startStepOver(doResume());
  }

  @Override
  public void stepInto() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    myDebugProcess.startStepInto(doResume());
  }

  @Override
  public void stepOut() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    myDebugProcess.startStepOut(doResume());
  }

  @Override
  public <V extends XSmartStepIntoVariant> void smartStepInto(XSmartStepIntoHandler<V> handler, V variant) {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    final XSuspendContext context = doResume();
    handler.startStepInto(variant, context);
  }

  @Override
  public void forceStepInto() {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    myDebugProcess.startForceStepInto(doResume());
  }

  @Override
  public void runToPosition(@NotNull final XSourcePosition position, final boolean ignoreBreakpoints) {
    if (!myDebugProcess.checkCanPerformCommands()) return;

    if (ignoreBreakpoints) {
      disableBreakpoints();
    }
    myDebugProcess.runToPosition(position, doResume());
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

    myDebugProcess.resume(doResume());
  }

  @Nullable
  private XSuspendContext doResume() {
    if (!myPaused.getAndSet(false)) {
      return null;
    }

    myDispatcher.getMulticaster().beforeSessionResume();
    XSuspendContext context = mySuspendContext;
    mySuspendContext = null;
    myCurrentExecutionStack = null;
    myCurrentStackFrame = null;
    myTopFramePosition = null;
    myActiveNonLineBreakpoint = null;
    updateExecutionPosition();
    UIUtil.invokeLaterIfNeeded(() -> {
      if (mySessionTab != null) {
        mySessionTab.getUi().clearAttractionBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
      }
    });
    myDispatcher.getMulticaster().sessionResumed();
    return context;
  }

  @Override
  public void updateExecutionPosition() {
    // allowed only for the active session
    if (myDebuggerManager.getCurrentSession() == this) {
      boolean isTopFrame = isTopFrameSelected();
      myDebuggerManager.updateExecutionPoint(getCurrentPosition(), !isTopFrame, getPositionIconRenderer(isTopFrame));
    }
  }

  public boolean isTopFrameSelected() {
    return myCurrentExecutionStack != null && myIsTopFrame;
  }


  @Override
  public void showExecutionPoint() {
    if (mySuspendContext != null) {
      XExecutionStack executionStack = mySuspendContext.getActiveExecutionStack();
      if (executionStack != null) {
        XStackFrame topFrame = executionStack.getTopFrame();
        if (topFrame != null) {
          setCurrentStackFrame(executionStack, topFrame, true);
          myDebuggerManager.showExecutionPosition();
        }
      }
    }
  }

  @Override
  public void setCurrentStackFrame(@NotNull XExecutionStack executionStack, @NotNull XStackFrame frame, boolean isTopFrame) {
    if (mySuspendContext == null) return;

    boolean frameChanged = myCurrentStackFrame != frame;
    myCurrentExecutionStack = executionStack;
    myCurrentStackFrame = frame;
    myIsTopFrame = isTopFrame;
    activateSession();

    if (frameChanged) {
      myDispatcher.getMulticaster().stackFrameChanged();
    }
  }

  void activateSession() {
    myDebuggerManager.setCurrentSession(this);
    updateExecutionPosition();
  }

  public XBreakpoint<?> getActiveNonLineBreakpoint() {
    if (myActiveNonLineBreakpoint != null) {
      XSourcePosition breakpointPosition = myActiveNonLineBreakpoint.getSourcePosition();
      XSourcePosition position = getTopFramePosition();
      if (breakpointPosition == null ||
          (position != null && !(breakpointPosition.getFile().equals(position.getFile()) && breakpointPosition.getLine() == position.getLine()))) {
        return myActiveNonLineBreakpoint;
      }
    }
    return null;
  }

  @Nullable
  private GutterIconRenderer getPositionIconRenderer(boolean isTopFrame) {
    if (!isTopFrame) {
      return null;
    }
    XBreakpoint<?> activeNonLineBreakpoint = getActiveNonLineBreakpoint();
    if (activeNonLineBreakpoint != null) {
      return ((XBreakpointBase<?, ?, ?>)activeNonLineBreakpoint).createGutterIconRenderer();
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
      if (breakpoint.isLogMessage()) {
        XSourcePosition position = breakpoint.getSourcePosition();
        OpenFileHyperlinkInfo hyperlinkInfo =
          position != null ? new OpenFileHyperlinkInfo(myProject, position.getFile(), position.getLine()) : null;
        printMessage(XDebuggerBundle.message("xbreakpoint.reached.text") + " ", XBreakpointUtil.getShortText(breakpoint), hyperlinkInfo);
      }

      if (breakpoint.isLogStack()) {
        myDebugProcess.logStack(suspendContext, this);
      }

      if (evaluatedLogExpression != null) {
        printMessage(evaluatedLogExpression, null, null);
      }

      processDependencies(breakpoint);

      if (breakpoint.getSuspendPolicy() == SuspendPolicy.NONE) {
        return false;
      }
    }

    myActiveNonLineBreakpoint =
      (!(breakpoint instanceof XLineBreakpoint) || ((XLineBreakpoint)breakpoint).getType().canBeHitInOtherPlaces()) ? breakpoint : null;

    // set this session active on breakpoint, update execution position will be called inside positionReached
    myDebuggerManager.setCurrentSession(this);

    positionReachedInternal(suspendContext, true);

    if (doProcessing && breakpoint instanceof XLineBreakpoint<?> && ((XLineBreakpoint)breakpoint).isTemporary()) {
      handleTemporaryBreakpointHit(breakpoint);
    }
    return true;
  }

  private void handleTemporaryBreakpointHit(final XBreakpoint<?> breakpoint) {
    addSessionListener(new XDebugSessionListener() {
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
    AppUIUtil.invokeOnEdt(() -> {
      myConsoleView.print(message, ConsoleViewContentType.SYSTEM_OUTPUT);
      if (info != null) {
        myConsoleView.printHyperlink(hyperLinkText, info);
      }
      else if (hyperLinkText != null) {
        myConsoleView.print(hyperLinkText, ConsoleViewContentType.SYSTEM_OUTPUT);
      }
      myConsoleView.print("\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    });
  }

  public void unsetPaused() {
    myPaused.set(false);
  }

  private void positionReachedInternal(@NotNull final XSuspendContext suspendContext, boolean attract) {
    enableBreakpoints();
    mySuspendContext = suspendContext;
    myCurrentExecutionStack = suspendContext.getActiveExecutionStack();
    myCurrentStackFrame = myCurrentExecutionStack != null ? myCurrentExecutionStack.getTopFrame() : null;
    myIsTopFrame = true;
    myTopFramePosition = myCurrentStackFrame != null ? myCurrentStackFrame.getSourcePosition() : null;

    myPaused.set(true);

    updateExecutionPosition();

    final boolean showOnSuspend = myShowTabOnSuspend.compareAndSet(true, false);
    if (showOnSuspend || attract) {
      AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
        if (showOnSuspend) {
          initSessionTab(null);
          showSessionTab();
        }

        // user attractions should only be made if event happens independently (e.g. program paused/suspended)
        // and should not be made when user steps in the code
        if (attract) {
          if (mySessionTab == null) {
            LOG.debug("Cannot request focus because Session Tab is not initialized yet");
            return;
          }

          if (XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isShowDebuggerOnBreakpoint()) {
            mySessionTab.toFront(true, this::updateExecutionPosition);
          }

          if (myTopFramePosition == null) {
            // if there is no source position available, we should somehow tell the user that session is stopped.
            // the best way is to show the stack frames.
            XDebugSessionTab.showFramesView(this);
          }

          mySessionTab.getUi().attractBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
        }
      });
    }

    myDispatcher.getMulticaster().sessionPaused();
  }

  @Override
  public void positionReached(@NotNull final XSuspendContext suspendContext) {
    positionReached(suspendContext, false);
  }

  public void positionReached(@NotNull XSuspendContext suspendContext, boolean attract) {
    myActiveNonLineBreakpoint = null;
    positionReachedInternal(suspendContext, attract);
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
    return myStopped.get();
  }

  private void stopImpl() {
    if (!myStopped.compareAndSet(false, true)) {
      return;
    }

    try {
      if (breakpointsInitialized) {
        XBreakpointManagerImpl breakpointManager = myDebuggerManager.getBreakpointManager();
        if (myBreakpointListener != null) {
          breakpointManager.removeBreakpointListener(myBreakpointListener);
        }
        if (myDependentBreakpointListener != null) {
          breakpointManager.getDependentBreakpointManager().removeListener(myDependentBreakpointListener);
        }
      }
    }
    finally {
      //noinspection unchecked
      myDebugProcess.stopAsync().done(aVoid -> {
        if (!myProject.isDisposed()) {
          myProject.getMessageBus().syncPublisher(XDebuggerManager.TOPIC).processStopped(myDebugProcess);
        }

        if (mySessionTab != null) {
          AppUIUtil.invokeOnEdt(() -> {
            ((XWatchesViewImpl)mySessionTab.getWatchesView()).updateSessionData();
            mySessionTab.detachFromSession();
          });
        }
        else if (myConsoleView != null) {
          AppUIUtil.invokeOnEdt(() -> Disposer.dispose(myConsoleView));
        }

        myTopFramePosition = null;
        myCurrentExecutionStack = null;
        myCurrentStackFrame = null;
        mySuspendContext = null;

        updateExecutionPosition();

        if (myValueMarkers != null) {
          myValueMarkers.clear();
        }
        if (XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isUnmuteOnStop()) {
          mySessionData.setBreakpointsMuted(false);
        }
        myDebuggerManager.removeSession(this);
        myDispatcher.getMulticaster().sessionStopped();
        myDispatcher.getListeners().clear();

        myProject.putUserData(XDebuggerEditorLinePainter.CACHE, null);

        synchronized (myRegisteredBreakpoints) {
          myRegisteredBreakpoints.clear();
        }
      });
    }
  }

  public boolean isInactiveSlaveBreakpoint(final XBreakpoint<?> breakpoint) {
    return myInactiveSlaveBreakpoints.contains(breakpoint);
  }

  @Override
  public void stop() {
    ProcessHandler processHandler = myDebugProcess == null ? null : myDebugProcess.getProcessHandler();
    if (processHandler == null || processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) return;

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
    NotificationListener notificationListener = listener == null ? null : (notification, event) -> {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        listener.hyperlinkUpdate(event);
      }
    };
    XDebuggerManagerImpl.NOTIFICATION_GROUP.createNotification("", message, type.toNotificationType(), notificationListener).notify(myProject);
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
      if (getActiveNonLineBreakpoint() == breakpoint) {
        myActiveNonLineBreakpoint = null;
      }
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

  @NotNull
  private String getConfigurationName() {
    if (myEnvironment != null) {
      RunProfile profile = myEnvironment.getRunProfile();
      if (profile instanceof RunConfiguration) {
        return ((RunConfiguration)profile).getType().getId();
      }
    }
    return getSessionName();
  }

  public void setWatchExpressions(@NotNull List<XExpression> watchExpressions) {
    mySessionData.setWatchExpressions(watchExpressions);
    myDebuggerManager.getWatchesManager().setWatches(getConfigurationName(), watchExpressions);
  }

  List<XExpression> getWatchExpressions() {
    return myDebuggerManager.getWatchesManager().getWatches(getConfigurationName());
  }

  @Nullable
  public ExecutionEnvironment getExecutionEnvironment() {
    return myEnvironment;
  }
}
