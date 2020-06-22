// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ide.plugins.CannotUnloadPluginException;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.HintHint;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.pinned.items.XDebuggerPinToTopManager;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.ui.DebuggerColors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@State(name = "XDebuggerManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class XDebuggerManagerImpl extends XDebuggerManager implements PersistentStateComponent<XDebuggerState>, Disposable {
  public static final NotificationGroup NOTIFICATION_GROUP =
    NotificationGroup.toolWindowGroup("Debugger messages", ToolWindowId.DEBUG, false);

  private final Project myProject;
  private final XBreakpointManagerImpl myBreakpointManager;
  private final XDebuggerWatchesManager myWatchesManager;
  private final XDebuggerPinToTopManager myPinToTopManager;
  private final ExecutionPointHighlighter myExecutionPointHighlighter;
  private final Map<ProcessHandler, XDebugSessionImpl> mySessions = Collections.synchronizedMap(new LinkedHashMap<>());
  private final AtomicReference<XDebugSessionImpl> myActiveSession = new AtomicReference<>();

  private XDebuggerState myState = new XDebuggerState();

  public XDebuggerManagerImpl(@NotNull Project project) {
    myProject = project;

    MessageBusConnection messageBusConnection = project.getMessageBus().connect(this);

    myBreakpointManager = new XBreakpointManagerImpl(project, this);
    myWatchesManager = new XDebuggerWatchesManager();
    myPinToTopManager = new XDebuggerPinToTopManager();
    myExecutionPointHighlighter = new ExecutionPointHighlighter(project);

    messageBusConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
      @Override
      public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
        updateExecutionPoint(file, true);
      }

      @Override
      public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
        updateExecutionPoint(file, true);
      }
    });
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateExecutionPoint(file, false);
      }
    });
    messageBusConnection.subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<XBreakpoint<?>>() {
      @Override
      public void breakpointChanged(@NotNull XBreakpoint<?> breakpoint) {
        if (!(breakpoint instanceof XLineBreakpoint)) {
          final XDebugSessionImpl session = getCurrentSession();
          if (session != null && breakpoint.equals(session.getActiveNonLineBreakpoint())) {
            final XBreakpointBase breakpointBase = (XBreakpointBase)breakpoint;
            breakpointBase.clearIcon();
            myExecutionPointHighlighter.updateGutterIcon(breakpointBase.createGutterIconRenderer());
          }
        }
      }

      @Override
      public void breakpointRemoved(@NotNull XBreakpoint<?> breakpoint) {
        XDebugSessionImpl session = getCurrentSession();
        if (session != null && breakpoint == session.getActiveNonLineBreakpoint()) {
          myExecutionPointHighlighter.updateGutterIcon(null);
        }
      }
    });

    messageBusConnection.subscribe(RunContentManager.TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        if (descriptor != null && ToolWindowId.DEBUG.equals(executor.getToolWindowId())) {
          XDebugSessionImpl session = mySessions.get(descriptor.getProcessHandler());
          if (session != null) {
            session.activateSession();
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
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.addEditorMouseMotionListener(listener, this);
    eventMulticaster.addEditorMouseListener(listener, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void initializeComponent() {
    myBreakpointManager.init();
  }

  private void updateExecutionPoint(@NotNull VirtualFile file, boolean navigate) {
    if (file.equals(myExecutionPointHighlighter.getCurrentFile())) {
      myExecutionPointHighlighter.update(navigate);
    }
  }

  void updateExecutionPoint(GutterIconRenderer renderer) {
    myExecutionPointHighlighter.updateGutterIcon(renderer);
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

  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public XDebugSession startSession(@NotNull ExecutionEnvironment environment, @NotNull XDebugProcessStarter processStarter) throws ExecutionException {
    return startSession(environment.getContentToReuse(), processStarter, new XDebugSessionImpl(environment, this));
  }

  @Override
  @NotNull
  public XDebugSession startSessionAndShowTab(@NotNull String sessionName, @Nullable RunContentDescriptor contentToReuse,
                                              @NotNull XDebugProcessStarter starter) throws ExecutionException {
    return startSessionAndShowTab(sessionName, contentToReuse, false, starter);
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
    XDebugSessionImpl session = startSession(contentToReuse, starter,
      new XDebugSessionImpl(null, this, sessionName, icon, showToolWindowOnSuspendOnly, contentToReuse));

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
      session.activateSession();
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

  void updateExecutionPoint(@Nullable XSourcePosition position, boolean nonTopFrame, @Nullable GutterIconRenderer gutterIconRenderer) {
    if (position != null) {
      myExecutionPointHighlighter.show(position, nonTopFrame, gutterIconRenderer);
    }
    else {
      myExecutionPointHighlighter.hide();
    }
  }

  private void onActiveSessionChanged(@Nullable XDebugSession previousSession, @Nullable XDebugSession currentSession) {
    myBreakpointManager.getLineBreakpointManager().queueAllBreakpointsUpdate();
    ApplicationManager.getApplication().invokeLater(() -> {
      ValueLookupManager.getInstance(myProject).hideHint();
      DebuggerUIUtil.repaintCurrentEditor(myProject); // to update inline debugger data
    }, myProject.getDisposed());
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(TOPIC).currentSessionChanged(previousSession, currentSession);
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

  void setCurrentSession(@Nullable XDebugSessionImpl session) {
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
        myExecutionPointHighlighter.hide();
      }
      onActiveSessionChanged(previousSession, session);
    }
  }

  @Override
  public XDebuggerState getState() {
    XDebuggerState state = myState;
    myBreakpointManager.saveState(state.getBreakpointManagerState());
    myWatchesManager.saveState(state.getWatchesManagerState());
    myPinToTopManager.saveState(state.getPinToTopManagerState());
    return state;
  }

  public boolean isFullLineHighlighter() {
    return myExecutionPointHighlighter.isFullLineHighlighter();
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

  public void showExecutionPosition() {
    myExecutionPointHighlighter.navigateTo();
  }

  private static final TooltipGroup RUN_TO_CURSOR_TOOLTIP_GROUP = new TooltipGroup("RUN_TO_CURSOR_TOOLTIP_GROUP", 0);

  private class DebuggerEditorListener implements EditorMouseMotionListener, EditorMouseListener {
    RangeHighlighter myCurrentHighlighter;

    boolean isEnabled(@NotNull EditorMouseEvent e) {
      Editor editor = e.getEditor();
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

    private int getLineNumber(EditorMouseEvent event) {
      Editor editor = event.getEditor();
      if (event.getVisualPosition().line >= ((EditorImpl)editor).getVisibleLineCount()) {
        return -1;
      }
      int lineStartOffset = EditorUtil.getNotFoldedLineStartOffset(editor, event.getOffset());
      return editor.getDocument().getLineNumber(lineStartOffset);
    }

    @Override
    public void mousePressed(@NotNull EditorMouseEvent e) {
      if (e.getMouseEvent().getButton() == MouseEvent.BUTTON1 && isEnabled(e)) {
        int lineNumber = getLineNumber(e);
        XDebugSessionImpl session = getCurrentSession();
        if (session != null && lineNumber >= 0) {
          XSourcePositionImpl position = XSourcePositionImpl.create(((EditorEx)e.getEditor()).getVirtualFile(), lineNumber);
          if (position != null) {
            session.runToPosition(position, false);
            e.consume();
          }
        }
      }
    }
  }
}
