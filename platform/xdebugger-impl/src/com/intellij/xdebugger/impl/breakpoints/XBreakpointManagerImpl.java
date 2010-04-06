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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpVirtualFileListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class XBreakpointManagerImpl implements XBreakpointManager, PersistentStateComponent<XBreakpointManagerImpl.BreakpointManagerState> {
  private final MultiValuesMap<XBreakpointType, XBreakpointBase<?,?,?>> myBreakpoints = new MultiValuesMap<XBreakpointType, XBreakpointBase<?,?,?>>(true);
  private final Map<XBreakpointType, EventDispatcher<XBreakpointListener>> myDispatchers = new HashMap<XBreakpointType, EventDispatcher<XBreakpointListener>>();
  private final Map<XBreakpointType<?,?>, XBreakpointTypeDialogState> myBreakpointsDialogSettings = new HashMap<XBreakpointType<?,?>, XBreakpointTypeDialogState>();
  private final EventDispatcher<XBreakpointListener> myAllBreakpointsDispatcher;
  private final XLineBreakpointManager myLineBreakpointManager;
  private final Project myProject;
  private final XDebuggerManagerImpl myDebuggerManager;
  private final XDependentBreakpointManager myDependentBreakpointManager;

  public XBreakpointManagerImpl(final Project project, final XDebuggerManagerImpl debuggerManager, StartupManager startupManager) {
    myProject = project;
    myDebuggerManager = debuggerManager;
    myAllBreakpointsDispatcher = EventDispatcher.create(XBreakpointListener.class);
    myDependentBreakpointManager = new XDependentBreakpointManager(this);
    myLineBreakpointManager = new XLineBreakpointManager(project, myDependentBreakpointManager, startupManager);
    if (!project.isDefault()) {
      HttpVirtualFileListener httpVirtualFileListener = new HttpVirtualFileListener() {
        public void fileDownloaded(@NotNull final VirtualFile file) {
          updateBreakpointInFile(file);
        }
      };
      HttpFileSystem.getInstance().addFileListener(httpVirtualFileListener, project);
    }
  }

  private void updateBreakpointInFile(final VirtualFile file) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        XBreakpointBase<?, ?, ?>[] breakpoints = getAllBreakpoints();
        for (XBreakpointBase<?, ?, ?> breakpoint : breakpoints) {
          XSourcePosition position = breakpoint.getSourcePosition();
          if (position != null && position.getFile() == file) {
            fireBreakpointChanged(breakpoint);
          }
        }
      }
    });
  }

  public XLineBreakpointManager getLineBreakpointManager() {
    return myLineBreakpointManager;
  }

  public XDependentBreakpointManager getDependentBreakpointManager() {
    return myDependentBreakpointManager;
  }

  public XDebuggerManagerImpl getDebuggerManager() {
    return myDebuggerManager;
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  public <T extends XBreakpointProperties> XBreakpoint<T> addBreakpoint(final XBreakpointType<XBreakpoint<T>,T> type, @Nullable final T properties) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    XBreakpointBase.BreakpointState<?,T,?> state = new XBreakpointBase.BreakpointState<XBreakpoint<T>,T,XBreakpointType<XBreakpoint<T>,T>>(true, type.getId());
    XBreakpointBase<?,T, ?> breakpoint = new XBreakpointBase<XBreakpoint<T>,T, XBreakpointBase.BreakpointState<?,T,?>>(type, this, properties, state);
    addBreakpoint(breakpoint, true);
    return breakpoint;
  }

  private <T extends XBreakpointProperties> void addBreakpoint(final XBreakpointBase<?,T,?> breakpoint, boolean initUI) {
    XBreakpointType type = breakpoint.getType();
    myBreakpoints.put(type, breakpoint);
    if (breakpoint instanceof XLineBreakpointImpl) {
      myLineBreakpointManager.registerBreakpoint((XLineBreakpointImpl)breakpoint, initUI);
    }
    EventDispatcher<XBreakpointListener> dispatcher = myDispatchers.get(type);
    if (dispatcher != null) {
      //noinspection unchecked
      dispatcher.getMulticaster().breakpointAdded(breakpoint);
    }
    getBreakpointDispatcherMulticaster().breakpointAdded(breakpoint);
  }

  private XBreakpointListener<XBreakpoint<?>> getBreakpointDispatcherMulticaster() {
    //noinspection unchecked
    return myAllBreakpointsDispatcher.getMulticaster();
  }

  public void fireBreakpointChanged(XBreakpointBase<?, ?, ?> breakpoint) {
    if (breakpoint instanceof XLineBreakpointImpl) {
      myLineBreakpointManager.breakpointChanged((XLineBreakpointImpl)breakpoint);
    }
    EventDispatcher<XBreakpointListener> dispatcher = myDispatchers.get(breakpoint.getType());
    if (dispatcher != null) {
      //noinspection unchecked
      dispatcher.getMulticaster().breakpointChanged(breakpoint);
    }
    getBreakpointDispatcherMulticaster().breakpointChanged(breakpoint);
  }

  public void removeBreakpoint(@NotNull final XBreakpoint<?> breakpoint) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    XBreakpointType type = breakpoint.getType();
    XBreakpointBase<?,?,?> breakpointBase = (XBreakpointBase<?,?,?>)breakpoint;
    myBreakpoints.remove(type, breakpointBase);
    if (breakpointBase instanceof XLineBreakpointImpl) {
      myLineBreakpointManager.unregisterBreakpoint((XLineBreakpointImpl)breakpointBase);
    }
    breakpointBase.dispose();
    EventDispatcher<XBreakpointListener> dispatcher = myDispatchers.get(type);
    if (dispatcher != null) {
      //noinspection unchecked
      dispatcher.getMulticaster().breakpointRemoved(breakpoint);
    }
    getBreakpointDispatcherMulticaster().breakpointRemoved(breakpoint);
  }

  @NotNull
  public <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(final XLineBreakpointType<T> type, @NotNull final String fileUrl,
                                                                            final int line, @Nullable final T properties) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    XLineBreakpointImpl<T> breakpoint = new XLineBreakpointImpl<T>(type, this, fileUrl, line, properties);
    addBreakpoint(breakpoint, true);
    return breakpoint;
  }

  @NotNull
  public XBreakpointBase<?,?,?>[] getAllBreakpoints() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Collection<XBreakpointBase<?,?,?>> breakpoints = myBreakpoints.values();
    return breakpoints.toArray(new XBreakpointBase[breakpoints.size()]);
  }

  @NotNull
  public <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull final XBreakpointType<B,?> type) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Collection<? extends XBreakpointBase<?,?,?>> breakpoints = myBreakpoints.get(type);
    if (breakpoints == null) {
      return Collections.emptyList();
    }
    //noinspection unchecked
    return Collections.unmodifiableCollection((Collection<? extends B>)breakpoints);
  }

  @Nullable
  public <P extends XBreakpointProperties> XLineBreakpoint<P> findBreakpointAtLine(@NotNull final XLineBreakpointType<P> type, @NotNull final VirtualFile file,
                                                                                   final int line) {
    Collection<XBreakpointBase<?,?,?>> breakpoints = myBreakpoints.get(type);
    if (breakpoints == null) return null;

    for (XBreakpointBase<?, ?, ?> breakpoint : breakpoints) {
      XLineBreakpoint lineBreakpoint = (XLineBreakpoint)breakpoint;
      if (lineBreakpoint.getFileUrl().equals(file.getUrl()) && lineBreakpoint.getLine() == line) {
        //noinspection unchecked
        return lineBreakpoint;
      }
    }
    return null;
  }

  private <T extends XBreakpointProperties> EventDispatcher<XBreakpointListener> getOrCreateDispatcher(final XBreakpointType<?,T> type) {
    EventDispatcher<XBreakpointListener> dispatcher = myDispatchers.get(type);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(XBreakpointListener.class);
      myDispatchers.put(type, dispatcher);
    }
    return dispatcher;
  }

  public <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(@NotNull final XBreakpointType<B,P> type, @NotNull final XBreakpointListener<B> listener) {
    getOrCreateDispatcher(type).addListener(listener);
  }

  public <B extends XBreakpoint<P>, P extends XBreakpointProperties> void removeBreakpointListener(@NotNull final XBreakpointType<B,P> type,
                                                                                                   @NotNull final XBreakpointListener<B> listener) {
    getOrCreateDispatcher(type).removeListener(listener);
  }

  public <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(@NotNull final XBreakpointType<B,P> type, @NotNull final XBreakpointListener<B> listener,
                                                                                                final Disposable parentDisposable) {
    getOrCreateDispatcher(type).addListener(listener, parentDisposable);
  }

  public void addBreakpointListener(@NotNull final XBreakpointListener<XBreakpoint<?>> listener) {
    myAllBreakpointsDispatcher.addListener(listener);
  }

  public void removeBreakpointListener(@NotNull final XBreakpointListener<XBreakpoint<?>> listener) {
    myAllBreakpointsDispatcher.removeListener(listener);
  }

  public void addBreakpointListener(@NotNull final XBreakpointListener<XBreakpoint<?>> listener, @NotNull final Disposable parentDisposable) {
    myAllBreakpointsDispatcher.addListener(listener, parentDisposable);
  }

  public void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint, @Nullable Icon icon, @Nullable String errorMessage) {
    final CustomizedBreakpointPresentation presentation = new CustomizedBreakpointPresentation();
    presentation.setErrorMessage(errorMessage);
    presentation.setIcon(icon);
    ((XLineBreakpointImpl)breakpoint).setCustomizedPresentation(presentation);
    myLineBreakpointManager.queueBreakpointUpdate(breakpoint);
  }

  public BreakpointManagerState getState() {
    myDependentBreakpointManager.saveState();
    BreakpointManagerState state = new BreakpointManagerState();
    for (XBreakpointBase<?,?,?> breakpoint : myBreakpoints.values()) {
      state.getBreakpoints().add(breakpoint.getState());
    }
    for (Map.Entry<XBreakpointType<?,?>, XBreakpointTypeDialogState> entry : myBreakpointsDialogSettings.entrySet()) {
      state.getBreakpointTypeDialogProperties().put(entry.getKey().getId(), entry.getValue());
    }
    return state;
  }

  public void loadState(final BreakpointManagerState state) {
    myBreakpointsDialogSettings.clear();
    for (Map.Entry<String, XBreakpointTypeDialogState> entry : state.getBreakpointTypeDialogProperties().entrySet()) {
      XBreakpointType<?, ?> type = XBreakpointUtil.findType(entry.getKey());
      if (type != null) {
        myBreakpointsDialogSettings.put(type, entry.getValue());
      }
    }

    removeAllBreakpoints();
    for (XBreakpointBase.BreakpointState breakpointState : state.getBreakpoints()) {
      XBreakpointBase<?,?,?> breakpoint = createBreakpoint(breakpointState);
      if (breakpoint != null) {
        addBreakpoint(breakpoint, false);
      }
    }
    myDependentBreakpointManager.loadState();
    myLineBreakpointManager.updateBreakpointsUI();
  }

  @Nullable
  public XBreakpointTypeDialogState getDialogState(@NotNull XBreakpointType<?, ?> type) {
    return myBreakpointsDialogSettings.get(type);
  }

  public void putDialogState(@NotNull XBreakpointType<?, ?> type, XBreakpointTypeDialogState dialogState) {
    myBreakpointsDialogSettings.put(type, dialogState);
  }

  private void removeAllBreakpoints() {
    for (XBreakpointBase<?,?,?> breakpoint : myBreakpoints.values()) {
      removeBreakpoint(breakpoint);
    }
  }

  @Nullable
  private XBreakpointBase<?,?,?> createBreakpoint(final XBreakpointBase.BreakpointState breakpointState) {
    XBreakpointType<?,?> type = XBreakpointUtil.findType(breakpointState.getTypeId());
    if (type == null) return null;                    
    return breakpointState.createBreakpoint(type, this);
  }


  @Tag("breakpoint-manager")
  public static class BreakpointManagerState {
    private List<XBreakpointBase.BreakpointState> myBreakpoints = new ArrayList<XBreakpointBase.BreakpointState>();
    private Map<String, XBreakpointTypeDialogState> myBreakpointTypeDialogProperties = new HashMap<String, XBreakpointTypeDialogState>();

    @Tag("breakpoints")
    @AbstractCollection(surroundWithTag = false, elementTypes = {XBreakpointBase.BreakpointState.class, XLineBreakpointImpl.LineBreakpointState.class})
    public List<XBreakpointBase.BreakpointState> getBreakpoints() {
      return myBreakpoints;
    }

    @Tag("dialog-properties")
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, keyAttributeName = "type-id",
                   entryTagName = "breakpoints-dialog")
    public Map<String, XBreakpointTypeDialogState> getBreakpointTypeDialogProperties() {
      return myBreakpointTypeDialogProperties;
    }

    public void setBreakpoints(final List<XBreakpointBase.BreakpointState> breakpoints) {
      myBreakpoints = breakpoints;
    }

    public void setBreakpointTypeDialogProperties(final Map<String, XBreakpointTypeDialogState> breakpointTypeDialogProperties) {
      myBreakpointTypeDialogProperties = breakpointTypeDialogProperties;
    }
  }
}
