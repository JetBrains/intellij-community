// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.AppTopics;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.CannotUnloadPluginException;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.HintHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.pinned.items.XDebuggerPinToTopManager;
import com.intellij.xdebugger.impl.settings.ShowBreakpointsOverLineNumbersAction;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.ui.DebuggerColors;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@State(name = "XDebuggerManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class XDebuggerManagerImpl extends XDebuggerManager implements PersistentStateComponent<XDebuggerState>, Disposable {
  /**
   * @deprecated Use {@link #getNotificationGroup()}
   */
  @Deprecated(forRemoval = true)
  public static final NotificationGroup NOTIFICATION_GROUP = getNotificationGroup();

  private final Project myProject;
  private final XBreakpointManagerImpl myBreakpointManager;
  private final XDebuggerWatchesManager myWatchesManager;
  private final XDebuggerPinToTopManager myPinToTopManager;
  private final XDebuggerExecutionPointManager myExecutionPointManager;
  private final Map<ProcessHandler, XDebugSessionImpl> mySessions = Collections.synchronizedMap(new LinkedHashMap<>());
  private final AtomicReference<XDebugSessionImpl> myActiveSession = new AtomicReference<>();

  private XDebuggerState myState = new XDebuggerState();

  public XDebuggerManagerImpl(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    myProject = project;

    MessageBusConnection messageBusConnection = project.getMessageBus().connect(this);

    myBreakpointManager = new XBreakpointManagerImpl(project, this, messageBusConnection);
    myWatchesManager = new XDebuggerWatchesManager(project);
    myPinToTopManager = new XDebuggerPinToTopManager();
    myExecutionPointManager = new XDebuggerExecutionPointManager(project, coroutineScope);

    messageBusConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
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
        if (session != null && breakpoint == session.getActiveNonLineBreakpoint()) {
          session.updateExecutionPointGutterIconRenderer();
        }
      }
    });

    messageBusConnection.subscribe(RunContentManager.TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        if (descriptor != null && ToolWindowId.DEBUG.equals(executor.getToolWindowId())) {
          XDebugSessionImpl session = mySessions.get(descriptor.getProcessHandler());
          if (session != null) {
            session.activateSession(true);
          }
          else {
            setCurrentSession(null);
          }
        }
      }

      @Override
      public void contentRemoved(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        if (descriptor != null && ToolWindowId.DEBUG.equals(executor.getToolWindowId())) {
          mySessions.remove(descriptor.getProcessHandler());
        }
      }
    });

    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        XDebugSessionImpl session = getCurrentSession();
        if (session == null) return;

        XStackFrame currentFrame = session.getCurrentStackFrame();
        if (currentFrame == null) return;

        XSourcePosition alternativePosition = session.getFrameSourcePosition(currentFrame, XSourceKind.ALTERNATIVE);
        boolean isAlternativeSourceSelected = alternativePosition != null && alternativePosition.getFile().equals(event.getNewFile());
        boolean isAlternativeSourceDeselected = alternativePosition != null && alternativePosition.getFile().equals(event.getOldFile());

        session.setCurrentSourceKind(!isAlternativeSourceSelected || isAlternativeSourceDeselected
                                     ? XSourceKind.MAIN : XSourceKind.ALTERNATIVE);
      }
    });

    messageBusConnection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void checkUnloadPlugin(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        XDebugSession[] sessions = getDebugSessions();
        for (XDebugSession session : sessions) {
          XDebugProcess process = session.getDebugProcess();
          if (process.dependsOnPlugin(pluginDescriptor)) {
            throw new CannotUnloadPluginException("Plugin is not unload-safe because of the started debug session");
          }
        }
      }
    });

    DebuggerEditorListener listener = new DebuggerEditorListener();
    BreakpointPromoterEditorListener bpPromoter = new BreakpointPromoterEditorListener(coroutineScope);
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.addEditorMouseMotionListener(listener, this);
    eventMulticaster.addEditorMouseListener(listener, this);
    eventMulticaster.addEditorMouseMotionListener(bpPromoter, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void initializeComponent() {
    myBreakpointManager.init();
  }

  @Override
  @NotNull
  public XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  public XDebuggerWatchesManager getWatchesManager() {
    return myWatchesManager;
  }

  @NotNull
  public XDebuggerPinToTopManager getPinToTopManager() {
    return myPinToTopManager;
  }

  public @NotNull XDebuggerExecutionPointManager getExecutionPointManager() {
    return myExecutionPointManager;
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public XDebugSession startSession(@NotNull ExecutionEnvironment environment, @NotNull XDebugProcessStarter processStarter)
    throws ExecutionException {
    return startSession(environment.getContentToReuse(), processStarter, new XDebugSessionImpl(environment, this));
  }

  @Override
  @NotNull
  public XDebugSession startSessionAndShowTab(@NotNull String sessionName, @Nullable RunContentDescriptor contentToReuse,
                                              @NotNull XDebugProcessStarter starter) throws ExecutionException {
    return startSessionAndShowTab(sessionName, contentToReuse, false, starter);
  }

  @Override
  public @NotNull XDebugSession startSessionAndShowTab(@Nls @NotNull String sessionName,
                                                       @NotNull XDebugProcessStarter starter,
                                                       @NotNull ExecutionEnvironment environment) throws ExecutionException {
    return startSessionAndShowTab(sessionName, null, environment, environment.getContentToReuse(), false, starter);
  }

  @NotNull
  @Override
  public XDebugSession startSessionAndShowTab(@NotNull String sessionName, @Nullable RunContentDescriptor contentToReuse,
                                              boolean showToolWindowOnSuspendOnly,
                                              @NotNull XDebugProcessStarter starter) throws ExecutionException {
    return startSessionAndShowTab(sessionName, null, contentToReuse, showToolWindowOnSuspendOnly, starter);
  }

  @NotNull
  @Override
  public XDebugSession startSessionAndShowTab(@NotNull String sessionName,
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

    if (!showToolWindowOnSuspendOnly) {
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
      session.activateSession(false);
      ((XDebugProcessConfiguratorStarter)processStarter).configure(session.getSessionData());
    }

    session.init(process, contentToReuse);

    mySessions.put(session.getDebugProcess().getProcessHandler(), session);

    return session;
  }

  void removeSession(@NotNull final XDebugSessionImpl session) {
    XDebugSessionTab sessionTab = session.getSessionTab();
    mySessions.remove(session.getDebugProcess().getProcessHandler());
    if (sessionTab != null &&
        !myProject.isDisposed() &&
        !ApplicationManager.getApplication().isUnitTestMode() &&
        XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isHideDebuggerOnProcessTermination()) {
      RunContentManager.getInstance(myProject).hideRunContent(DefaultDebugExecutor.getDebugExecutorInstance(),
                                                              sessionTab.getRunContentDescriptor());
    }
    if (myActiveSession.compareAndSet(session, null)) {
      onActiveSessionChanged(session, null);
    }
  }

  private void onActiveSessionChanged(@Nullable XDebugSession previousSession, @Nullable XDebugSession currentSession) {
    myBreakpointManager.getLineBreakpointManager().queueAllBreakpointsUpdate();
    ApplicationManager.getApplication().invokeLater(() -> {
      ValueLookupManager.getInstance(myProject).hideHint();
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
    // ConcurrentHashMap.values().toArray(new T[0]) guaranteed to return array with no nulls
    return mySessions.values().toArray(new XDebugSessionImpl[0]);
  }

  @Override
  @Nullable
  public XDebugSession getDebugSession(@NotNull ExecutionConsole executionConsole) {
    synchronized (mySessions) {
      for (final XDebugSessionImpl debuggerSession : mySessions.values()) {
        XDebugSessionTab sessionTab = debuggerSession.getSessionTab();
        if (sessionTab != null) {
          RunContentDescriptor contentDescriptor = sessionTab.getRunContentDescriptor();
          if (contentDescriptor != null && executionConsole == contentDescriptor.getExecutionConsole()) {
            return debuggerSession;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public <T extends XDebugProcess> List<? extends T> getDebugProcesses(Class<T> processClass) {
    synchronized (mySessions) {
      return StreamEx.of(mySessions.values()).map(XDebugSessionImpl::getDebugProcess).select(processClass).toList();
    }
  }

  @Override
  @Nullable
  public XDebugSessionImpl getCurrentSession() {
    return myActiveSession.get();
  }

  boolean setCurrentSession(@Nullable XDebugSessionImpl session) {
    XDebugSessionImpl previousSession = myActiveSession.getAndSet(session);
    boolean sessionChanged = previousSession != session;
    if (sessionChanged) {
      if (session != null) {
        XDebugSessionTab tab = session.getSessionTab();
        if (tab != null) {
          tab.select();
        }
      }
      else {
        myExecutionPointManager.setExecutionPoint(null);
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

  private final class BreakpointPromoterEditorListener implements EditorMouseMotionListener, EditorMouseListener {
    private XSourcePositionImpl myLastPosition = null;
    private Icon myLastIcon = null;

    private final XDebuggerLineChangeHandler lineChangeHandler;

    BreakpointPromoterEditorListener(CoroutineScope coroutineScope) {
      lineChangeHandler = new XDebuggerLineChangeHandler(coroutineScope, (gutter, position, types) -> {
        myLastIcon = ObjectUtils.doIfNotNull(ContainerUtil.getFirstItem(types), XBreakpointType::getEnabledIcon);
        if (myLastIcon != null) {
          gutter.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          updateActiveLineNumberIcon(gutter, myLastIcon, position.getLine());
        }
        return Unit.INSTANCE;
      });
    }

    @Override
    public void mouseMoved(@NotNull EditorMouseEvent e) {
      if (!ExperimentalUI.isNewUI() || !ShowBreakpointsOverLineNumbersAction.isSelected()) return;
      Editor editor = e.getEditor();
      if (editor.getProject() != myProject || editor.getEditorKind() != EditorKind.MAIN_EDITOR) return;
      EditorGutter editorGutter = editor.getGutter();
      if (editorGutter instanceof EditorGutterComponentEx gutter) {
        if (e.getArea() == EditorMouseEventArea.LINE_NUMBERS_AREA) {
          int line = EditorUtil.yToLogicalLineNoCustomRenderers(editor, e.getMouseEvent().getY());
          Document document = editor.getDocument();
          if (DocumentUtil.isValidLine(line, document)) {
            XSourcePositionImpl position = XSourcePositionImpl.create(FileDocumentManager.getInstance().getFile(document), line);
            if (position != null) {
              if (myLastPosition == null || !myLastPosition.getFile().equals(position.getFile()) || myLastPosition.getLine() != line) {
                // drop an icon first and schedule the available types calculation
                clear(gutter);
                myLastPosition = position;
                lineChangeHandler.lineChanged(editor, position);
              }
              else if (myLastIcon != null) {
                // we need to set the cursor on every event, otherwise it is reset inside the editor
                gutter.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              }
              return;
            }
          }
        }
        if (myLastIcon != null) {
          clear(gutter);
          myLastPosition = null;
          lineChangeHandler.exitedGutter();
        }
      }
    }

    private void clear(EditorGutterComponentEx gutter) {
      updateActiveLineNumberIcon(gutter, null, null);
      myLastIcon = null;
    }

    private static void updateActiveLineNumberIcon(@NotNull EditorGutterComponentEx gutter, @Nullable Icon icon, @Nullable Integer line) {
      if (gutter.getClientProperty("editor.gutter.context.menu") != null) return;
      boolean requireRepaint = false;
      if (gutter.getClientProperty("line.number.hover.icon") != icon) {
        gutter.putClientProperty("line.number.hover.icon", icon);
        gutter.putClientProperty("line.number.hover.icon.context.menu", icon == null ? null
                                                                                     : ActionManager.getInstance().getAction("XDebugger.Hover.Breakpoint.Context.Menu"));
        requireRepaint = true;
      }
      if (!Objects.equals(gutter.getClientProperty("active.line.number"), line)) {
        gutter.putClientProperty("active.line.number", line);
        requireRepaint = true;
      }
      if (requireRepaint) {
        gutter.repaint();
      }
    }
  }

  private final class DebuggerEditorListener implements EditorMouseMotionListener, EditorMouseListener {
    RangeHighlighter myCurrentHighlighter;

    boolean isEnabled(@NotNull EditorMouseEvent e) {
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

      HintHint hint = new HintHint(e.getMouseEvent()).setAwtTooltip(true).setPreferredPosition(Balloon.Position.above);
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

    private static int getLineNumber(EditorMouseEvent event) {
      Editor editor = event.getEditor();
      if (event.getVisualPosition().line >= ((EditorImpl)editor).getVisibleLineCount()) {
        return -1;
      }
      int lineStartOffset = EditorUtil.getNotFoldedLineStartOffset(editor, event.getOffset());
      int documentLine = editor.getDocument().getLineNumber(lineStartOffset);
      return documentLine < editor.getDocument().getLineCount() ? documentLine : -1;
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
}
