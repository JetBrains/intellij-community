/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpVirtualFileListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import one.util.streamex.StreamEx;
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
  private final MultiValuesMap<XBreakpointType, XBreakpointBase<?,?,?>> myBreakpoints = new MultiValuesMap<>(true);
  private final Map<XBreakpointType, XBreakpointBase<?,?,?>> myDefaultBreakpoints = new LinkedHashMap<>();
  private final Map<XBreakpointType, BreakpointState<?,?,?>> myBreakpointsDefaults = new LinkedHashMap<>();
  private final Set<XBreakpointBase<?,?,?>> myAllBreakpoints = new HashSet<>();
  private final Map<XBreakpointType, EventDispatcher<XBreakpointListener>> myDispatchers = new HashMap<>();
  private XBreakpointsDialogState myBreakpointsDialogSettings;
  private final EventDispatcher<XBreakpointListener> myAllBreakpointsDispatcher;
  private final XLineBreakpointManager myLineBreakpointManager;
  private final Project myProject;
  private final XDebuggerManagerImpl myDebuggerManager;
  private final XDependentBreakpointManager myDependentBreakpointManager;
  private long myTime;
  private String myDefaultGroup;

  public XBreakpointManagerImpl(final Project project, final XDebuggerManagerImpl debuggerManager, StartupManager startupManager) {
    myProject = project;
    myDebuggerManager = debuggerManager;
    myAllBreakpointsDispatcher = EventDispatcher.create(XBreakpointListener.class);
    myDependentBreakpointManager = new XDependentBreakpointManager(this);
    myLineBreakpointManager = new XLineBreakpointManager(project, myDependentBreakpointManager, startupManager);
    if (!project.isDefault()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        HttpVirtualFileListener httpVirtualFileListener = this::updateBreakpointInFile;
        HttpFileSystem.getInstance().addFileListener(httpVirtualFileListener, project);
      }
      XBreakpointUtil.breakpointTypes().forEach(this::addDefaultBreakpoint);
    }
  }

  private void updateBreakpointInFile(final VirtualFile file) {
    ApplicationManager.getApplication().invokeLater(() -> {
      for (XBreakpointBase breakpoint : getAllBreakpoints()) {
        XSourcePosition position = breakpoint.getSourcePosition();
        if (position != null && Comparing.equal(position.getFile(), file)) {
          fireBreakpointChanged(breakpoint);
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

  @Override
  @NotNull
  public <T extends XBreakpointProperties> XBreakpoint<T> addBreakpoint(final XBreakpointType<XBreakpoint<T>,T> type, @Nullable final T properties) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    XBreakpointBase<?, T, ?> breakpoint = createBreakpoint(type, properties, true, false);
    addBreakpoint(breakpoint, false, true);
    return breakpoint;
  }

  private <T extends XBreakpointProperties> XBreakpointBase<?, T, ?> createBreakpoint(XBreakpointType<XBreakpoint<T>, T> type,
                                                                                      T properties, final boolean enabled,
                                                                                      boolean defaultBreakpoint) {
    BreakpointState<?,T,?> state = new BreakpointState<>(enabled,
                                                         type.getId(),
                                                         defaultBreakpoint ? 0 : myTime++, type.getDefaultSuspendPolicy());
    getBreakpointDefaults(type).applyDefaults(state);
    state.setGroup(myDefaultGroup);
    return new XBreakpointBase<XBreakpoint<T>,T, BreakpointState<?,T,?>>(type, this, properties, state);
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
    myAllBreakpoints.add(breakpoint);
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
    if (!myAllBreakpoints.contains(breakpoint)) {
      return;
    }

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

  @Override
  public void removeBreakpoint(@NotNull final XBreakpoint<?> breakpoint) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    doRemoveBreakpoint(breakpoint);
  }

  private void doRemoveBreakpoint(XBreakpoint<?> breakpoint) {
    if (isDefaultBreakpoint(breakpoint)) {
      // removing default breakpoint should just disable it
      breakpoint.setEnabled(false);
    }
    else {
      XBreakpointType type = breakpoint.getType();
      XBreakpointBase<?,?,?> breakpointBase = (XBreakpointBase<?,?,?>)breakpoint;
      myBreakpoints.remove(type, breakpointBase);
      myAllBreakpoints.remove(breakpointBase);
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
  }

  @Override
  @NotNull
  public <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(final XLineBreakpointType<T> type,
                                                                                @NotNull final String fileUrl,
                                                                                final int line,
                                                                                @Nullable final T properties) {
    return addLineBreakpoint(type, fileUrl, line, properties, false);
  }

  @Override
  @NotNull
  public <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(final XLineBreakpointType<T> type,
                                                                                @NotNull final String fileUrl,
                                                                                final int line,
                                                                                @Nullable final T properties,
                                                                                boolean temporary) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LineBreakpointState<T> state = new LineBreakpointState<>(true, type.getId(), fileUrl, line, temporary,
                                                             myTime++, type.getDefaultSuspendPolicy());
    getBreakpointDefaults(type).applyDefaults(state);
    state.setGroup(myDefaultGroup);
    XLineBreakpointImpl<T> breakpoint = new XLineBreakpointImpl<>(type, this, properties,
                                                                  state);
    addBreakpoint(breakpoint, false, true);
    return breakpoint;
  }

  @Override
  @NotNull
  public XBreakpointBase<?,?,?>[] getAllBreakpoints() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myAllBreakpoints.toArray(new XBreakpointBase[myAllBreakpoints.size()]);
  }

  @Override
  @SuppressWarnings({"unchecked"})
  @NotNull
  public <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull final XBreakpointType<B,?> type) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    List<B> result = new ArrayList<>();
    B defaultBreakpoint = getDefaultBreakpoint(type);
    if (defaultBreakpoint != null) {
      result.add(defaultBreakpoint);
    }
    Collection<XBreakpointBase<?, ?, ?>> breakpoints = myBreakpoints.get(type);
    if (breakpoints != null) {
      result.addAll((Collection<? extends B>)breakpoints);
    }
    return Collections.unmodifiableList(result);
  }

  @NotNull
  @Override
  public <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull Class<? extends XBreakpointType<B, ?>> typeClass) {
    XBreakpointType<B, ?> type = XDebuggerUtil.getInstance().findBreakpointType(typeClass);
    LOG.assertTrue(type != null, "Unregistered breakpoint type " + typeClass + ", registered: " + Arrays.toString(XBreakpointType.EXTENSION_POINT_NAME.getExtensions()));
    return getBreakpoints(type);
  }

  @Override
  @Nullable
  public <B extends XBreakpoint<?>> B getDefaultBreakpoint(@NotNull XBreakpointType<B, ?> type) {
    //noinspection unchecked
    return (B)myDefaultBreakpoints.get(type);
  }

  @Override
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
    return myDispatchers.computeIfAbsent(type, k -> EventDispatcher.create(XBreakpointListener.class));
  }

  @Override
  public <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(@NotNull final XBreakpointType<B,P> type, @NotNull final XBreakpointListener<B> listener) {
    getOrCreateDispatcher(type).addListener(listener);
  }

  @Override
  public <B extends XBreakpoint<P>, P extends XBreakpointProperties> void removeBreakpointListener(@NotNull final XBreakpointType<B,P> type,
                                                                                                   @NotNull final XBreakpointListener<B> listener) {
    getOrCreateDispatcher(type).removeListener(listener);
  }

  @Override
  public <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(@NotNull final XBreakpointType<B,P> type, @NotNull final XBreakpointListener<B> listener,
                                                                                                final Disposable parentDisposable) {
    getOrCreateDispatcher(type).addListener(listener, parentDisposable);
  }

  @Override
  public void addBreakpointListener(@NotNull final XBreakpointListener<XBreakpoint<?>> listener) {
    myAllBreakpointsDispatcher.addListener(listener);
  }

  @Override
  public void removeBreakpointListener(@NotNull final XBreakpointListener<XBreakpoint<?>> listener) {
    myAllBreakpointsDispatcher.removeListener(listener);
  }

  @Override
  public void addBreakpointListener(@NotNull final XBreakpointListener<XBreakpoint<?>> listener, @NotNull final Disposable parentDisposable) {
    myAllBreakpointsDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint, @Nullable Icon icon, @Nullable String errorMessage) {
    XLineBreakpointImpl lineBreakpoint = (XLineBreakpointImpl)breakpoint;
    CustomizedBreakpointPresentation presentation = lineBreakpoint.getCustomizedPresentation();
    if (presentation == null) {
      if (icon == null && errorMessage == null) {
        return;
      }
      presentation = new CustomizedBreakpointPresentation();
    }
    else if (Comparing.equal(presentation.getIcon(), icon) && Comparing.strEqual(presentation.getErrorMessage(), errorMessage)) {
      return;
    }

    presentation.setErrorMessage(errorMessage);
    presentation.setIcon(icon);
    lineBreakpoint.setCustomizedPresentation(presentation);
    myLineBreakpointManager.queueBreakpointUpdate(breakpoint);
  }

  @Override
  public BreakpointManagerState getState() {
    myDependentBreakpointManager.saveState();
    BreakpointManagerState state = new BreakpointManagerState();
    for (XBreakpointBase<?, ?, ?> breakpoint : myDefaultBreakpoints.values()) {
      final BreakpointState breakpointState = breakpoint.getState();
      if (differsFromDefault(breakpoint.getType(), breakpointState)) {
        state.getDefaultBreakpoints().add(breakpointState);
      }
    }
    for (XBreakpointBase<?,?,?> breakpoint : myBreakpoints.values()) {
      state.getBreakpoints().add(breakpoint.getState());
    }

    for (Map.Entry<XBreakpointType, BreakpointState<?,?,?>> entry : myBreakpointsDefaults.entrySet()) {
      if (statesAreDifferent(entry.getValue(), createBreakpointDefaults(entry.getKey()))) {
        state.getBreakpointsDefaults().add(entry.getValue());
      }
    }

    state.setBreakpointsDialogProperties(myBreakpointsDialogSettings);
    state.setTime(myTime);
    state.setDefaultGroup(myDefaultGroup);
    return state;
  }

  private <P extends XBreakpointProperties> boolean differsFromDefault(XBreakpointType<?, P> type,
                                                                              BreakpointState state) {
    final XBreakpoint<P> defaultBreakpoint = createDefaultBreakpoint(type);
    if (defaultBreakpoint == null) {
      return false;
    }

    BreakpointState defaultState = ((XBreakpointBase)defaultBreakpoint).getState();
    return statesAreDifferent(state, defaultState);
  }

  private static boolean statesAreDifferent(BreakpointState state1, BreakpointState state2) {
    Element elem1 = XmlSerializer.serialize(state1, SERIALIZATION_FILTER);
    Element elem2 = XmlSerializer.serialize(state2, SERIALIZATION_FILTER);
    return !JDOMUtil.areElementsEqual(elem1, elem2);
  }

  @Override
  public void loadState(final BreakpointManagerState state) {
    myBreakpointsDialogSettings = state.getBreakpointsDialogProperties();

    myAllBreakpoints.clear();
    myDefaultBreakpoints.clear();
    myBreakpointsDefaults.clear();

    ApplicationManager.getApplication().runReadAction(() -> {
      state.getDefaultBreakpoints().forEach(breakpointState -> loadBreakpoint(breakpointState, true));

      XBreakpointUtil.breakpointTypes().remove(myDefaultBreakpoints::containsKey).forEach(this::addDefaultBreakpoint);

      myBreakpoints.values().forEach(this::doRemoveBreakpoint);

      state.getBreakpoints().forEach(breakpointState -> loadBreakpoint(breakpointState, false));

      for (BreakpointState defaults : state.getBreakpointsDefaults()) {
        XBreakpointType<?, ?> type = XBreakpointUtil.findType(defaults.getTypeId());
        if (type != null) {
          myBreakpointsDefaults.put(type, defaults);
        }
        else {
          LOG.warn("Unknown breakpoint type " + defaults.getTypeId());
        }
      }

      myDependentBreakpointManager.loadState();
    });
    myLineBreakpointManager.updateBreakpointsUI();
    myTime = state.getTime();
    myDefaultGroup = state.getDefaultGroup();
  }

  private <P extends XBreakpointProperties> void addDefaultBreakpoint(XBreakpointType<?, P> type) {
    final XBreakpoint<P> breakpoint = createDefaultBreakpoint(type);
    if (breakpoint != null) {
      addBreakpoint((XBreakpointBase<?, P, ?>)breakpoint, true, false);
    }
  }

  @Nullable
  private <P extends XBreakpointProperties> XBreakpoint<P> createDefaultBreakpoint(final XBreakpointType<? extends XBreakpoint<P>, P> type) {
    return type.createDefaultBreakpoint(properties -> {
      //noinspection unchecked
      return createBreakpoint((XBreakpointType<XBreakpoint<P>, P>)type, properties, false, true);
    });
  }

  private void loadBreakpoint(BreakpointState breakpointState, final boolean defaultBreakpoint) {
    XBreakpointBase<?,?,?> breakpoint = createBreakpoint(breakpointState);
    if (breakpoint != null) {
      addBreakpoint(breakpoint, defaultBreakpoint, false);
    }
  }

  public XBreakpointsDialogState getBreakpointsDialogSettings() {
    return myBreakpointsDialogSettings;
  }

  public void setBreakpointsDialogSettings(XBreakpointsDialogState breakpointsDialogSettings) {
    myBreakpointsDialogSettings = breakpointsDialogSettings;
  }

  public Set<String> getAllGroups() {
    return StreamEx.of(myAllBreakpoints).map(XBreakpointBase::getGroup).nonNull().toSet();
  }

  public String getDefaultGroup() {
    return myDefaultGroup;
  }

  public void setDefaultGroup(String defaultGroup) {
    myDefaultGroup = defaultGroup;
  }

  @Nullable
  private XBreakpointBase<?,?,?> createBreakpoint(final BreakpointState breakpointState) {
    XBreakpointType<?,?> type = XBreakpointUtil.findType(breakpointState.getTypeId());
    if (type == null) {
      LOG.warn("Unknown breakpoint type " + breakpointState.getTypeId());
      return null;
    }
    //noinspection unchecked
    return breakpointState.createBreakpoint(type, this);
  }

  @NotNull
  public BreakpointState getBreakpointDefaults(@NotNull XBreakpointType type) {
    return myBreakpointsDefaults.computeIfAbsent(type, k -> createBreakpointDefaults(type));
  }

  @Nullable
  <T extends XBreakpointProperties> XLineBreakpoint<T> copyLineBreakpoint(@NotNull XLineBreakpoint<T> source,
                                                                          @NotNull String fileUrl,
                                                                          int line) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (!(source instanceof XLineBreakpointImpl<?>)) {
      return null;
    }
    myDependentBreakpointManager.saveState();
    final LineBreakpointState sourceState = ((XLineBreakpointImpl<?>)source).getState();

    final LineBreakpointState newState =
      XmlSerializer.deserialize(XmlSerializer.serialize(sourceState, SERIALIZATION_FILTER), LineBreakpointState.class);
    newState.setLine(line);
    newState.setFileUrl(fileUrl);

    //noinspection unchecked
    final XLineBreakpointImpl<T> breakpoint = (XLineBreakpointImpl<T>)createBreakpoint(newState);
    if (breakpoint != null) {
      addBreakpoint(breakpoint, false, true);
      final XBreakpoint<?> masterBreakpoint = myDependentBreakpointManager.getMasterBreakpoint(source);
      if (masterBreakpoint != null) {
        myDependentBreakpointManager.setMasterBreakpoint(breakpoint, masterBreakpoint, sourceState.getDependencyState().isLeaveEnabled());
      }
    }

    return breakpoint;
  }

  @NotNull
  private static BreakpointState createBreakpointDefaults(@NotNull XBreakpointType type) {
    BreakpointState state = new BreakpointState();
    state.setTypeId(type.getId());
    state.setSuspendPolicy(type.getDefaultSuspendPolicy());
    return state;
  }

  @Tag("breakpoint-manager")
  public static class BreakpointManagerState {
    private List<BreakpointState> myDefaultBreakpoints = new ArrayList<>();
    private List<BreakpointState> myBreakpoints = new ArrayList<>();
    private List<BreakpointState> myBreakpointsDefaults = new ArrayList<>();
    private XBreakpointsDialogState myBreakpointsDialogProperties;

    private long myTime;
    private String myDefaultGroup;

    @Tag("default-breakpoints")
    @AbstractCollection(surroundWithTag = false)
    public List<BreakpointState> getDefaultBreakpoints() {
      return myDefaultBreakpoints;
    }

    @Tag("breakpoints")
    @AbstractCollection(surroundWithTag = false,
                        elementTypes = {BreakpointState.class, LineBreakpointState.class})
    public List<BreakpointState> getBreakpoints() {
      return myBreakpoints;
    }

    @Tag("breakpoints-defaults")
    @AbstractCollection(surroundWithTag = false,
                        elementTypes = {BreakpointState.class, LineBreakpointState.class})
    public List<BreakpointState> getBreakpointsDefaults() {
      return myBreakpointsDefaults;
    }

    @Tag("breakpoints-dialog")
    public XBreakpointsDialogState getBreakpointsDialogProperties() {
      return myBreakpointsDialogProperties;
    }

    public void setBreakpoints(final List<BreakpointState> breakpoints) {
      myBreakpoints = breakpoints;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setDefaultBreakpoints(List<BreakpointState> defaultBreakpoints) {
      myDefaultBreakpoints = defaultBreakpoints;
    }

    public void setBreakpointsDefaults(List<BreakpointState> breakpointsDefaults) {
      myBreakpointsDefaults = breakpointsDefaults;
    }

    public void setBreakpointsDialogProperties(XBreakpointsDialogState breakpointsDialogProperties) {
      myBreakpointsDialogProperties = breakpointsDialogProperties;
    }

    public long getTime() {
      return myTime;
    }

    public void setTime(long time) {
      myTime = time;
    }

    public String getDefaultGroup() {
      return myDefaultGroup;
    }

    public void setDefaultGroup(String defaultGroup) {
      myDefaultGroup = defaultGroup;
    }
  }
}
