// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.BreakpointManagerState;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public final class XBreakpointManagerImpl implements XBreakpointManager {
  private static final Logger LOG = Logger.getInstance(XBreakpointManagerImpl.class);

  private final MultiMap<XBreakpointType, XBreakpointBase<?, ?, ?>> myBreakpoints = MultiMap.createLinkedSet();
  private final Map<XBreakpointType, Set<XBreakpointBase<?, ?, ?>>> myDefaultBreakpoints = new LinkedHashMap<>();
  private final Map<XBreakpointType, BreakpointState<?, ?, ?>> myBreakpointsDefaults = new LinkedHashMap<>();
  private final Set<XBreakpointBase<?, ?, ?>> myAllBreakpoints = new LinkedHashSet<>();
  private final Map<XBreakpointType, EventDispatcher<XBreakpointListener>> myDispatchers = new HashMap<>();
  private XBreakpointsDialogState myBreakpointsDialogSettings;
  private volatile EventDispatcher<XBreakpointListener> myAllBreakpointsDispatcher;
  private final XLineBreakpointManager myLineBreakpointManager;
  private final Project myProject;
  private final XDebuggerManagerImpl myDebuggerManager;
  private final XDependentBreakpointManager myDependentBreakpointManager;
  private long myTime;
  private String myDefaultGroup;
  private RemovedBreakpointData myLastRemovedBreakpoint = null;
  private volatile boolean myFirstLoadDone = false;

  public XBreakpointManagerImpl(@NotNull Project project, @NotNull XDebuggerManagerImpl debuggerManager, @NotNull MessageBusConnection messageBusConnection) {
    myProject = project;
    myDebuggerManager = debuggerManager;
    myDependentBreakpointManager = new XDependentBreakpointManager(this, messageBusConnection);
    myLineBreakpointManager = new XLineBreakpointManager(project);

    messageBusConnection.subscribe(XBreakpointListener.TOPIC, new XBreakpointListener() {
      @SuppressWarnings("unchecked")
      @Override
      public void breakpointAdded(@NotNull XBreakpoint breakpoint) {
        if (myAllBreakpointsDispatcher != null) {
          myAllBreakpointsDispatcher.getMulticaster().breakpointAdded(breakpoint);
        }
      }

      @SuppressWarnings("unchecked")
      @Override
      public void breakpointRemoved(@NotNull XBreakpoint breakpoint) {
        if (myAllBreakpointsDispatcher != null) {
          myAllBreakpointsDispatcher.getMulticaster().breakpointRemoved(breakpoint);
        }
      }

      @SuppressWarnings("unchecked")
      @Override
      public void breakpointChanged(@NotNull XBreakpoint breakpoint) {
        if (myAllBreakpointsDispatcher != null) {
          myAllBreakpointsDispatcher.getMulticaster().breakpointChanged(breakpoint);
        }
      }

      @SuppressWarnings("unchecked")
      @Override
      public void breakpointPresentationUpdated(@NotNull XBreakpoint breakpoint, @Nullable XDebugSession session) {
        if (myAllBreakpointsDispatcher != null) {
          myAllBreakpointsDispatcher.getMulticaster().breakpointPresentationUpdated(breakpoint, session);
        }
      }
    });

    XBreakpointType.EXTENSION_POINT_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull XBreakpointType type, @NotNull PluginDescriptor pluginDescriptor) {
        //the project may be 'temporarily disposed' in tests if this class was created from a light test
        if (project.isDisposed()) return;
        //noinspection unchecked
        WriteAction.run(() -> addDefaultBreakpoint(type));
      }

      @Override
      public void extensionRemoved(@NotNull XBreakpointType type, @NotNull PluginDescriptor pluginDescriptor) {
        WriteAction.run(() -> {
          //noinspection unchecked
          for (Object b : getBreakpoints(type)) {
            XBreakpoint<?> breakpoint = (XBreakpoint<?>)b;
            doRemoveBreakpointImpl(breakpoint, isDefaultBreakpoint(breakpoint));
          }
          myBreakpointsDefaults.remove(type);
          myDefaultBreakpoints.remove(type);
        });
      }
    }, debuggerManager);
  }

  public void init() {
    Project project = myProject;
    if (!project.isDefault()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        HttpFileSystem.getInstance().addFileListener(this::updateBreakpointInFile, project);
      }
    }
  }

  private final ExecutorService myHttpBreakpointUpdater =
    SequentialTaskExecutor.createSequentialApplicationPoolExecutor("HttpFileSystem breakpoints updater");

  private void updateBreakpointInFile(final VirtualFile file) {
    ReadAction
      .nonBlocking(() -> changedBreakpoints(file))
      .coalesceBy(Pair.create(this, file))
      .expireWith(myProject)
      .finishOnUiThread(ModalityState.defaultModalityState(), this::fireBreakpointsChanged)
      .submit(myHttpBreakpointUpdater);
  }

  private @NotNull Collection<? extends XBreakpointBase<?, ?, ?>> changedBreakpoints(VirtualFile file) {
    Collection<XBreakpointBase<?, ?, ?>> result = new ArrayList<>();
    for (XBreakpointBase<?, ?, ?> breakpoint : getAllBreakpoints()) {
      ProgressManager.checkCanceled();
      XSourcePosition position = breakpoint.getSourcePosition();
      if (position != null && Comparing.equal(position.getFile(), file)) {
        result.add(breakpoint);
      }
    }
    return result;
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
    return doAddBreakpoint(type, properties, false);
  }

  @NotNull
  public <T extends XBreakpointProperties> XBreakpoint<T> addDefaultBreakpoint(final XBreakpointType<XBreakpoint<T>,T> type, @Nullable final T properties) {
    return doAddBreakpoint(type, properties, true);
  }

  @NotNull
  private <T extends XBreakpointProperties> XBreakpoint<T> doAddBreakpoint(XBreakpointType<XBreakpoint<T>, T> type, @Nullable T properties, boolean defaultBreakpoint) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    XBreakpointBase<?, T, ?> breakpoint = createBreakpoint(type, properties, true, defaultBreakpoint);
    addBreakpoint(breakpoint, defaultBreakpoint, true);
    return breakpoint;
  }

  private <T extends XBreakpointProperties> XBreakpointBase<?, T, ?> createBreakpoint(XBreakpointType<XBreakpoint<T>, T> type,
                                                                                      T properties, final boolean enabled,
                                                                                      boolean defaultBreakpoint) {
    BreakpointState<?,T,?> state = new BreakpointState<>(enabled,
                                                         type.getId(),
                                                         defaultBreakpoint ? 0 : ++myTime, type.getDefaultSuspendPolicy());
    getBreakpointDefaults(type).applyDefaults(state);
    state.setGroup(myDefaultGroup);
    return new XBreakpointBase<XBreakpoint<T>,T, BreakpointState<?,T,?>>(type, this, properties, state);
  }

  private <T extends XBreakpointProperties> void addBreakpoint(final XBreakpointBase<?, T, ?> breakpoint, final boolean defaultBreakpoint,
                                                               boolean initUI) {
    XBreakpointType type = breakpoint.getType();
    if (defaultBreakpoint) {
      Set<XBreakpointBase<?, ?, ?>> typeDefaultBreakpoints = myDefaultBreakpoints.computeIfAbsent(type, k -> new LinkedHashSet<>());
      typeDefaultBreakpoints.add(breakpoint);
    }
    else {
      myBreakpoints.putValue(type, breakpoint);
      if (initUI) {
        BreakpointsUsageCollector.reportNewBreakpoint(breakpoint, type, getDebuggerManager().getCurrentSession() != null);
      }
    }
    myAllBreakpoints.add(breakpoint);
    if (breakpoint instanceof XLineBreakpointImpl) {
      myLineBreakpointManager.registerBreakpoint((XLineBreakpointImpl)breakpoint, initUI);
    }
    sendBreakpointEvent(type, listener -> listener.breakpointAdded(breakpoint));
  }

  @NotNull
  private XBreakpointListener<XBreakpoint<?>> getBreakpointDispatcherMulticaster() {
    //noinspection unchecked
    return myProject.getMessageBus().syncPublisher(XBreakpointListener.TOPIC);
  }

  private void fireBreakpointsChanged(Collection<? extends XBreakpointBase<?, ?, ?>> breakpoints) {
    for (XBreakpointBase<?, ?, ?> breakpoint : breakpoints) {
      fireBreakpointChanged(breakpoint);
    }
  }

  public void fireBreakpointChanged(XBreakpointBase<?, ?, ?> breakpoint) {
    if (!myAllBreakpoints.contains(breakpoint)) {
      return;
    }

    if (breakpoint instanceof XLineBreakpointImpl) {
      myLineBreakpointManager.breakpointChanged((XLineBreakpointImpl)breakpoint);
    }
    sendBreakpointEvent(breakpoint.getType(), listener -> listener.breakpointChanged(breakpoint));
  }
  
  public void fireBreakpointPresentationUpdated(XBreakpoint<?> breakpoint, @Nullable XDebugSession session) {
    if (!myAllBreakpoints.contains(breakpoint)) {
      return;
    }
    
    sendBreakpointEvent(breakpoint.getType(), listener -> listener.breakpointPresentationUpdated(breakpoint, session));
  }

  @Override
  public void removeBreakpoint(@NotNull final XBreakpoint<?> breakpoint) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    doRemoveBreakpoint(breakpoint);
  }

  public void removeDefaultBreakpoint(@NotNull final XBreakpoint<?> breakpoint) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (!isDefaultBreakpoint(breakpoint)) {
      throw new IllegalStateException("Trying to remove not default breakpoint " + breakpoint);
    }
    doRemoveBreakpointImpl(breakpoint, true);
  }

  private void doRemoveBreakpoint(XBreakpoint<?> breakpoint) {
    if (isDefaultBreakpoint(breakpoint)) {
      // removing default breakpoint should just disable it
      breakpoint.setEnabled(false);
    }
    else {
      doRemoveBreakpointImpl(breakpoint, false);
    }
  }

  private void doRemoveBreakpointImpl(XBreakpoint<?> breakpoint, boolean isDefaultBreakpoint) {
    XBreakpointType type = breakpoint.getType();
    XBreakpointBase<?,?,?> breakpointBase = (XBreakpointBase<?,?,?>)breakpoint;
    if (isDefaultBreakpoint) {
      Set<XBreakpointBase<?, ?, ?>> typeDefaultBreakpoints = myDefaultBreakpoints.get(breakpoint.getType());
      if (typeDefaultBreakpoints != null) {
        typeDefaultBreakpoints.remove(breakpoint);
      }
    } else {
      myBreakpoints.remove(type, breakpointBase);
    }
    myAllBreakpoints.remove(breakpointBase);
    if (breakpointBase instanceof XLineBreakpointImpl) {
      myLineBreakpointManager.unregisterBreakpoint((XLineBreakpointImpl)breakpointBase);
    }
    breakpointBase.dispose();
    sendBreakpointEvent(type, listener -> listener.breakpointRemoved(breakpoint));
  }

  private void sendBreakpointEvent(XBreakpointType type, Consumer<? super XBreakpointListener<XBreakpoint<?>>> event) {
    if (myFirstLoadDone) {
      EventDispatcher<XBreakpointListener> dispatcher = myDispatchers.get(type);
      if (dispatcher != null) {
        //noinspection unchecked
        XBreakpointListener<XBreakpoint<?>> multicaster = dispatcher.getMulticaster();
        event.consume(multicaster);
      }
      event.consume(getBreakpointDispatcherMulticaster());
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
    return addLineBreakpoint(type, fileUrl, line, properties, temporary, true);
  }

  @NotNull
  public <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(final XLineBreakpointType<T> type,
                                                                                @NotNull final String fileUrl,
                                                                                final int line,
                                                                                @Nullable final T properties,
                                                                                boolean temporary,
                                                                                boolean initUI) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    LineBreakpointState<T> state = new LineBreakpointState<>(true, type.getId(), fileUrl, line, temporary,
                                                             ++myTime, type.getDefaultSuspendPolicy());
    getBreakpointDefaults(type).applyDefaults(state);
    state.setGroup(myDefaultGroup);
    XLineBreakpointImpl<T> breakpoint = new XLineBreakpointImpl<>(type, this, properties,
                                                                  state);
    addBreakpoint(breakpoint, false, initUI);
    return breakpoint;
  }

  @Override
  public XBreakpointBase<?,?,?> @NotNull [] getAllBreakpoints() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myAllBreakpoints.toArray(new XBreakpointBase[0]);
  }

  @Override
  @SuppressWarnings("unchecked")
  @NotNull
  public <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull final XBreakpointType<B,?> type) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    List<B> result = new ArrayList<>(getDefaultBreakpoints(type));
    result.addAll((Collection<? extends B>)myBreakpoints.get(type));
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
  @NotNull
  public <B extends XBreakpoint<?>> Set<B> getDefaultBreakpoints(@NotNull XBreakpointType<B, ?> type) {
    Set<XBreakpointBase<?, ?, ?>> breakpointsSet = myDefaultBreakpoints.get(type);
    if (breakpointsSet == null) {
      return Collections.emptySet();
    }
    //noinspection unchecked
    return breakpointsSet.stream().map(breakpoint -> (B)breakpoint).collect(Collectors.toSet());
  }

  @Override
  @Nullable
  public <P extends XBreakpointProperties> XLineBreakpoint<P> findBreakpointAtLine(@NotNull final XLineBreakpointType<P> type,
                                                                                   @NotNull final VirtualFile file,
                                                                                   final int line) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    //noinspection unchecked
    return StreamEx.of(myBreakpoints.get(type))
      .select(XLineBreakpoint.class)
      .findFirst(b -> b.getFileUrl().equals(file.getUrl()) && b.getLine() == line)
      .orElse(null);
  }

  @Override
  public boolean isDefaultBreakpoint(@NotNull XBreakpoint<?> breakpoint) {
    return ContainerUtil.exists(myDefaultBreakpoints.values(), s -> s.contains(breakpoint));
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
    getDispatcher().addListener(listener);
  }

  @Override
  public void removeBreakpointListener(@NotNull final XBreakpointListener<XBreakpoint<?>> listener) {
    getDispatcher().removeListener(listener);
  }

  private EventDispatcher<XBreakpointListener> getDispatcher() {
    synchronized (myDispatchers) {
      if (myAllBreakpointsDispatcher == null) {
        myAllBreakpointsDispatcher = EventDispatcher.create(XBreakpointListener.class);
      }
      return myAllBreakpointsDispatcher;
    }
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
    myLineBreakpointManager.queueBreakpointUpdate(breakpoint, () -> fireBreakpointPresentationUpdated(breakpoint, null));
  }

  @NotNull
  public BreakpointManagerState saveState(@NotNull BreakpointManagerState state) {
    myDependentBreakpointManager.saveState();

    List<BreakpointState<?, ?, ?>> defaultBreakpoints = new SmartList<>();
    for (Set<XBreakpointBase<?, ?, ?>> typeDefaultBreakpoints : myDefaultBreakpoints.values()) {
      if (!ContainerUtil.exists(typeDefaultBreakpoints, breakpoint -> differsFromDefault(breakpoint.getType(), breakpoint.getState()))) {
        continue;
      }
      for (XBreakpointBase<?, ?, ?> breakpoint : typeDefaultBreakpoints) {
        final BreakpointState breakpointState = breakpoint.getState();
        defaultBreakpoints.add(breakpointState);
      }
    }

    List<BreakpointState<?, ?, ?>> breakpoints = new SmartList<>();
    for (XBreakpointBase<?,?,?> breakpoint : myBreakpoints.values()) {
      breakpoints.add(breakpoint.getState());
    }

    List<BreakpointState<?, ?, ?>> breakpointsDefaults = new SmartList<>();
    for (Map.Entry<XBreakpointType, BreakpointState<?,?,?>> entry : myBreakpointsDefaults.entrySet()) {
      if (statesAreDifferent(entry.getValue(), createBreakpointDefaults(entry.getKey()), false)) {
        breakpointsDefaults.add(entry.getValue());
      }
    }

    state.getDefaultBreakpoints().clear();
    state.getDefaultBreakpoints().addAll(defaultBreakpoints);
    state.getBreakpoints().clear();
    state.getBreakpoints().addAll(breakpoints);
    state.getBreakpointsDefaults().clear();
    state.getBreakpointsDefaults().addAll(breakpointsDefaults);

    state.setBreakpointsDialogProperties(myBreakpointsDialogSettings);
    state.setDefaultGroup(myDefaultGroup);
    return state;
  }

  private <P extends XBreakpointProperties> boolean differsFromDefault(XBreakpointType<?, P> type, BreakpointState state) {
    final XBreakpoint<P> defaultBreakpoint = createDefaultBreakpoint(type);
    if (defaultBreakpoint == null) {
      return false;
    }

    BreakpointState defaultState = ((XBreakpointBase<?, ?, ?>)defaultBreakpoint).getState();
    return statesAreDifferent(state, defaultState, false);
  }

  public static boolean statesAreDifferent(BreakpointState state1, BreakpointState state2, boolean ignoreTimestamp) {
    long timeStamp1 = state1.getTimeStamp();
    long timeStamp2 = state2.getTimeStamp();
    if (ignoreTimestamp) {
      state1.setTimeStamp(timeStamp2);
    }

    Element elem1 = XmlSerializer.serialize(state1);
    Element elem2 = XmlSerializer.serialize(state2);
    boolean res = !JDOMUtil.areElementsEqual(elem1, elem2);

    if (ignoreTimestamp) {
      state1.setTimeStamp(timeStamp1);
    }
    return res;
  }

  public void loadState(@NotNull BreakpointManagerState state) {
    myBreakpointsDialogSettings = state.getBreakpointsDialogProperties();

    myAllBreakpoints.clear();
    myDefaultBreakpoints.clear();
    myBreakpointsDefaults.clear();

    ApplicationManager.getApplication().runReadAction(() -> {
      state.getDefaultBreakpoints().forEach(breakpointState -> loadBreakpoint(breakpointState, true));

      XBreakpointUtil.breakpointTypes().remove(myDefaultBreakpoints::containsKey).forEach(this::addDefaultBreakpoint);

      new ArrayList<>(myBreakpoints.values()).forEach(this::doRemoveBreakpoint);

      ContainerUtil.notNullize(state.getBreakpoints()).forEach(breakpointState -> loadBreakpoint(breakpointState, false));

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
    myDefaultGroup = state.getDefaultGroup();
    myFirstLoadDone = true;
  }

  public void noStateLoaded() {
    myDefaultBreakpoints.clear();
    XBreakpointUtil.breakpointTypes().forEach(this::addDefaultBreakpoint);
    myFirstLoadDone = true;
  }

  private <P extends XBreakpointProperties> void addDefaultBreakpoint(@NotNull XBreakpointType<?, P> type) {
    XBreakpoint<P> breakpoint = createDefaultBreakpoint(type);
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
    myTime = Math.max(myTime, breakpointState.getTimeStamp());
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

    Element element = XmlSerializer.serialize(sourceState);
    LineBreakpointState newState = element == null ? new LineBreakpointState() : XmlSerializer.deserialize(element, LineBreakpointState.class);
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

  public void rememberRemovedBreakpoint(@NotNull XBreakpointBase breakpoint) {
    myLastRemovedBreakpoint = new RemovedBreakpointData(breakpoint);
  }

  @Nullable
  public XBreakpointBase getLastRemovedBreakpoint() {
    return myLastRemovedBreakpoint != null ? myLastRemovedBreakpoint.myBreakpoint : null;
  }

  @Nullable
  public XBreakpoint restoreLastRemovedBreakpoint() {
    if (myLastRemovedBreakpoint != null) {
      XBreakpoint breakpoint = myLastRemovedBreakpoint.restore();
      myLastRemovedBreakpoint = null;
      return breakpoint;
    }
    return null;
  }

  public boolean canRestoreLastRemovedBreakpoint() {
    return myLastRemovedBreakpoint != null && myLastRemovedBreakpoint.isRestorable();
  }

  private final class RemovedBreakpointData {
    private final XBreakpointBase myBreakpoint;
    private final XDependentBreakpointManager.DependenciesData myDependenciesData;

    private RemovedBreakpointData(@NotNull XBreakpointBase breakpoint) {
      myBreakpoint = breakpoint;
      myDependenciesData = myDependentBreakpointManager.new DependenciesData(breakpoint);
    }

    boolean isRestorable() {
      return !(myBreakpoint instanceof XLineBreakpointImpl) || ((XLineBreakpointImpl<?>)myBreakpoint).getFile() != null;
    }

    @Nullable
    XBreakpoint restore() {
      if (myBreakpoint instanceof XLineBreakpointImpl) {
        XLineBreakpointImpl<?> lineBreakpoint = (XLineBreakpointImpl)myBreakpoint;
        VirtualFile file = lineBreakpoint.getFile();
        if (file == null) { // file was deleted
          return null;
        }
        XLineBreakpoint existingBreakpoint = findBreakpointAtLine(lineBreakpoint.getType(), file, lineBreakpoint.getLine());
        if (existingBreakpoint != null) {
          removeBreakpoint(existingBreakpoint);
        }
      }

      XBreakpointBase<?, ?, ?> breakpoint = createBreakpoint(myBreakpoint.getState());
      if (breakpoint != null) {
        addBreakpoint(breakpoint, false, true);
        myDependenciesData.restore(breakpoint);
        return breakpoint;
      }
      return null;
    }
  }
}
