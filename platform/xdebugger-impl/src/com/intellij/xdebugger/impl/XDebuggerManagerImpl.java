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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointAdapter;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
@State(
    name = XDebuggerManagerImpl.COMPONENT_NAME,
    storages = {@Storage(
        id = "other",
        file = "$WORKSPACE_FILE$")})
public class XDebuggerManagerImpl extends XDebuggerManager implements ProjectComponent, PersistentStateComponent<XDebuggerManagerImpl.XDebuggerState> {
  @NonNls public static final String COMPONENT_NAME = "XDebuggerManager";
  private final Project myProject;
  private final XBreakpointManagerImpl myBreakpointManager;
  private final Map<RunContentDescriptor, XDebugSessionData> mySessionData;
  private final Map<ProcessHandler, XDebugSessionImpl> mySessions;
  private final ExecutionPointHighlighter myExecutionPointHighlighter;
  private XDebugSessionImpl myActiveSession;

  public XDebuggerManagerImpl(final Project project, final StartupManager startupManager, MessageBus messageBus) {
    myProject = project;
    myBreakpointManager = new XBreakpointManagerImpl(project, this, startupManager);
    mySessionData = new HashMap<RunContentDescriptor, XDebugSessionData>();
    mySessions = new LinkedHashMap<ProcessHandler, XDebugSessionImpl>();
    myExecutionPointHighlighter = new ExecutionPointHighlighter(project);
    messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(final FileEditorManager source, final VirtualFile file) {
        if (file instanceof HttpVirtualFile && file.equals(myExecutionPointHighlighter.getCurrentFile())) {
          myExecutionPointHighlighter.update();
        }
      }
    });
    myBreakpointManager.addBreakpointListener(new XBreakpointAdapter<XBreakpoint<?>>() {
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
    });

    messageBus.connect().subscribe(RunContentManagerImpl.RUN_CONTENT_TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(RunContentDescriptor descriptor, @NotNull Executor executor) {
        if (executor.equals(DefaultDebugExecutor.getDebugExecutorInstance())) {
          final XDebugSessionImpl session = mySessions.get(descriptor.getProcessHandler());
          if (session != null) {
            session.activateSession();
          }
          else {
            setActiveSession(null, null, false, null);
          }
        }
      }

      @Override
      public void contentRemoved(RunContentDescriptor descriptor, @NotNull Executor executor) {
        if (executor.equals(DefaultDebugExecutor.getDebugExecutorInstance())) {
          mySessionData.remove(descriptor);
          XDebugSessionImpl session = mySessions.remove(descriptor.getProcessHandler());
          if (session != null) {
            Disposer.dispose(session.getSessionTab());
          }
        }
      }
    });
  }

  @NotNull
  public XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  public XDebugSession startSession(@NotNull final ProgramRunner runner,
                                    @NotNull final ExecutionEnvironment env,
                                    @Nullable final RunContentDescriptor contentToReuse,
                                    @NotNull final XDebugProcessStarter processStarter) throws ExecutionException {
    return startSession(contentToReuse, processStarter, new XDebugSessionImpl(env, runner, this));
  }

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
    XDebugSessionImpl session = startSession(contentToReuse, starter, new XDebugSessionImpl(null, null, this, sessionName,
                                                                                        showToolWindowOnSuspendOnly));
    if (!showToolWindowOnSuspendOnly) {
      session.showSessionTab();
    }
    ProcessHandler handler = session.getDebugProcess().getProcessHandler();
    handler.startNotify();
    return session;
  }

  private XDebugSessionImpl startSession(final RunContentDescriptor contentToReuse, final XDebugProcessStarter processStarter,
                                     final XDebugSessionImpl session) throws ExecutionException {
    XDebugProcess process = processStarter.start(session);

    XDebugSessionData oldSessionData = contentToReuse != null ? mySessionData.get(contentToReuse) : null;
    if (oldSessionData == null) {
      oldSessionData = new XDebugSessionData();
    }
    session.init(process, oldSessionData);
    mySessions.put(session.getDebugProcess().getProcessHandler(), session);

    return session;
  }

  public void removeSession(@NotNull XDebugSessionImpl session) {
    XDebugSessionTab sessionTab = session.getSessionTab();
    mySessions.remove(session.getDebugProcess().getProcessHandler());
    if (sessionTab != null) {
      mySessionData.put(sessionTab.getRunContentDescriptor(), session.getSessionData());
    }
    if (myActiveSession == session) {
      myActiveSession = null;
      onActiveSessionChanged();
    }
  }

  public void setActiveSession(@Nullable XDebugSessionImpl session, @Nullable XSourcePosition position, boolean useSelection,
                               final @Nullable GutterIconRenderer gutterIconRenderer) {
    boolean sessionChanged = myActiveSession != session;
    myActiveSession = session;
    updateExecutionPoint(position, useSelection, gutterIconRenderer);
    if (sessionChanged) {
      onActiveSessionChanged();
    }
  }

  public void updateExecutionPoint(XSourcePosition position, boolean useSelection, @Nullable GutterIconRenderer gutterIconRenderer) {
    if (position != null) {
      myExecutionPointHighlighter.show(position, useSelection, gutterIconRenderer);
    }
    else {
      myExecutionPointHighlighter.hide();
    }
  }

  private void onActiveSessionChanged() {
    myBreakpointManager.getLineBreakpointManager().queueAllBreakpointsUpdate();
  }

  @NotNull
  public XDebugSession[] getDebugSessions() {
    final Collection<XDebugSessionImpl> sessions = mySessions.values();
    return sessions.toArray(new XDebugSessionImpl[sessions.size()]);
  }

  @Override
  @Nullable
  public XDebugSession getDebugSession(@NotNull ExecutionConsole executionConsole) {
    for (final XDebugSessionImpl debuggerSession : mySessions.values()) {
      XDebugSessionTab sessionTab = debuggerSession.getSessionTab();
      if (sessionTab != null) {
        RunContentDescriptor contentDescriptor = sessionTab.getRunContentDescriptor();
        if (contentDescriptor != null && executionConsole == contentDescriptor.getExecutionConsole()) {
          return debuggerSession;
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public <T extends XDebugProcess> Collection<? extends T> getDebugProcesses(Class<T> processClass) {
    final List<T> list = new ArrayList<T>();
    for (XDebugSessionImpl session : mySessions.values()) {
      final XDebugProcess process = session.getDebugProcess();
      if (processClass.isInstance(process)) {
        list.add(processClass.cast(process));
      }
    }
    return list;
  }

  @Nullable
  public XDebugSessionImpl getCurrentSession() {
    return myActiveSession;
  }

  public XDebuggerState getState() {
    return new XDebuggerState(myBreakpointManager.getState());
  }

  public void loadState(final XDebuggerState state) {
    myBreakpointManager.loadState(state.myBreakpointManagerState);
  }

  public void showExecutionPosition() {
    myExecutionPointHighlighter.navigateTo();
  }

  public static class XDebuggerState {
    private XBreakpointManagerImpl.BreakpointManagerState myBreakpointManagerState;

    public XDebuggerState() {
    }

    public XDebuggerState(final XBreakpointManagerImpl.BreakpointManagerState breakpointManagerState) {
      myBreakpointManagerState = breakpointManagerState;
    }

    @Property(surroundWithTag = false)
    public XBreakpointManagerImpl.BreakpointManagerState getBreakpointManagerState() {
      return myBreakpointManagerState;
    }

    public void setBreakpointManagerState(final XBreakpointManagerImpl.BreakpointManagerState breakpointManagerState) {
      myBreakpointManagerState = breakpointManagerState;
    }
  }

}
