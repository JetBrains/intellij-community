// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.DynamicPluginVetoer;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.HintHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.DocumentUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.evaluate.ValueLookupManagerController;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.pinned.items.XDebuggerPinToTopManager;
import com.intellij.xdebugger.impl.settings.ShowBreakpointsOverLineNumbersAction;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.intellij.xdebugger.impl.CoroutineUtilsKt.createMutableStateFlow;

@ApiStatus.Internal
@State(name = "XDebuggerManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class XDebuggerManagerImpl extends XDebuggerManager implements PersistentStateComponent<XDebuggerState>, Disposable {
  public static final DataKey<Integer> ACTIVE_LINE_NUMBER = DataKey.create("active.line.number");
  private static final ExecutorService EXECUTION_POINT_ICON_EXECUTOR =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Execution point icon updater", 1);

  private final Project myProject;
  private final CoroutineScope myCoroutineScope;
  private final XBreakpointManagerImpl myBreakpointManager;
  private final XDebuggerWatchesManager myWatchesManager;
  private final XDebuggerPinToTopManager myPinToTopManager;
  private final XDebuggerExecutionPointManager myExecutionPointManager;
  private final Map<ProcessHandler, XDebugSessionImpl> mySessions = Collections.synchronizedMap(new LinkedHashMap<>());
  private final MutableStateFlow<@Nullable XDebugSessionImpl> myActiveSession = createMutableStateFlow(null);

  private XDebuggerState myState = new XDebuggerState();

  private InlayRunToCursorEditorListener myNewRunToCursorListener = null;

  XDebuggerManagerImpl(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    myProject = project;
    myCoroutineScope = coroutineScope;

    SimpleMessageBusConnection messageBusConnection = project.getMessageBus().connect(coroutineScope);

    myBreakpointManager = new XBreakpointManagerImpl(project, this, messageBusConnection, coroutineScope);
    myWatchesManager = new XDebuggerWatchesManager(project, coroutineScope);
    myPinToTopManager = new XDebuggerPinToTopManager(coroutineScope);
    myExecutionPointManager = new XDebuggerExecutionPointManager(project, coroutineScope);

    messageBusConnection.subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
        myExecutionPointManager.updateExecutionPosition(file, true);
      }

      @Override
      public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
        myExecutionPointManager.updateExecutionPosition(file, true);
      }
    });
    messageBusConnection.subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
      @Override
      public void breakpointChanged(@NotNull XBreakpoint<?> breakpoint) {
        updateActiveNonLineBreakpointGutterIconRenderer(breakpoint);
      }

      @Override
      public void breakpointRemoved(@NotNull XBreakpoint<?> breakpoint) {
        updateActiveNonLineBreakpointGutterIconRenderer(breakpoint);
      }

      private void updateActiveNonLineBreakpointGutterIconRenderer(@NotNull XBreakpoint<?> breakpoint) {
        XDebugSessionImpl session = getCurrentSession();
        if (session == null) return;
        ReadAction
          .nonBlocking(() -> session.getActiveNonLineBreakpoint())
          .coalesceBy(myProject, breakpoint)
          .expireWith(myProject)
          .finishOnUiThread(ModalityState.defaultModalityState(), activeNonLineBreakpoint -> {
            // also verify that the session has not changed
            if (getCurrentSession() == session && breakpoint == activeNonLineBreakpoint) {
              session.updateExecutionPointGutterIconRenderer();
            }
          })
          .submit(EXECUTION_POINT_ICON_EXECUTOR);
      }
    });

    GutterUiRunToCursorEditorListener listener = new GutterUiRunToCursorEditorListener();
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.addEditorMouseMotionListener(listener, this);
    eventMulticaster.addEditorMouseListener(listener, this);
    if (ExperimentalUI.isNewUI()) {
      myNewRunToCursorListener = new InlayRunToCursorEditorListener(myProject, coroutineScope);
      eventMulticaster.addEditorMouseMotionListener(myNewRunToCursorListener, this);
      eventMulticaster.addEditorMouseListener(myNewRunToCursorListener, this);
    }
  }

  @ApiStatus.Internal
  public void onSessionSelected(@Nullable XDebugSessionImpl session) {
    if (session != null) {
      session.activateSession(true);
    }
    else {
      setCurrentSession(null);
    }
  }

  void reshowInlayToolbar(@NotNull Editor editor) {
    if (myNewRunToCursorListener == null) {
      return;
    }
    XDebugSessionImpl session = getCurrentSession();
    if (session == null) {
      return;
    }
    myNewRunToCursorListener.reshowInlayRunToCursor(editor);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void initializeComponent() {
    myBreakpointManager.init();
  }

  @Override
  public @NotNull XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  public XDebuggerWatchesManager getWatchesManager() {
    return myWatchesManager;
  }

  public @NotNull XDebuggerPinToTopManager getPinToTopManager() {
    return myPinToTopManager;
  }

  @ApiStatus.Internal
  public @NotNull XDebuggerExecutionPointManager getExecutionPointManager() {
    return myExecutionPointManager;
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull XDebugSession startSession(@NotNull ExecutionEnvironment environment, @NotNull XDebugProcessStarter processStarter)
    throws ExecutionException {
    return startSession(environment.getContentToReuse(), processStarter, new XDebugSessionImpl(environment, this));
  }

  @Override
  public @NotNull XDebugSession startSessionAndShowTab(@NotNull String sessionName, @Nullable RunContentDescriptor contentToReuse,
                                                       @NotNull XDebugProcessStarter starter) throws ExecutionException {
    return startSessionAndShowTab(sessionName, contentToReuse, false, starter);
  }

  @Override
  public @NotNull XDebugSession startSessionAndShowTab(@Nls @NotNull String sessionName,
                                                       @NotNull XDebugProcessStarter starter,
                                                       @NotNull ExecutionEnvironment environment) throws ExecutionException {
    return startSessionAndShowTab(sessionName, null, environment, environment.getContentToReuse(), false, starter);
  }

  @Override
  public @NotNull XDebugSession startSessionAndShowTab(@NotNull String sessionName, @Nullable RunContentDescriptor contentToReuse,
                                                       boolean showToolWindowOnSuspendOnly,
                                                       @NotNull XDebugProcessStarter starter) throws ExecutionException {
    return startSessionAndShowTab(sessionName, null, contentToReuse, showToolWindowOnSuspendOnly, starter);
  }

  @Override
  public @NotNull XDebugSession startSessionAndShowTab(@NotNull String sessionName,
                                                       Icon icon,
                                                       @Nullable RunContentDescriptor contentToReuse,
                                                       boolean showToolWindowOnSuspendOnly,
                                                       @NotNull XDebugProcessStarter starter) throws ExecutionException {
    return startSessionAndShowTab(sessionName, icon, null, contentToReuse, showToolWindowOnSuspendOnly, starter);
  }

  private XDebugSession startSessionAndShowTab(@Nls @NotNull String sessionName,
                                              Icon icon,
                                              @Nullable ExecutionEnvironment environment,
                                              @Nullable RunContentDescriptor contentToReuse,
                                              boolean showToolWindowOnSuspendOnly,
                                              @NotNull XDebugProcessStarter starter) throws ExecutionException {
    XDebugSessionImpl session = startSession(contentToReuse, starter,
      new XDebugSessionImpl(environment, this, sessionName, icon, showToolWindowOnSuspendOnly, contentToReuse));

    if (!showToolWindowOnSuspendOnly && !XDebugSessionProxy.useFeProxy()) {
      session.showSessionTab();
    }
    ProcessHandler handler = session.getDebugProcess().getProcessHandler();
    handler.startNotify();
    return session;
  }

  private XDebugSessionImpl startSession(@Nullable RunContentDescriptor contentToReuse,
                                         @NotNull XDebugProcessStarter processStarter,
                                         @NotNull XDebugSessionImpl session) throws ExecutionException {
    XDebugProcess process = processStarter.start(session);
    myProject.getMessageBus().syncPublisher(TOPIC).processStarted(process);

    // Perform custom configuration of session data for XDebugProcessConfiguratorStarter classes
    if (processStarter instanceof XDebugProcessConfiguratorStarter) {
      ((XDebugProcessConfiguratorStarter)processStarter).configure(session.getSessionData());
    }

    session.init(process, contentToReuse);

    // TODO: may be this session activation is not needed?
    if (processStarter instanceof XDebugProcessConfiguratorStarter) {
      session.activateSession(false);
    }

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      session.addSessionListener(new XDebugSessionListener() {
        @Override
        public void sessionPaused() {
          ApplicationManager.getApplication().invokeLater(() -> {
            Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
            if (editor == null) {
              return;
            }
            reshowInlayToolbar(editor);
          });
        }
      });
    }

    mySessions.put(session.getDebugProcess().getProcessHandler(), session);

    return session;
  }

  void removeSession(final @NotNull XDebugSessionImpl session) {
    removeSessionNoNotify(session);
    if (myActiveSession.compareAndSet(session, null)) {
      onActiveSessionChanged(session, null);
    }
  }

  @ApiStatus.Internal
  public void removeSessionNoNotify(@NotNull XDebugSessionImpl session) {
    mySessions.remove(session.getDebugProcess().getProcessHandler());
  }

  private void onActiveSessionChanged(@Nullable XDebugSession previousSession, @Nullable XDebugSession currentSession) {
    myBreakpointManager.getLineBreakpointManager().queueAllBreakpointsUpdate();
    ApplicationManager.getApplication().invokeLater(() -> {
      ValueLookupManagerController.getInstance(myProject).hideHint();
    }, myProject.getDisposed());
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(TOPIC).currentSessionChanged(previousSession, currentSession);
      if (currentSession != null && previousSession != null) {
        XDebuggerActionsCollector.sessionChanged.log();
      }
    }
  }

  @Override
  public XDebugSession @NotNull [] getDebugSessions() {
    // ConcurrentHashMap.values().toArray(new T[0]) guaranteed to return an array with no nulls
    return mySessions.values().toArray(new XDebugSessionImpl[0]);
  }

  @Override
  public @Nullable XDebugSession getDebugSession(@NotNull ExecutionConsole executionConsole) {
    synchronized (mySessions) {
      return ContainerUtil.find(mySessions.values(), session -> session.getConsoleView() == executionConsole);
    }
  }

  @Override
  public @NotNull <T extends XDebugProcess> List<? extends T> getDebugProcesses(Class<T> processClass) {
    synchronized (mySessions) {
      return StreamEx.of(mySessions.values()).map(XDebugSessionImpl::getDebugProcess).select(processClass).toList();
    }
  }

  @Override
  public @Nullable XDebugSessionImpl getCurrentSession() {
    return myActiveSession.getValue();
  }

  @ApiStatus.Internal
  public StateFlow<@Nullable XDebugSessionImpl> getCurrentSessionFlow() {
    return myActiveSession;
  }

  boolean setCurrentSession(@Nullable XDebugSessionImpl session) {
    XDebugSessionImpl previousSession = StateFlowKt.getAndUpdate(myActiveSession, (currentValue) -> {
      return session;
    });
    boolean sessionChanged = previousSession != session;
    if (sessionChanged) {
      if (session != null) {
        myExecutionPointManager.setAlternativeSourceKindFlow(session.getAlternativeSourceKindState());
      }
      else {
        myExecutionPointManager.clearExecutionPoint();
      }
      onActiveSessionChanged(previousSession, session);
    }
    return sessionChanged;
  }

  @Override
  public XDebuggerState getState() {
    XDebuggerState state = myState;
    myBreakpointManager.saveState(state.getBreakpointManagerState());
    myWatchesManager.saveState(state.getWatchesManagerState());
    myPinToTopManager.saveState(state.getPinToTopManagerState());
    return state;
  }

  @Override
  public void loadState(@NotNull XDebuggerState state) {
    myState = state;
    myBreakpointManager.loadState(state.getBreakpointManagerState());
    myWatchesManager.loadState(state.getWatchesManagerState());
    myPinToTopManager.loadState(state.getPinToTopManagerState());
  }

  @Override
  public void noStateLoaded() {
    myBreakpointManager.noStateLoaded();
  }

  private static final TooltipGroup RUN_TO_CURSOR_TOOLTIP_GROUP = new TooltipGroup("RUN_TO_CURSOR_TOOLTIP_GROUP", 0);

  public static @NotNull NotificationGroup getNotificationGroup() {
    return NotificationGroupManager.getInstance().getNotificationGroup("Debugger messages");
  }

  private final class GutterUiRunToCursorEditorListener implements EditorMouseMotionListener, EditorMouseListener {
    RangeHighlighter myCurrentHighlighter;

    boolean isEnabled(@NotNull EditorMouseEvent e) {
      if (InlayRunToCursorEditorListener.isInlayRunToCursorEnabled() && ExperimentalUI.isNewUI()) return false;

      Editor editor = e.getEditor();
      if (ExperimentalUI.isNewUI() && ShowBreakpointsOverLineNumbersAction.isSelected()) {
        //todo[kb] make it possible to do run to cursor by clicking on the gutter
        return false;
      }
      if (e.getArea() != EditorMouseEventArea.LINE_NUMBERS_AREA ||
          editor.getProject() != myProject ||
          !EditorUtil.isRealFileEditor(editor) ||
          !XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isRunToCursorGestureEnabled()) {
        return false;
      }
      XDebugSessionImpl session = getCurrentSession();
      return session != null && session.isPaused() && !session.isReadOnly();
    }

    @Override
    public void mouseMoved(@NotNull EditorMouseEvent e) {
      if (!isEnabled(e)) {
        removeHighlighter(e);
        return;
      }
      removeHighlighter(e);

      int lineNumber = getLineNumber(e);
      if (lineNumber < 0) {
        return;
      }

      Editor editor = e.getEditor();
      myCurrentHighlighter = editor.getMarkupModel().addLineHighlighter(DebuggerColors.NOT_TOP_FRAME_ATTRIBUTES,
                                                                        lineNumber,
                                                                        DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER);

      HintHint hint =
        new HintHint(e.getMouseEvent()).setAwtTooltip(true).setPreferredPosition(Balloon.Position.above).setStatus(HintHint.Status.Info);
      String text = UIUtil.removeMnemonic(ActionsBundle.actionText(XDebuggerActions.RUN_TO_CURSOR));
      TooltipController.getInstance()
        .showTooltipByMouseMove(editor, new RelativePoint(e.getMouseEvent()), new LineTooltipRenderer(text, new Object[]{text}), false,
                                RUN_TO_CURSOR_TOOLTIP_GROUP, hint);

      IdeGlassPaneUtil.find(e.getMouseEvent().getComponent()).setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), this);
    }

    @Override
    public void mouseExited(@NotNull EditorMouseEvent e) {
      removeHighlighter(e);
    }

    private void removeHighlighter(@NotNull EditorMouseEvent e) {
      if (myCurrentHighlighter != null) {
        myCurrentHighlighter.dispose();
        TooltipController.getInstance().cancelTooltip(RUN_TO_CURSOR_TOOLTIP_GROUP, e.getMouseEvent(), true);
        IdeGlassPaneUtil.find(e.getMouseEvent().getComponent()).setCursor(null, this);
        myCurrentHighlighter = null;
      }
    }

    @Override
    public void mousePressed(@NotNull EditorMouseEvent e) {
      if (e.getMouseEvent().getButton() == MouseEvent.BUTTON1 && isEnabled(e)) {
        int lineNumber = getLineNumber(e);
        XDebugSessionImpl session = getCurrentSession();
        if (session != null && lineNumber >= 0) {
          XSourcePositionImpl position = XSourcePositionImpl.create(e.getEditor().getVirtualFile(), lineNumber);
          if (position != null) {
            e.consume();
            AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_RUN_TO_CURSOR);
            if (action == null) throw new AssertionError("'" + IdeActions.ACTION_RUN_TO_CURSOR + "' action not found");
            DataContext dataContext = DataManager.getInstance().getDataContext(e.getMouseEvent().getComponent());
            AnActionEvent event = AnActionEvent.createFromAnAction(action, e.getMouseEvent(), ActionPlaces.EDITOR_GUTTER, dataContext);
            ActionUtil.performDumbAwareWithCallbacks(action, event, () -> session.runToPosition(position, false));
          }
        }
      }
    }
  }

  static int getLineNumber(@NotNull EditorMouseEvent event) {
    Editor editor = event.getEditor();
    if (event.getVisualPosition().line >= ((EditorImpl)editor).getVisibleLineCount()) {
      return -1;
    }
    int lineStartOffset = EditorUtil.getNotFoldedLineStartOffset(editor, event.getOffset());
    int documentLine = editor.getDocument().getLineNumber(lineStartOffset);
    return documentLine < editor.getDocument().getLineCount() ? documentLine : -1;
  }

  static class XDebuggerPluginVetoer implements DynamicPluginVetoer {
    @Override
    public @Nls @Nullable String vetoPluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor) {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        XDebuggerManager manager = project.getServiceIfCreated(XDebuggerManager.class);
        if (manager == null) continue;

        XDebugSession[] sessions = manager.getDebugSessions();
        for (XDebugSession session : sessions) {
          XDebugProcess process = session.getDebugProcess();
          if (process.dependsOnPlugin(pluginDescriptor)) {
            return XDebuggerBundle.message("plugin.is.not.unload.safe.because.of.the.started.debug.session");
          }
        }
      }
      return null;
    }
  }

  @ApiStatus.Internal
  public CoroutineScope getCoroutineScope() {
    return myCoroutineScope;
  }
}
