// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.BreakpointManagerState;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import kotlinx.coroutines.CoroutineScope;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.util.progress.CancellationUtil.withLockMaybeCancellable;
import static com.intellij.xdebugger.impl.breakpoints.XBreakpointProxyKt.asProxy;
import static com.intellij.xdebugger.impl.frame.XDebugSessionProxy.useFeLineBreakpointProxy;

@ApiStatus.Internal
public final class XBreakpointManagerImpl implements XBreakpointManager {
  private static final Logger LOG = Logger.getInstance(XBreakpointManagerImpl.class);

  private final ReentrantLock myLock = new ReentrantLock();

  private final MultiMap<XBreakpointType, XBreakpointBase<?, ?, ?>> myBreakpoints = MultiMap.createLinkedSet();
  private final Map<XBreakpointType, Set<XBreakpointBase<?, ?, ?>>> myDefaultBreakpoints = new LinkedHashMap<>();
  private final Map<XBreakpointType, BreakpointState> myBreakpointsDefaults = new LinkedHashMap<>();
  private final Set<XBreakpointBase<?, ?, ?>> myAllBreakpoints = new LinkedHashSet<>();
  private final Map<XBreakpointType, EventDispatcher<XBreakpointListener>> myDispatchers = new ConcurrentHashMap<>();
  private volatile XBreakpointsDialogState myBreakpointsDialogSettings;
  private final XLineBreakpointManager myLineBreakpointManager;
  private final Project myProject;
  private final XDebuggerManagerImpl myDebuggerManager;
  private final XDependentBreakpointManager myDependentBreakpointManager;
  @NotNull private final CoroutineScope myCoroutineScope;
  private final BackendBreakpointRequestCounter myRequestCounter = new BackendBreakpointRequestCounter();
  private long myTime;
  private volatile String myDefaultGroup;
  private RemovedBreakpointData myLastRemovedBreakpoint = null;
  private volatile boolean myFirstLoadDone = false;

  public XBreakpointManagerImpl(@NotNull Project project,
                                @NotNull XDebuggerManagerImpl debuggerManager,
                                SimpleMessageBusConnection messageBusConnection,
                                @NotNull CoroutineScope coroutineScope) {
    myProject = project;
    myDebuggerManager = debuggerManager;
    myCoroutineScope = coroutineScope;
    myDependentBreakpointManager = new XDependentBreakpointManager(this, messageBusConnection);
    myLineBreakpointManager = new XLineBreakpointManager(project, coroutineScope, !useFeLineBreakpointProxy());

    XBreakpointType.EXTENSION_POINT_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @SuppressWarnings("unchecked")
      @Override
      public void extensionAdded(@NotNull XBreakpointType type, @NotNull PluginDescriptor pluginDescriptor) {
        //the project may be 'temporarily disposed' in tests if this class was created from a light test
        if (project.isDisposed()) return;
        var breakpoint = createDefaultBreakpoint(type);
        if (breakpoint != null) {
          addBreakpoint(breakpoint, true, false);
        }
      }

      @Override
      public void extensionRemoved(@NotNull XBreakpointType type, @NotNull PluginDescriptor pluginDescriptor) {
        withLockMaybeCancellable(myLock, () -> {
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
        HttpFileSystem.getInstance().addFileListener(this::updateBreakpointInHttpFile, project);
      }
    }
  }

  private final ExecutorService myHttpBreakpointUpdater =
    SequentialTaskExecutor.createSequentialApplicationPoolExecutor("HttpFileSystem breakpoints updater");

  private void updateBreakpointInHttpFile(final VirtualFile file) {
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

  @ApiStatus.Internal
  public @NotNull CoroutineScope getCoroutineScope() {
    return myCoroutineScope;
  }

  @ApiStatus.Internal
  public BackendBreakpointRequestCounter getRequestCounter() {
    return myRequestCounter;
  }

  @Override
  public @NotNull <T extends XBreakpointProperties> XBreakpoint<T> addBreakpoint(final XBreakpointType<XBreakpoint<T>,T> type, final @Nullable T properties) {
    return doAddBreakpoint(type, properties, false);
  }

  public @NotNull <T extends XBreakpointProperties> XBreakpoint<T> addDefaultBreakpoint(final XBreakpointType<XBreakpoint<T>,T> type, final @Nullable T properties) {
    return doAddBreakpoint(type, properties, true);
  }

  private @NotNull <T extends XBreakpointProperties> XBreakpoint<T> doAddBreakpoint(XBreakpointType<XBreakpoint<T>, T> type, @Nullable T properties, boolean defaultBreakpoint) {
    return withLockMaybeCancellable(myLock, () -> {
      XBreakpointBase<?, T, ?> breakpoint = createBreakpoint(type, properties, true, defaultBreakpoint);
      addBreakpoint(breakpoint, defaultBreakpoint, true);
      return breakpoint;
    });
  }

  private <T extends XBreakpointProperties> XBreakpointBase<?, T, ?> createBreakpoint(XBreakpointType<XBreakpoint<T>, T> type,
                                                                                      T properties, final boolean enabled,
                                                                                      boolean defaultBreakpoint) {
    return withLockMaybeCancellable(myLock, () -> {
      BreakpointState state = new BreakpointState(enabled,
                                                             type.getId(),
                                                             defaultBreakpoint ? 0 : ++myTime, type.getDefaultSuspendPolicy());
      getBreakpointDefaults(type).applyDefaults(state);
      state.setGroup(myDefaultGroup);
      return new XBreakpointBase<>(type, this, properties, state);
    });
  }

  private <T extends XBreakpointProperties> void addBreakpoint(final XBreakpointBase<?, T, ?> breakpoint, final boolean defaultBreakpoint,
                                                               boolean initUI) {
    XBreakpointType type = breakpoint.getType();

    withLockMaybeCancellable(myLock, () -> {
      if (defaultBreakpoint) {
        myDefaultBreakpoints.computeIfAbsent(type, k -> new LinkedHashSet<>()).add(breakpoint);
      }
      else {
        myBreakpoints.putValue(type, breakpoint);
        if (initUI) {
          BreakpointsUsageCollector.reportNewBreakpoint(breakpoint, type, getDebuggerManager().getCurrentSession() != null);
        }
      }
      myAllBreakpoints.add(breakpoint);
      if (breakpoint instanceof XLineBreakpointImpl<?> lineBreakpoint) {
        myLineBreakpointManager.registerBreakpoint(asProxy(lineBreakpoint), initUI);
      }
    });
    sendBreakpointEvent(type, listener -> listener.breakpointAdded(breakpoint));
  }

  private @NotNull XBreakpointListener<XBreakpoint<?>> getBreakpointDispatcherMulticaster() {
    //noinspection unchecked
    return myProject.getMessageBus().syncPublisher(XBreakpointListener.TOPIC);
  }

  private void fireBreakpointsChanged(Collection<? extends XBreakpointBase<?, ?, ?>> breakpoints) {
    for (XBreakpointBase<?, ?, ?> breakpoint : breakpoints) {
      fireBreakpointChanged(breakpoint);
    }
  }

  private boolean isRegistered(XBreakpoint<?> breakpoint) {
    return withLockMaybeCancellable(myLock, () -> myAllBreakpoints.contains(breakpoint));
  }

  public void fireBreakpointChanged(XBreakpointBase<?, ?, ?> breakpoint) {
    if (isRegistered(breakpoint)) {
      if (breakpoint instanceof XLineBreakpointImpl<?> lineBreakpoint) {
        myLineBreakpointManager.breakpointChanged(asProxy(lineBreakpoint));
      }
      sendBreakpointEvent(breakpoint.getType(), listener -> listener.breakpointChanged(breakpoint));
    }
  }

  public void fireBreakpointPresentationUpdated(XBreakpoint<?> breakpoint, @Nullable XDebugSession session) {
    if (isRegistered(breakpoint)) {
      sendBreakpointEvent(breakpoint.getType(), listener -> listener.breakpointPresentationUpdated(breakpoint, session));
    }
  }

  @Override
  public void removeBreakpoint(final @NotNull XBreakpoint<?> breakpoint) {
    removeBreakpoints(List.of(breakpoint));
  }

  void removeBreakpoints(@NotNull Collection<? extends XBreakpoint> toRemove) {
    withLockMaybeCancellable(myLock, () -> toRemove.forEach(this::doRemoveBreakpoint));
  }

  public void removeAllBreakpoints() {
    withLockMaybeCancellable(myLock, () -> List.copyOf(myAllBreakpoints).forEach(this::doRemoveBreakpoint));
  }

  @SuppressWarnings("unused")
  public void removeDefaultBreakpoint(final @NotNull XBreakpoint<?> breakpoint) {
    withLockMaybeCancellable(myLock, () -> {
      if (!isDefaultBreakpoint(breakpoint)) {
        throw new IllegalStateException("Trying to remove not default breakpoint " + breakpoint);
      }
      doRemoveBreakpointImpl(breakpoint, true);
    });
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
    XBreakpointBase<?, ?, ?> breakpointBase = (XBreakpointBase<?, ?, ?>)breakpoint;

    withLockMaybeCancellable(myLock, () -> {
      if (isDefaultBreakpoint) {
        Set<XBreakpointBase<?, ?, ?>> typeDefaultBreakpoints = myDefaultBreakpoints.get(breakpoint.getType());
        if (typeDefaultBreakpoints != null) {
          typeDefaultBreakpoints.remove(breakpoint);
        }
      }
      else {
        myBreakpoints.remove(type, breakpointBase);
      }
      myAllBreakpoints.remove(breakpointBase);
      if (breakpointBase instanceof XLineBreakpointImpl<?> lineBreakpoint) {
        myLineBreakpointManager.unregisterBreakpoint(asProxy(lineBreakpoint));
      }
    });

    UIUtil.invokeLaterIfNeeded(() -> breakpointBase.dispose());
    sendBreakpointEvent(type, listener -> listener.breakpointRemoved(breakpoint));
  }

  private void sendBreakpointEvent(XBreakpointType type, Consumer<? super XBreakpointListener<XBreakpoint<?>>> event) {
    if (myFirstLoadDone) {
      EventDispatcher<XBreakpointListener> dispatcher = myDispatchers.get(type);
      if (dispatcher != null) {
        //noinspection unchecked
        XBreakpointListener<XBreakpoint<?>> multicaster = dispatcher.getMulticaster();
        event.accept(multicaster);
      }
      event.accept(getBreakpointDispatcherMulticaster());
    }
  }

  @Override
  public @NotNull <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(final XLineBreakpointType<T> type,
                                                                                         final @NotNull String fileUrl,
                                                                                         final int line,
                                                                                         final @Nullable T properties) {
    return addLineBreakpoint(type, fileUrl, line, properties, false);
  }

  @Override
  public @NotNull <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(final XLineBreakpointType<T> type,
                                                                                         final @NotNull String fileUrl,
                                                                                         final int line,
                                                                                         final @Nullable T properties,
                                                                                         boolean temporary) {
    return addLineBreakpoint(type, fileUrl, line, properties, temporary, true);
  }

  public @NotNull <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(final XLineBreakpointType<T> type,
                                                                                         final @NotNull String fileUrl,
                                                                                         final int line,
                                                                                         final @Nullable T properties,
                                                                                         boolean temporary,
                                                                                         boolean initUI) {
    return withLockMaybeCancellable(myLock, () -> {
      LineBreakpointState state = new LineBreakpointState(true, type.getId(), fileUrl, line, temporary,
                                                               ++myTime, type.getDefaultSuspendPolicy());
      getBreakpointDefaults(type).applyDefaults(state);
      state.setGroup(myDefaultGroup);
      XLineBreakpointImpl<T> breakpoint = new XLineBreakpointImpl<>(type, this, properties,
                                                                    state);
      addBreakpoint(breakpoint, false, initUI);
      return breakpoint;
    });
  }

  @Override
  public XBreakpointBase<?,?,?> @NotNull [] getAllBreakpoints() {
    return withLockMaybeCancellable(myLock, () -> myAllBreakpoints.toArray(new XBreakpointBase[0]));
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(final @NotNull XBreakpointType<B,?> type) {
    return withLockMaybeCancellable(myLock, () -> {
      List<B> result = new ArrayList<>(getDefaultBreakpoints(type));
      result.addAll((Collection<? extends B>)myBreakpoints.get(type));
      return Collections.unmodifiableList(result);
    });
  }

  @Override
  public @NotNull <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull Class<? extends XBreakpointType<B, ?>> typeClass) {
    XBreakpointType<B, ?> type = XDebuggerUtil.getInstance().findBreakpointType(typeClass);
    LOG.assertTrue(type != null, "Unregistered breakpoint type " + typeClass + ", registered: " + Arrays.toString(XBreakpointType.EXTENSION_POINT_NAME.getExtensions()));
    return getBreakpoints(type);
  }

  @Override
  public @NotNull <B extends XBreakpoint<?>> Set<B> getDefaultBreakpoints(@NotNull XBreakpointType<B, ?> type) {
    return withLockMaybeCancellable(myLock, () -> {
      Set<XBreakpointBase<?, ?, ?>> breakpointsSet = myDefaultBreakpoints.get(type);
      if (breakpointsSet == null) {
        return Collections.emptySet();
      }
      //noinspection unchecked
      return (Set<B>)Set.copyOf(breakpointsSet);
    });
  }

  @Override
  public @NotNull <B extends XLineBreakpoint<P>, P extends XBreakpointProperties> Collection<B> findBreakpointsAtLine(@NotNull XLineBreakpointType<P> type,
                                                                                                         @NotNull VirtualFile file,
                                                                                                         int line) {
    return withLockMaybeCancellable(myLock, () -> {
      //noinspection unchecked
      return (Collection<B>)((StreamEx<XLineBreakpoint<P>>)(StreamEx<?>)StreamEx.of(myBreakpoints.get(type))
        .select(XLineBreakpoint.class)
        .filter(b -> b.getFileUrl().equals(file.getUrl()) && b.getLine() == line))
        .toList();
    });
  }

  @Override
  public @Nullable <P extends XBreakpointProperties> XLineBreakpoint<P> findBreakpointAtLine(final @NotNull XLineBreakpointType<P> type,
                                                                                             final @NotNull VirtualFile file,
                                                                                             final int line) {
    return ContainerUtil.getFirstItem(findBreakpointsAtLine(type, file, line));
  }

  @Override
  public boolean isDefaultBreakpoint(@NotNull XBreakpoint<?> breakpoint) {
    return withLockMaybeCancellable(myLock, () ->
      ContainerUtil.exists(myDefaultBreakpoints.values(), s -> s.contains(breakpoint)));
  }

  private <T extends XBreakpointProperties<?>> EventDispatcher<XBreakpointListener> getOrCreateDispatcher(final XBreakpointType<?,T> type) {
    return myDispatchers.computeIfAbsent(type, k -> EventDispatcher.create(XBreakpointListener.class));
  }

  @Override
  public <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(final @NotNull XBreakpointType<B,P> type, final @NotNull XBreakpointListener<B> listener) {
    getOrCreateDispatcher(type).addListener(listener);
  }

  @Override
  public <B extends XBreakpoint<P>, P extends XBreakpointProperties> void removeBreakpointListener(final @NotNull XBreakpointType<B,P> type,
                                                                                                   final @NotNull XBreakpointListener<B> listener) {
    getOrCreateDispatcher(type).removeListener(listener);
  }

  @Override
  public <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(final @NotNull XBreakpointType<B,P> type, final @NotNull XBreakpointListener<B> listener,
                                                                                                final Disposable parentDisposable) {
    getOrCreateDispatcher(type).addListener(listener, parentDisposable);
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
    myLineBreakpointManager.queueBreakpointUpdate(breakpoint,
                                                  () -> ((XBreakpointBase<?, ?, ?>)breakpoint).fireBreakpointPresentationUpdated(null));
  }

  @ApiStatus.Internal
  public @NotNull BreakpointManagerState saveState(@NotNull BreakpointManagerState state) {
    // create default breakpoints map without locking
    Map<XBreakpointType, XBreakpointBase> defaultBreakpointsMap = StreamEx.of(createDefaultBreakpoints()).toMap(XBreakpointBase::getType, Function.identity());

    return withLockMaybeCancellable(myLock, () -> {
      myDependentBreakpointManager.saveState();

      List<BreakpointState> defaultBreakpoints = new SmartList<>();
      for (Set<XBreakpointBase<?, ?, ?>> typeDefaultBreakpoints : myDefaultBreakpoints.values()) {
        if (!ContainerUtil.exists(typeDefaultBreakpoints,
                                  breakpoint -> differsFromDefault(defaultBreakpointsMap, breakpoint.getType(), breakpoint.getState()))) {
          continue;
        }
        for (XBreakpointBase<?, ?, ?> breakpoint : typeDefaultBreakpoints) {
          final BreakpointState breakpointState = breakpoint.getState();
          defaultBreakpoints.add(breakpointState);
        }
      }

      List<BreakpointState> breakpoints = new SmartList<>();
      for (XBreakpointBase breakpoint : myBreakpoints.values()) {
        breakpoints.add(breakpoint.getState());
      }

      List<BreakpointState> breakpointsDefaults = new SmartList<>();
      for (Map.Entry<XBreakpointType, BreakpointState> entry : myBreakpointsDefaults.entrySet()) {
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
    });
  }

  private static <P extends XBreakpointProperties> boolean differsFromDefault(Map<XBreakpointType, XBreakpointBase> defaultBreakpoints,
                                                                              XBreakpointType<?, P> type,
                                                                              BreakpointState state) {
    var defaultBreakpoint = defaultBreakpoints.get(type);
    if (defaultBreakpoint == null) {
      return false;
    }
    return statesAreDifferent(state, defaultBreakpoint.getState(), false);
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

  @ApiStatus.Internal
  public void loadState(@NotNull BreakpointManagerState state) {
    // create default breakpoint without locking
    var defaultBreakpoints = createDefaultBreakpoints();

    // make sure that no RW lock is taken under myLock
    ApplicationManager.getApplication().runReadAction(() -> {
      withLockMaybeCancellable(myLock, () -> {
        myBreakpointsDialogSettings = state.getBreakpointsDialogProperties();

        myAllBreakpoints.clear();
        myDefaultBreakpoints.clear();
        myBreakpointsDefaults.clear();

        state.getDefaultBreakpoints().forEach(breakpointState -> loadBreakpoint(breakpointState, true));

        //noinspection unchecked
        StreamEx.of(defaultBreakpoints)
          .remove(b -> myDefaultBreakpoints.containsKey(b.getType()))
          .forEach(b -> addBreakpoint(b, true, false));

        List.copyOf(myBreakpoints.values()).forEach(this::doRemoveBreakpoint);

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
        myLineBreakpointManager.updateBreakpointsUI();
        myDefaultGroup = state.getDefaultGroup();
        myFirstLoadDone = true;
      });
    });
  }

  public void noStateLoaded() {
    // create default breakpoint without locking
    var defaultBreakpoints = createDefaultBreakpoints();

    withLockMaybeCancellable(myLock, () -> {
      myDefaultBreakpoints.clear();
      //noinspection unchecked
      defaultBreakpoints.forEach(b -> addBreakpoint(b, true, false));
      myFirstLoadDone = true;
    });
  }

  private List<? extends XBreakpointBase> createDefaultBreakpoints() {
    return XBreakpointUtil.breakpointTypes().map(this::createDefaultBreakpoint).nonNull().toList();
  }

  private @Nullable <P extends XBreakpointProperties> XBreakpointBase<?, P, ?> createDefaultBreakpoint(final XBreakpointType<? extends XBreakpoint<P>, P> type) {
    assert !myLock.isHeldByCurrentThread();
    return (XBreakpointBase<?, P, ?>)type.createDefaultBreakpoint(properties -> {
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

  public String getDefaultGroup() {
    return myDefaultGroup;
  }

  public void setDefaultGroup(String defaultGroup) {
    myDefaultGroup = defaultGroup;
  }

  private @Nullable XBreakpointBase<?,?,?> createBreakpoint(final BreakpointState breakpointState) {
    XBreakpointType<?,?> type = XBreakpointUtil.findType(breakpointState.getTypeId());
    if (type == null) {
      LOG.warn("Unknown breakpoint type " + breakpointState.getTypeId());
      return null;
    }
    return XBreakpointUtil.createBreakpoint(type, breakpointState, this);
  }

  public @NotNull BreakpointState getBreakpointDefaults(@NotNull XBreakpointType type) {
    return withLockMaybeCancellable(myLock, () ->
      myBreakpointsDefaults.computeIfAbsent(type, k -> createBreakpointDefaults(type)));
  }

  @Nullable
  <T extends XBreakpointProperties> XLineBreakpoint<T> copyLineBreakpoint(@NotNull XLineBreakpoint<T> source,
                                                                          @NotNull String fileUrl,
                                                                          int line) {
    return withLockMaybeCancellable(myLock, () -> {
      if (!(source instanceof XLineBreakpointImpl<?>)) {
        return null;
      }
      myDependentBreakpointManager.saveState();
      final LineBreakpointState sourceState = ((XLineBreakpointImpl<?>)source).getState();

      Element element = XmlSerializer.serialize(sourceState);
      LineBreakpointState newState =
        element == null ? new LineBreakpointState() : XmlSerializer.deserialize(element, LineBreakpointState.class);
      newState.setLine(line);
      newState.setFileUrl(fileUrl);

      //noinspection unchecked
      XLineBreakpointImpl<T> breakpoint = (XLineBreakpointImpl<T>)createBreakpoint(newState);
      if (breakpoint != null) {
        addBreakpoint(breakpoint, false, true);
        final XBreakpoint<?> masterBreakpoint = myDependentBreakpointManager.getMasterBreakpoint(source);
        if (masterBreakpoint != null) {
          myDependentBreakpointManager.setMasterBreakpoint(breakpoint, masterBreakpoint, sourceState.getDependencyState().isLeaveEnabled());
        }
      }

      return breakpoint;
    });
  }

  private static @NotNull BreakpointState createBreakpointDefaults(@NotNull XBreakpointType type) {
    BreakpointState state = new BreakpointState();
    state.setTypeId(type.getId());
    state.setSuspendPolicy(type.getDefaultSuspendPolicy());
    return state;
  }

  public void rememberRemovedBreakpoint(@NotNull XBreakpointBase breakpoint) {
    myLastRemovedBreakpoint = new RemovedBreakpointData(breakpoint);
  }

  public @Nullable XBreakpointBase getLastRemovedBreakpoint() {
    return myLastRemovedBreakpoint != null ? myLastRemovedBreakpoint.myBreakpoint : null;
  }

  public @Nullable XBreakpoint restoreLastRemovedBreakpoint() {
    // FIXME[inline-bp]: support multiple breakpoints restore
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
