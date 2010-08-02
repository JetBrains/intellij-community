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
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentListener;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.xdebugger.*;
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
public class XDebuggerManagerImpl extends XDebuggerManager
    implements ProjectComponent, PersistentStateComponent<XDebuggerManagerImpl.XDebuggerState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.XDebuggerManagerImpl");
  @NonNls public static final String COMPONENT_NAME = "XDebuggerManager";
  private final Project myProject;
  private final XBreakpointManagerImpl myBreakpointManager;
  private final Map<ProcessHandler, XDebugSessionData> mySessionData;
  private final Map<ProcessHandler, XDebugSessionTab> mySessionTabs;
  private final List<XDebugSessionImpl> mySessions;
  private final ExecutionPointHighlighter myExecutionPointHighlighter;
  private XDebugSessionImpl myLastActiveSession;


  public XDebuggerManagerImpl(final Project project, final StartupManager startupManager, MessageBus messageBus) {
    myProject = project;
    myBreakpointManager = new XBreakpointManagerImpl(project, this, startupManager);
    mySessionData = new LinkedHashMap<ProcessHandler, XDebugSessionData>();
    mySessions = new ArrayList<XDebugSessionImpl>();
    myExecutionPointHighlighter = new ExecutionPointHighlighter(project);
    mySessionTabs = new HashMap<ProcessHandler, XDebugSessionTab>();
    messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(final FileEditorManager source, final VirtualFile file) {
        if (file instanceof HttpVirtualFile && file.equals(myExecutionPointHighlighter.getCurrentFile())) {
          myExecutionPointHighlighter.update();
        }
      }
    });
  }

  @NotNull
  public XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  public void projectOpened() {
    final RunContentManager contentManager = ExecutionManager.getInstance(myProject).getContentManager();
    LOG.assertTrue(contentManager != null, "Content manager is null");
    final RunContentListener myContentListener = new RunContentListener() {
      public void contentSelected(RunContentDescriptor descriptor) {
      }

      public void contentRemoved(RunContentDescriptor descriptor) {
        XDebugSessionTab sessionTab = mySessionTabs.remove(descriptor.getProcessHandler());
        if (sessionTab != null) {
          Disposer.dispose(sessionTab);
        }
      }
    };


    contentManager.addRunContentListener(myContentListener, DefaultDebugExecutor.getDebugExecutorInstance());
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        contentManager.removeRunContentListener(myContentListener);
      }
    });
  }

  public void projectClosed() {
  }

  public Project getProject() {
    return myProject;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

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
    XDebugSession session = startSession(contentToReuse, starter, new XDebugSessionImpl(null, null, this, sessionName));
    RunContentDescriptor descriptor = session.getRunContentDescriptor();
    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), descriptor);
    ProcessHandler handler = descriptor.getProcessHandler();
    if (handler != null) {
      handler.startNotify();
    }
    return session;
  }

  private XDebugSession startSession(final RunContentDescriptor contentToReuse, final XDebugProcessStarter processStarter,
                                     final XDebugSessionImpl session) throws ExecutionException {
    XDebugProcess process = processStarter.start(session);

    XDebugSessionData oldSessionData = contentToReuse != null ? mySessionData.remove(contentToReuse.getProcessHandler()) : null;
    if (oldSessionData == null) {
      oldSessionData = new XDebugSessionData();
    }
    final XDebugSessionTab sessionTab = session.init(process, oldSessionData);
    mySessions.add(session);
    mySessionTabs.put(session.getDebugProcess().getProcessHandler(), sessionTab);

    return session;
  }

  public void removeSession(@NotNull XDebugSessionImpl session) {
    XDebugSessionTab sessionTab = session.getSessionTab();
    XDebugSessionData data = sessionTab.saveData();
    mySessions.remove(session);
    mySessionData.put(session.getDebugProcess().getProcessHandler(), data);
    if (myLastActiveSession == session) {
      myLastActiveSession = null;
      onActiveSessionChanged();
    }
  }

  public void updateExecutionPosition(@NotNull XDebugSessionImpl session, @Nullable XSourcePosition position, boolean useSelection) {
    boolean sessionChanged = myLastActiveSession != session;
    myLastActiveSession = session;
    if (position != null) {
      myExecutionPointHighlighter.show(position, useSelection);
    }
    else {
      myExecutionPointHighlighter.hide();
    }
    if (sessionChanged) {
      onActiveSessionChanged();
    }
  }

  private void onActiveSessionChanged() {
    myBreakpointManager.getLineBreakpointManager().queueAllBreakpointsUpdate();
  }

  @NotNull
  public XDebugSession[] getDebugSessions() {
    return mySessions.toArray(new XDebugSession[mySessions.size()]);
  }

  @Override
  @Nullable
  public XDebugSession getDebugSession(@NotNull ExecutionConsole executionConsole) {
    for (final XDebugSession debuggerSession : getDebugSessions()) {
      if (executionConsole == debuggerSession.getRunContentDescriptor().getExecutionConsole()) {
        return debuggerSession;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public <T extends XDebugProcess> Collection<? extends T> getDebugProcesses(Class<T> processClass) {
    final List<T> list = new ArrayList<T>();
    for (XDebugSessionImpl session : mySessions) {
      final XDebugProcess process = session.getDebugProcess();
      if (processClass.isInstance(process)) {
        list.add(processClass.cast(process));
      }
    }
    return list;
  }

  @Nullable
  public XDebugSessionImpl getCurrentSession() {
    if (myLastActiveSession != null) {
      return myLastActiveSession;
    }
    return !mySessions.isEmpty() ? mySessions.get(0) : null;
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
