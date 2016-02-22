/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.AppTopics;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointAdapter;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author nik
 */
@State(name = XDebuggerManagerImpl.COMPONENT_NAME, storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class XDebuggerManagerImpl extends XDebuggerManager
  implements NamedComponent, PersistentStateComponent<XDebuggerManagerImpl.XDebuggerState> {
  @NonNls public static final String COMPONENT_NAME = "XDebuggerManager";
  private final Project myProject;
  private final XBreakpointManagerImpl myBreakpointManager;
  private final XDebuggerWatchesManager myWatchesManager;
  private final Map<ProcessHandler, XDebugSessionImpl> mySessions;
  private final ExecutionPointHighlighter myExecutionPointHighlighter;
  private final AtomicReference<XDebugSessionImpl> myActiveSession = new AtomicReference<XDebugSessionImpl>();

  public XDebuggerManagerImpl(final Project project, final StartupManager startupManager, MessageBus messageBus) {
    myProject = project;
    myBreakpointManager = new XBreakpointManagerImpl(project, this, startupManager);
    myWatchesManager = new XDebuggerWatchesManager();
    mySessions = new LinkedHashMap<ProcessHandler, XDebugSessionImpl>();
    myExecutionPointHighlighter = new ExecutionPointHighlighter(project);

    MessageBusConnection messageBusConnection = messageBus.connect();
    messageBusConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
        updateExecutionPoint(file, true);
      }

      @Override
      public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
        updateExecutionPoint(file, true);
      }
    });
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        updateExecutionPoint(file, false);
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
        if (descriptor != null && executor.equals(DefaultDebugExecutor.getDebugExecutorInstance())) {
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
        if (descriptor != null && executor.equals(DefaultDebugExecutor.getDebugExecutorInstance())) {
          mySessions.remove(descriptor.getProcessHandler());
        }
      }
    });
  }

  private void updateExecutionPoint(@NotNull VirtualFile file, boolean navigate) {
    if (file.equals(myExecutionPointHighlighter.getCurrentFile())) {
      myExecutionPointHighlighter.update(navigate);
    }
  }

  @Override
  @NotNull
  public XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  public XDebuggerWatchesManager getWatchesManager() {
    return myWatchesManager;
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return COMPONENT_NAME;
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
    XDebugSessionImpl session = startSession(contentToReuse, starter, new XDebugSessionImpl(null, this, sessionName,
                                                                                            icon, showToolWindowOnSuspendOnly));
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

    XDebugSessionData oldSessionData = null;
    if (contentToReuse != null) {
      JComponent component = contentToReuse.getComponent();
      if (component != null) {
        oldSessionData = XDebugSessionData.DATA_KEY.getData(DataManager.getInstance().getDataContext(component));
      }
    }

    session.initSessionData(oldSessionData);

    // Perform custom configuration of session data for XDebugProcessConfiguratorStarter classes
    if (processStarter instanceof XDebugProcessConfiguratorStarter) {
      session.activateSession();
      ((XDebugProcessConfiguratorStarter)processStarter).configure(session.getSessionData());
    }

    session.init(process, contentToReuse);

    mySessions.put(session.getDebugProcess().getProcessHandler(), session);

    return session;
  }

  public void removeSession(@NotNull final XDebugSessionImpl session) {
    XDebugSessionTab sessionTab = session.getSessionTab();
    mySessions.remove(session.getDebugProcess().getProcessHandler());
    if (sessionTab != null) {
      RunContentDescriptor descriptor = sessionTab.getRunContentDescriptor();
      if (descriptor != null) {
        // in test-mode RunContentWithExecutorListener.contentRemoved events are not sent (see RunContentManagerImpl.showRunContent)
        // so we make sure the mySessions and mySessionData are cleared correctly when session is disposed
        Disposer.register(descriptor, () -> mySessions.remove(session.getDebugProcess().getProcessHandler()));
      }

      if (!myProject.isDisposed() && !ApplicationManager.getApplication().isUnitTestMode() && XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isHideDebuggerOnProcessTermination()) {
        ExecutionManager.getInstance(myProject).getContentManager().hideRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), descriptor);
      }
    }
    if (myActiveSession.compareAndSet(session, null)) {
      onActiveSessionChanged();
    }
  }

  void updateExecutionPoint(@Nullable XSourcePosition position, boolean useSelection, @Nullable GutterIconRenderer gutterIconRenderer) {
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

  @Override
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
  public <T extends XDebugProcess> List<? extends T> getDebugProcesses(Class<T> processClass) {
    List<T> list = null;
    for (XDebugSessionImpl session : mySessions.values()) {
      final XDebugProcess process = session.getDebugProcess();
      if (processClass.isInstance(process)) {
        if (list == null) {
          list = new SmartList<T>();
        }
        list.add(processClass.cast(process));
      }
    }
    return ContainerUtil.notNullize(list);
  }

  @Override
  @Nullable
  public XDebugSessionImpl getCurrentSession() {
    return myActiveSession.get();
  }

  void setCurrentSession(@Nullable XDebugSessionImpl session) {
    boolean sessionChanged = myActiveSession.getAndSet(session) != session;
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
      onActiveSessionChanged();
    }
  }

  @Override
  public XDebuggerState getState() {
    return new XDebuggerState(myBreakpointManager.getState(), myWatchesManager.getState());
  }

  public boolean isFullLineHighlighter() {
    return myExecutionPointHighlighter.isFullLineHighlighter();
  }

  @Override
  public void loadState(final XDebuggerState state) {
    myBreakpointManager.loadState(state.myBreakpointManagerState);
    myWatchesManager.loadState(state.myWatchesManagerState);
  }

  public void showExecutionPosition() {
    myExecutionPointHighlighter.navigateTo();
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class XDebuggerState {
    private XBreakpointManagerImpl.BreakpointManagerState myBreakpointManagerState;
    private XDebuggerWatchesManager.WatchesManagerState myWatchesManagerState;

    public XDebuggerState() {
    }

    public XDebuggerState(final XBreakpointManagerImpl.BreakpointManagerState breakpointManagerState, XDebuggerWatchesManager.WatchesManagerState watchesManagerState) {
      myBreakpointManagerState = breakpointManagerState;
      myWatchesManagerState = watchesManagerState;
    }

    @Property(surroundWithTag = false)
    public XBreakpointManagerImpl.BreakpointManagerState getBreakpointManagerState() {
      return myBreakpointManagerState;
    }

    public void setBreakpointManagerState(final XBreakpointManagerImpl.BreakpointManagerState breakpointManagerState) {
      myBreakpointManagerState = breakpointManagerState;
    }

    @Property(surroundWithTag = false)
    public XDebuggerWatchesManager.WatchesManagerState getWatchesManagerState() {
      return myWatchesManagerState;
    }

    public void setWatchesManagerState(XDebuggerWatchesManager.WatchesManagerState watchesManagerState) {
      myWatchesManagerState = watchesManagerState;
    }
  }
}
