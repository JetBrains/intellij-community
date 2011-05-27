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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpVirtualFileListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
 */
public class XBreakpointManagerImpl implements XBreakpointManager, PersistentStateComponent<XBreakpointManagerImpl.BreakpointManagerState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl");
  public static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTER = new SkipDefaultValuesSerializationFilters();
  private final MultiValuesMap<XBreakpointType, XBreakpointBase<?,?,?>> myBreakpoints = new MultiValuesMap<XBreakpointType, XBreakpointBase<?,?,?>>(true);
  private final Map<XBreakpointType, XBreakpointBase<?,?,?>> myDefaultBreakpoints = new LinkedHashMap<XBreakpointType, XBreakpointBase<?, ?, ?>>();
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
    XBreakpointBase<?, T, ?> breakpoint = createBreakpoint(type, properties, true);
    addBreakpoint(breakpoint, false, true);
    return breakpoint;
  }

  private <T extends XBreakpointProperties> XBreakpointBase<?, T, ?> createBreakpoint(XBreakpointType<XBreakpoint<T>, T> type,
                                                                                      T properties, final boolean enabled) {
    XBreakpointBase.BreakpointState<?,T,?> state = new XBreakpointBase.BreakpointState<XBreakpoint<T>,T,XBreakpointType<XBreakpoint<T>,T>>(enabled, type.getId());
    return new XBreakpointBase<XBreakpoint<T>,T, XBreakpointBase.BreakpointState<?,T,?>>(type, this, properties, state);
  }

  private <T extends XBreakpointProperties> void addBreakpoint(final XBreakpointBase<?, T, ?> breakpoint, final boolean defaultBreakpoint,
                                                               boolean initUI) {
    XBreakpointType type = breakpoint.getType();
    if (defaultBreakpoint) {
      LOG.assertTrue(!myDefaultBreakpoints.containsKey(type), "Cannot have more than one default breakpoint (type " + type.getId() + ")");
      myDefaultBreakpoints.put(type, breakpoint);
    }
    else {
      myBreakpoints.put(type, breakpoint);
    }
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
    doRemoveBreakpoint(breakpoint);
  }

  private void doRemoveBreakpoint(XBreakpoint<?> breakpoint) {
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
    addBreakpoint(breakpoint, false, true);
    return breakpoint;
  }

  @NotNull
  public XBreakpointBase<?,?,?>[] getAllBreakpoints() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    List<XBreakpointBase<?, ?, ?>> breakpoints = new ArrayList<XBreakpointBase<?, ?, ?>>();
    breakpoints.addAll(myDefaultBreakpoints.values());
    breakpoints.addAll(myBreakpoints.values());
    return breakpoints.toArray(new XBreakpointBase[breakpoints.size()]);
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull final XBreakpointType<B,?> type) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Collection<? extends XBreakpointBase<?,?,?>> breakpoints = myBreakpoints.get(type);
    Collection<? extends B> regular = breakpoints != null ? Collections.unmodifiableCollection((Collection<? extends B>)breakpoints) : Collections.<B>emptyList();

    final XBreakpointBase<?, ?, ?> defaultBreakpoint = myDefaultBreakpoints.get(type);
    if (defaultBreakpoint == null) return regular;
    List<B> result = new ArrayList<B>();
    result.add((B)defaultBreakpoint);
    result.addAll(regular);
    return result;
  }

  @Override
  @Nullable
  public <B extends XBreakpoint<?>> B getDefaultBreakpoint(@NotNull XBreakpointType<B, ?> type) {
    //noinspection unchecked
    return (B)myDefaultBreakpoints.get(type);
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

  @Override
  public boolean isDefaultBreakpoint(@NotNull XBreakpoint<?> breakpoint) {
    //noinspection SuspiciousMethodCalls
    return myDefaultBreakpoints.values().contains(breakpoint);
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
    for (XBreakpointBase<?, ?, ?> breakpoint : myDefaultBreakpoints.values()) {
      final XBreakpointBase.BreakpointState breakpointState = breakpoint.getState();
      if (differsFromDefault(breakpoint.getType(), breakpointState)) {
        state.getDefaultBreakpoints().add(breakpointState);
      }
    }
    for (XBreakpointBase<?,?,?> breakpoint : myBreakpoints.values()) {
      state.getBreakpoints().add(breakpoint.getState());
    }
    for (Map.Entry<XBreakpointType<?,?>, XBreakpointTypeDialogState> entry : myBreakpointsDialogSettings.entrySet()) {
      state.getBreakpointTypeDialogProperties().put(entry.getKey().getId(), entry.getValue());
    }
    return state;
  }

  private <P extends XBreakpointProperties> boolean differsFromDefault(XBreakpointType<?, P> type,
                                                                              XBreakpointBase.BreakpointState state) {
    final XBreakpoint<P> defaultBreakpoint = createDefaultBreakpoint(type);
    if (defaultBreakpoint == null) {
      return false;
    }

    XBreakpointBase.BreakpointState defaultState = ((XBreakpointBase)defaultBreakpoint).getState();
    Element defaultElement = XmlSerializer.serialize(defaultState, SERIALIZATION_FILTER);
    Element currentElement = XmlSerializer.serialize(state, SERIALIZATION_FILTER);
    return !JDOMUtil.areElementsEqual(defaultElement, currentElement);
  }

  public void loadState(final BreakpointManagerState state) {
    myBreakpointsDialogSettings.clear();
    for (Map.Entry<String, XBreakpointTypeDialogState> entry : state.getBreakpointTypeDialogProperties().entrySet()) {
      XBreakpointType<?, ?> type = XBreakpointUtil.findType(entry.getKey());
      if (type != null) {
        myBreakpointsDialogSettings.put(type, entry.getValue());
      }
    }

    myDefaultBreakpoints.clear();
    for (XBreakpointBase.BreakpointState breakpointState : state.getDefaultBreakpoints()) {
      loadBreakpoint(breakpointState, true);
    }
    for (XBreakpointType<?, ?> type : XBreakpointUtil.getBreakpointTypes()) {
      if (!myDefaultBreakpoints.containsKey(type)) {
        addDefaultBreakpoint(type);
      }
    }

    for (XBreakpointBase<?,?,?> breakpoint : myBreakpoints.values()) {
      doRemoveBreakpoint(breakpoint);
    }
    for (XBreakpointBase.BreakpointState breakpointState : state.getBreakpoints()) {
      loadBreakpoint(breakpointState, false);
    }
    myDependentBreakpointManager.loadState();
    myLineBreakpointManager.updateBreakpointsUI();
  }

  private <P extends XBreakpointProperties> void addDefaultBreakpoint(XBreakpointType<?, P> type) {
    final XBreakpoint<P> breakpoint = createDefaultBreakpoint(type);
    if (breakpoint != null) {
      addBreakpoint((XBreakpointBase<?, P, ?>)breakpoint, true, false);
    }
  }

  @Nullable
  private <P extends XBreakpointProperties> XBreakpoint<P> createDefaultBreakpoint(final XBreakpointType<? extends XBreakpoint<P>, P> type) {
    return type.createDefaultBreakpoint(new XBreakpointType.XBreakpointCreator<P>() {
      @NotNull
      @Override
      public XBreakpoint<P> createBreakpoint(@Nullable P properties) {
        //noinspection unchecked
        return XBreakpointManagerImpl.this.createBreakpoint((XBreakpointType<XBreakpoint<P>, P>)type, properties, false);
      }
    });
  }

  private void loadBreakpoint(XBreakpointBase.BreakpointState breakpointState, final boolean defaultBreakpoint) {
    XBreakpointBase<?,?,?> breakpoint = createBreakpoint(breakpointState);
    if (breakpoint != null) {
      addBreakpoint(breakpoint, defaultBreakpoint, false);
    }
  }

  @Nullable
  public XBreakpointTypeDialogState getDialogState(@NotNull XBreakpointType<?, ?> type) {
    return myBreakpointsDialogSettings.get(type);
  }

  public void putDialogState(@NotNull XBreakpointType<?, ?> type, XBreakpointTypeDialogState dialogState) {
    myBreakpointsDialogSettings.put(type, dialogState);
  }

  @Nullable
  private XBreakpointBase<?,?,?> createBreakpoint(final XBreakpointBase.BreakpointState breakpointState) {
    XBreakpointType<?,?> type = XBreakpointUtil.findType(breakpointState.getTypeId());
    if (type == null) return null;
    //noinspection unchecked
    return breakpointState.createBreakpoint(type, this);
  }


  @Tag("breakpoint-manager")
  public static class BreakpointManagerState {
    private List<XBreakpointBase.BreakpointState> myDefaultBreakpoints = new ArrayList<XBreakpointBase.BreakpointState>();
    private List<XBreakpointBase.BreakpointState> myBreakpoints = new ArrayList<XBreakpointBase.BreakpointState>();
    private Map<String, XBreakpointTypeDialogState> myBreakpointTypeDialogProperties = new HashMap<String, XBreakpointTypeDialogState>();

    @Tag("default-breakpoints")
    @AbstractCollection(surroundWithTag = false)
    public List<XBreakpointBase.BreakpointState> getDefaultBreakpoints() {
      return myDefaultBreakpoints;
    }

    @Tag("breakpoints")
    @AbstractCollection(surroundWithTag = false,
                        elementTypes = {XBreakpointBase.BreakpointState.class, XLineBreakpointImpl.LineBreakpointState.class})
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

    public void setDefaultBreakpoints(List<XBreakpointBase.BreakpointState> defaultBreakpoints) {
      myDefaultBreakpoints = defaultBreakpoints;
    }

    public void setBreakpointTypeDialogProperties(final Map<String, XBreakpointTypeDialogState> breakpointTypeDialogProperties) {
      myBreakpointTypeDialogProperties = breakpointTypeDialogProperties;
    }
  }
}
