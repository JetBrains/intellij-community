// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.CommonBundle;
import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.ThreadContext;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ApplicationActivationStateManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.idea.AppExitCodes;
import com.intellij.idea.AppMode;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.InstantShutdown;
import com.intellij.openapi.application.ModalityKt;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadActionListener;
import com.intellij.openapi.application.ThreadingSupport;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.application.WriteActionListener;
import com.intellij.openapi.application.WriteIntentReadActionListener;
import com.intellij.openapi.application.WriteLockReacquisitionListener;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.client.ClientAwareComponentManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.impl.ProgressRunner;
import com.intellij.openapi.progress.util.PotemkinProgress;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SuvorovProgress;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.platform.locking.impl.IntelliJLockingUtil;
import com.intellij.platform.locking.impl.NestedLocksThreadingSupport;
import com.intellij.platform.locking.impl.listeners.ErrorHandler;
import com.intellij.platform.locking.impl.listeners.LegacyProgressIndicatorProvider;
import com.intellij.platform.locking.impl.listeners.LockAcquisitionListener;
import com.intellij.psi.util.ReadActionCache;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.BitUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Restarter;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.concurrency.Propagation;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.EDT;
import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function0;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.ide.ShutdownKt.cancelAndJoinBlocking;
import static com.intellij.openapi.application.ModalityKt.asContextElement;
import static com.intellij.openapi.application.RuntimeFlagsKt.getReportInvokeLaterWithoutModality;
import static com.intellij.openapi.application.impl.AppImplKt.rethrowCheckedExceptions;
import static com.intellij.openapi.application.impl.AppImplKt.runnableUnitFunction;
import static com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope;
import static com.intellij.util.concurrency.AppExecutorUtil.propagateContext;
import static com.intellij.util.concurrency.Propagation.isContextAwareComputation;

@SuppressWarnings("UsagesOfObsoleteApi")
@ApiStatus.Internal
public final class ApplicationImpl extends ClientAwareComponentManager implements ApplicationEx {
  private static @NotNull Logger getLogger() {
    return Logger.getInstance(ApplicationImpl.class);
  }

  private final EventDispatcher<ApplicationListener> myDispatcher = EventDispatcher.create(ApplicationListener.class);
  private final WriteActionListener appListenerDispatcherWrapper = new WriteActionListener() {
    @Override
    public void beforeWriteActionStart(@NotNull Class<?> action) {
      ActivityTracker.getInstance().inc();
      myDispatcher.getMulticaster().beforeWriteActionStart(action);
    }

    @Override
    public void writeActionStarted(@NotNull Class<?> action) {
      myDispatcher.getMulticaster().writeActionStarted(action);
    }

    @Override
    public void writeActionFinished(@NotNull Class<?> action) {
      myDispatcher.getMulticaster().writeActionFinished(action);
    }

    @Override
    public void afterWriteActionFinished(@NotNull Class<?> action) {
      otelMonitor.get().writeActionExecuted();
      myDispatcher.getMulticaster().afterWriteActionFinished(action);
    }
  };

  private final ReadActionListener customReadActionListener = new ReadActionListener() {
    @Override
    public void readActionFinished(@NotNull Class<?> action) {
      myReadActionCacheImpl.clear();
      otelMonitor.get().readActionExecuted();
    }

    @Override
    public void fastPathAcquisitionFailed() {
      // Impatient reader not in non-cancellable session will not wait
      if (myImpatientReader.get() && !Cancellation.isInNonCancelableSection()) {
        throw ApplicationUtil.CannotRunReadActionException.create();
      }
    }
  };

  private static final ErrorHandler lockingErrorHandler = (error) -> getLogger().error(error);

  private final boolean myTestModeFlag;
  private final boolean myHeadlessMode;
  private final boolean myCommandLineMode;

  private final boolean myIsInternal;

  // contents modified in write action, read in read action
  private final TransactionGuardImpl myTransactionGuard = new TransactionGuardImpl();

  private final NestedLocksThreadingSupport lock = IntelliJLockingUtil.getGlobalNestedLockingThreadingSupport();

  private final ReadActionCacheImpl myReadActionCacheImpl = new ReadActionCacheImpl();

  private final ThreadLocal<Boolean> myImpatientReader = ThreadLocal.withInitial(() -> false);

  private final long myStartTime = System.currentTimeMillis();
  private boolean mySaveAllowed;
  private volatile boolean myExitInProgress;

  private final @Nullable Disposable myLastDisposable;  // the last to be disposed

  private static final String WAS_EVER_SHOWN = "was.ever.shown";

  private static final LegacyProgressIndicatorProvider myLegacyIndicatorProvider = () -> {
    var indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    return indicator == null ? null : () -> {
      if (indicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
    };
  };

  @SuppressWarnings("Convert2Lambda")
  private final Supplier<OTelReadWriteActionsMonitor> otelMonitor = new SynchronizedClearableLazy<>(new Function0<>() {
    @Override
    public OTelReadWriteActionsMonitor invoke() {
      return new OTelReadWriteActionsMonitor(TelemetryManager.getInstance().getMeter(PlatformScopesKt.EDT));
    }
  });

  @TestOnly
  public ApplicationImpl(@NotNull CoroutineContext testCoroutineContext, boolean isHeadless) {
    super(childScope(GlobalScope.INSTANCE, "Test Application Scope", testCoroutineContext, true));

    Extensions.setRootArea(getExtensionArea());

    registerFakeServices(this);

    myIsInternal = true;
    myTestModeFlag = true;
    myHeadlessMode = isHeadless;
    myCommandLineMode = true;
    mySaveAllowed = false;

    postInit(this);

    myLastDisposable = Disposer.newDisposable();
    // reset back to null only when all components are already disposed
    ApplicationManager.setApplication(this, myLastDisposable);
  }

  public ApplicationImpl(@NotNull CoroutineScope parentScope, boolean isInternal) {
    super(parentScope);

    Extensions.setRootArea(getExtensionArea());

    registerFakeServices(this);

    myIsInternal = isInternal;
    myTestModeFlag = Boolean.getBoolean("idea.is.unit.test");
    myHeadlessMode = AppMode.isHeadless();
    myCommandLineMode = AppMode.isCommandLine();
    if (!myHeadlessMode || SystemProperties.getBooleanProperty("allow.save.application.headless", false)) {
      mySaveAllowed = true;
    }

    myLastDisposable = null;
  }

  private final SynchronizedClearableLazy<IComponentStore> componentStoreValue = new SynchronizedClearableLazy<>(() -> {
    return getService(IComponentStore.class);
  });

  @Override
  public @NotNull IComponentStore getComponentStore() {
    return componentStoreValue.get();
  }

  @TestOnly
  public void componentStoreImplChanged() {
    componentStoreValue.drop();
  }

  private static void registerFakeServices(ApplicationImpl app) {
    app.registerServiceInstance(TransactionGuard.class, app.myTransactionGuard, fakeCorePluginDescriptor);
    app.registerServiceInstance(Application.class, app, fakeCorePluginDescriptor);
    app.registerServiceInstance(ReadActionCache.class, app.myReadActionCacheImpl, fakeCorePluginDescriptor);
  }

  @TestOnly
  @ApiStatus.Internal
  public ThreadingSupport getRwLock() {
    return getThreadingSupport();
  }

  /**
   * Executes a {@code runnable} in an "impatient" mode.
   * In this mode any attempt to call {@link #runReadAction(Runnable)}
   * would fail (i.e., throw {@link ApplicationUtil.CannotRunReadActionException})
   * if there is a pending write action.
   */
  @Override
  public void executeByImpatientReader(@NotNull Runnable runnable) throws ApplicationUtil.CannotRunReadActionException {
    if (EDT.isCurrentThreadEdt()) {
      runnable.run();
      return;
    }

    myImpatientReader.set(true);
    try {
      runnable.run();
    }
    finally {
      myImpatientReader.set(false);
    }
  }

  @Override
  public boolean isInImpatientReader() {
    return myImpatientReader.get();
  }

  @VisibleForTesting
  public void disposeContainer() {
    // NonCancellable will override context Job
    var coroutineContext = ThreadContext.currentThreadContext();
    try (var ignored = Cancellation.withNonCancelableSection()) {
      cancelAndJoinBlocking(this, coroutineContext);
      runWriteAction(() -> {
        startDispose();
        Disposer.dispose(this);
      });
      Disposer.assertIsEmpty();
    }
    catch (Throwable t) {
      logErrorDuringExit("Failed to dispose the container", t);
    }
  }

  @Override
  public boolean holdsReadLock() {
    return getThreadingSupport().isReadLockedByThisThread();
  }

  @Override
  public boolean isInternal() {
    return myIsInternal;
  }

  @Override
  public boolean isEAP() {
    return ApplicationInfoImpl.getShadowInstance().isEAP();
  }

  @Override
  public boolean isUnitTestMode() {
    return myTestModeFlag;
  }

  @Override
  public boolean isHeadlessEnvironment() {
    return myHeadlessMode;
  }

  @Override
  public boolean isCommandLine() {
    return myCommandLineMode;
  }

  @Override
  public boolean isLightEditMode() {
    return AppMode.isLightEdit();
  }

  @Override
  public @NotNull Future<?> executeOnPooledThread(@NotNull Runnable action) {
    @SuppressWarnings("deprecation") var actionDecorated = ClientId.decorateRunnable(action);
    return AppExecutorUtil.getAppExecutorService().submit(new Runnable() {
      @Override
      public void run() {
        if (isDisposed()) {
          return;
        }

        try {
          actionDecorated.run();
        }
        catch (@SuppressWarnings("IncorrectCancellationExceptionHandling") ProcessCanceledException ignored) { }
        catch (Throwable e) {
          getLogger().error(e);
        }
        finally {
          Thread.interrupted(); // reset interrupted status
        }
      }

      @Override
      public String toString() {
        return action.toString();
      }
    });
  }

  @Override
  public @NotNull <T> Future<T> executeOnPooledThread(@SuppressWarnings("BoundedWildcard") @NotNull Callable<T> action) {
    @SuppressWarnings("deprecation") var actionDecorated = ClientId.decorateCallable(action);
    return AppExecutorUtil.getAppExecutorService().submit(new Callable<>() {
      @Override
      public T call() {
        if (isDisposed()) {
          return null;
        }
        try {
          return actionDecorated.call();
        }
        catch (@SuppressWarnings("IncorrectCancellationExceptionHandling") ProcessCanceledException ignored) { }
        catch (Throwable e) {
          getLogger().error(e);
        }
        finally {
          Thread.interrupted();
        }
        return null;
      }

      @Override
      public String toString() {
        return action.toString();
      }
    });
  }

  @Override
  public boolean isDispatchThread() {
    return EDT.isCurrentThreadEdt();
  }

  @Override
  public boolean isWriteIntentLockAcquired() {
    return getThreadingSupport().isWriteIntentReadAccessAllowed();
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable) {
    invokeLater(runnable, getDisposed());
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull Condition<?> expired) {
    var state = getDefaultModalityState();
    if (getReportInvokeLaterWithoutModality() && state == ModalityState.any()) {
      getLogger().error(
        "Application.invokeLater() was called without modality state and default modality state is ANY\n" +
        "Current thread context is: " + ThreadContext.currentThreadContext());
    }
    invokeLater(runnable, state, expired);
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
    invokeLater(runnable, state, getDisposed());
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition<?> expired) {
    final var ctxAware = isContextAwareComputation(runnable);
    // Start from inner layer: transaction guard
    final var guarded = myTransactionGuard.wrapLaterInvocation(runnable, state);
    // Middle layer: lock and modality
    final var locked = wrapWithRunIntendedWriteActionAndModality(guarded, true, ctxAware ? null : state);
    var finalRunnable = locked;
    // Outer layer, optional: context capture & reset
    if (propagateContext()) {
      var captured = Propagation.capturePropagationContext(locked, expired, runnable);
      finalRunnable = captured.getFirst();
      expired = captured.getSecond();
    }
    LaterInvocator.invokeLater(state, expired, true, finalRunnable);
  }

  @ApiStatus.Internal
  @Override
  public void dispatchCoroutineOnEDT(Runnable runnable, ModalityState state, boolean needsWriteIntent) {
    var wrapped = myTransactionGuard.wrapCoroutineInvocation(runnable, state);
    LaterInvocator.invokeLater(state, Conditions.alwaysFalse(), needsWriteIntent, wrapped);
  }

  @Override
  public void dispose() {
    lock.removeErrorHandler();
    lock.removeLegacyIndicatorProvider(myLegacyIndicatorProvider);
    lock.removeWriteActionListener(appListenerDispatcherWrapper);
    lock.removeReadActionListener(customReadActionListener);

    //noinspection deprecation
    myDispatcher.getMulticaster().applicationExiting();

    var componentStore = componentStoreValue.getValueIfInitialized();
    super.dispose();
    if (componentStore != null) {
      try {
        componentStore.release();
      }
      catch (Exception e) {
        getLogger().error(e);
      }
    }

    // FileBasedIndexImpl can schedule some more activities to execute, so, shutdown executor only after service disposing
    AppExecutorUtil.shutdownApplicationScheduledExecutorService();

    if (myLastDisposable == null) {
      ApplicationManager.setApplication(null);
    }
    else {
      Disposer.dispose(myLastDisposable);
    }

    otelMonitor.get().close();
  }

  @Override
  public boolean runProcessWithProgressSynchronously(
    @NotNull Runnable process,
    @NotNull String progressTitle,
    boolean canBeCanceled,
    boolean shouldShowModalWindow,
    @Nullable Project project,
    @Nullable JComponent parentComponent,
    @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText
  ) {
    // disallow running a process in a separate thread from a write-action, or a thread will deadlock trying to acquire the read-lock
    if (isDispatchThread() && isWriteAccessAllowed()) {
      getLogger().debug("Starting process with progress from within write action makes no sense");
      try {
        ProgressManager.getInstance().runProcess(process, new EmptyProgressIndicator());
      }
      catch (@SuppressWarnings("IncorrectCancellationExceptionHandling") ProcessCanceledException ignored) {
        return false; // ok to ignore.
      }
      return true;
    }

    var progress =
      createProgressWindowAsyncIfNeeded(progressTitle, canBeCanceled, shouldShowModalWindow, project, parentComponent, cancelText);

    // Event pumping (`ProgressRunner.modal()`) is not correct without entering the modality (`shouldShowModalWindow == false`),
    // because one of the events might show a dialog scheduled in outer modality,
    // which will start another nested loop and prevent the pumping from exit until the dialog closed (IDEA-307428):
    // - modal progress: `enterModal`;
    // - modal progress: schedule modal dialog to show after 300ms;
    // - modal progress: `pumpEventsForHierarchy`;
    // - one of the events runs `isConditionalModal() && !shouldStartInBackground()` task;
    // - on EDT such tasks are executed synchronously;
    // - task starts nested `pumpEventsForHierarchy` without entering the modality;
    // - nested `pumpEventsForHierarchy` shows a scheduled modal progress dialog;
    // - nested `pumpEventsForHierarchy` cannot finish because the scheduled modal progress dialog runs nested event loop;
    // - modal dialog cannot finish until the task is finished because it's synchronous.
    //
    // Applying `ProgressRunner.modal()` only when `shouldShowModalWindow == true` is a correct solution,
    // but it forces the execution of non-modal synchronous tasks directly on the EDT
    // (see com.intellij.openapi.progress.impl.ProgressRunner.checkIfForceDirectExecNeeded),
    // and clients are not ready for this, they still expect the process Runnable to be executed on a BGT.
    //
    // On the other hand, synchronous execution of background tasks on EDT happens for headless tasks,
    // and it should still pump the EDT without entering the modality state (IDEA-241785).
    // In tests and headless mode, there is a modal progress dialog, so IDEA-307428 should not be possible in tests.
    //
    // Instead, IDEA-307428 is fixed by ensuring the new modality state for non-headless synchronous EDT tasks
    // (see `CoreProgressManager.runProcessWithProgressSynchronously(Task)`),
    // so that the scheduled outer modal progress dialog cannot be shown from inside the nested `pumpEventsForHierarchy`.
    ProgressRunner<?> progressRunner = new ProgressRunner<>(process)
      .sync()
      .onThread(ProgressRunner.ThreadToUse.POOLED)
      .modal()
      .withProgress(progress);
    progressRunner = !shouldShowModalWindow && isHeadlessEnvironment() && !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()
                     ? progressRunner.fakeModal()
                     : progressRunner;

    var result = progressRunner.submitAndGet();

    var exception = result.getThrowable();
    if (!(exception instanceof ProcessCanceledException)) {
      ExceptionUtil.rethrowUnchecked(exception);
    }
    return !result.isCanceled();
  }


  @Override
  public void invokeAndWaitRelaxed(@NotNull Runnable runnable, @NotNull ModalityState state) {
    doInvokeAndWait(runnable, state, false);
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState state) {
    doInvokeAndWait(runnable, state, true);
  }

  public void doInvokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState state, boolean wrapWithLocks) {
    if (EDT.isCurrentThreadEdt()) {
      if (wrapWithLocks) {
        runIntendedWriteActionOnCurrentThread(runnable);
      } else {
        runnable.run();
      }
      return;
    }

    if (isWriteAccessAllowed()) {
      throw new IllegalStateException("Calling invokeAndWait from write-action leads to deadlock.");
    }

    if (holdsReadLock()) {
      throw new IllegalStateException("Calling invokeAndWait from read-action leads to possible deadlock.");
    }

    final var ctxAware = isContextAwareComputation(runnable);
    // Start from inner layer: transaction guard
    final var guarded = myTransactionGuard.wrapLaterInvocation(runnable, state);
    // Middle layer: lock and modality
    final var locked = wrapWithRunIntendedWriteActionAndModality(guarded, false, ctxAware ? null : state);
    // Outer layer context capture & reset
    final var finalRunnable = AppImplKt.rethrowExceptions(AppScheduledExecutorService::captureContextCancellationForRunnableThatDoesNotOutliveContextScope, locked);

    LaterInvocator.invokeAndWait(state, wrapWithLocks, finalRunnable);
  }

  private @NotNull Runnable wrapWithRunIntendedWriteActionAndModality(@NotNull Runnable runnable,
                                                                      boolean wrapWithLocks,
                                                                      @Nullable ModalityState modalityState) {
    if (modalityState == null && wrapWithLocks) {
      return new Runnable() {
        @Override
        public void run() {
          runIntendedWriteActionOnCurrentThread(runnable);
        }

        @Override
        public String toString() {
          return runnable.toString();
        }
      };
    }
    else if (modalityState == null) {
      // wrapWithLocks == false
      return runnable;
    }
    else {
      // modalityState != null
      return new Runnable() {
        @Override
        public void run() {
          ThreadContext.installThreadContext(ThreadContext.currentThreadContext().plus(asContextElement(modalityState)), true, () -> {
            if (wrapWithLocks) {
              runIntendedWriteActionOnCurrentThread(runnable);
            }
            else {
              runnable.run();
            }
            return Unit.INSTANCE;
          });
        }

        @Override
        public String toString() {
          return runnable.toString();
        }
      };
    }
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable) throws ProcessCanceledException {
    invokeAndWait(runnable, getDefaultModalityState());
  }

  @Override
  public @NotNull ModalityState getCurrentModalityState() {
    return LaterInvocator.getCurrentModalityState();
  }

  @Override
  public @NotNull ModalityState getModalityStateForComponent(@NotNull Component c) {
    var window = ComponentUtil.getWindow(c);
    if (window == null) return getNoneModalityState(); //?
    return LaterInvocator.modalityStateForWindow(window);
  }

  @Override
  public @NotNull ModalityState getAnyModalityState() {
    return AnyModalityState.ANY;
  }

  @Override
  public @NotNull ModalityState getDefaultModalityState() {
    return isDispatchThread() ? getCurrentModalityState() : ModalityKt.defaultModalityImpl();
  }

  @Override
  public @NotNull ModalityState getNoneModalityState() {
    return ModalityState.nonModal();
  }

  @Override
  public long getStartTime() {
    return myStartTime;
  }

  @Override
  public long getIdleTime() {
    return IdeEventQueue.getInstance().getIdleTime();
  }

  @Override
  public void restart(boolean exitConfirmed) {
    restart(exitConfirmed, false);
  }

  @Override
  public void restart(boolean exitConfirmed, boolean elevate) {
    var flags = SAVE;
    if (exitConfirmed) {
      flags |= EXIT_CONFIRMED;
    }
    if (elevate) {
      flags |= ELEVATE;
    }
    restart(flags, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  /**
   * There are two ways we can get an exit notification.
   * 1. From user input i.e., ExitAction
   * 2. From the native system.
   * We should not process any quit notifications if we are handling another one
   * <p>
   * Note: there are possible scenarios when we get a quit notification at a moment when another
   * quit message is shown. In that case, showing multiple messages sounds contra-intuitive as well
   */
  @Override
  public void exit(boolean force, boolean exitConfirmed, boolean restart, int exitCode) {
    var flags = SAVE;
    if (force) {
      flags |= FORCE_EXIT;
    }
    if (exitConfirmed) {
      flags |= EXIT_CONFIRMED;
    }
    exit(flags, restart, ArrayUtilRt.EMPTY_STRING_ARRAY, exitCode);
  }

  @Override
  public void exit(boolean force, boolean exitConfirmed, boolean restart) {
    exit(force, exitConfirmed, restart, 0);
  }

  @Override
  public void restart(int flags, String @NotNull [] beforeRestart) {
    exit(flags, true, beforeRestart, 0);
  }

  @Override
  public void exit(int flags, int exitCode) {
    exit(flags, false, ArrayUtil.EMPTY_STRING_ARRAY, exitCode);
  }

  @Override
  public void exit(int flags) {
    exit(flags, false, ArrayUtil.EMPTY_STRING_ARRAY, 0);
  }

  private void exit(int flags, boolean restart, String @NotNull [] beforeRestart, int exitCode) {
    if (!BitUtil.isSet(flags, FORCE_EXIT) &&
        (myExitInProgress || (!BitUtil.isSet(flags, EXIT_CONFIRMED) && getDefaultModalityState() != ModalityState.nonModal()))) {
      return;
    }

    myExitInProgress = true;
    if (isDispatchThread()) {
      doExit(flags, restart, beforeRestart, exitCode);
    }
    else {
      invokeLater(() -> doExit(flags, restart, beforeRestart, exitCode), ModalityState.nonModal());
    }
  }

  @Override
  public boolean isExitInProgress() {
    return myExitInProgress;
  }

  private void doExit(int flags, boolean restart, String @NotNull [] beforeRestart, int exitCode) {
    Integer actualExitCode = null;
    try {
      actualExitCode = destructApplication(flags, restart, beforeRestart, exitCode);
    }
    catch (Throwable err) {
      logErrorDuringExit("Failed to destruct the application", err);
    }
    finally {
      if (actualExitCode != null) {
        System.exit(actualExitCode);
      }
    }
  }

  private @Nullable Integer destructApplication(int flags, boolean restart, String @NotNull [] beforeRestart, int exitCode) {
    var tracer = TelemetryManager.getInstance().getTracer(new Scope("exitApp", null));
    var exitSpan = tracer.spanBuilder("application.exit").startSpan();
    var force = BitUtil.isSet(flags, FORCE_EXIT);
    try (var scope = exitSpan.makeCurrent()) {
      if (!force && !confirmExitIfNeeded(BitUtil.isSet(flags, EXIT_CONFIRMED))) {
        return null;
      }

      var canRestart = restart && Restarter.isSupported();  // `Restarter` might load a service; calling before everything's disposed

      var lifecycleListener = getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
      lifecycleListener.appClosing();

      if (!force && !canExit(restart)) {
        return null;
      }

      try {
        stopServicePreloading();
      }
      catch (Throwable t) {
        logErrorDuringExit("Failed to stop service preloading", t);
      }

      try {
        lifecycleListener.beforeAppWillBeClosed(restart);
      }
      catch (Throwable t) {
        logErrorDuringExit("Failed to invoke lifecycle listeners", t);
      }

      if (BitUtil.isSet(flags, SAVE)) {
        try {
          TraceKt.use(tracer.spanBuilder("saveSettingsOnExit"),
                      __ -> SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(this));
        }
        catch (Throwable e) {
          logErrorDuringExit("Failed to save settings", e);
        }
      }

      try {
        if (isInstantShutdownPossible()) {
          for (var frame : Frame.getFrames()) {
            frame.setVisible(false);
          }
        }
      }
      catch (Throwable e) {
        logErrorDuringExit("Failed to instant shutdown the frames", e);
      }

      try {
        lifecycleListener.appWillBeClosed(restart);
      }
      catch (Throwable t) {
        logErrorDuringExit("Failed to invoke lifecycle listeners", t);
      }

      try {
        LifecycleUsageTriggerCollector.onIdeClose(restart);
      }
      catch (Throwable e) {
        logErrorDuringExit("Failed to notify usage collector", e);
      }

      var success = true;
      var manager = ProjectManagerEx.getInstanceExIfCreated();
      if (manager != null) {
        try {
          boolean projectsClosedSuccessfully = TraceKt.use(tracer.spanBuilder("disposeProjects"), __ -> {
            return manager.closeAndDisposeAllProjects(!force);
          });
          if (!projectsClosedSuccessfully) {
            success = false;
          }
        }
        catch (Throwable e) {
          logErrorDuringExit("Failed to close and dispose all projects", e);
        }
      }

      try {
        // can't report OT after the container disposal
        scope.close();
        exitSpan.end();
      }
      catch (Throwable e) {
        logErrorDuringExit("Failed to report the telemetry", e);
      }

      disposeContainer();

      if (!success || isUnitTestMode()) {
        return null;
      }

      IdeEventQueue.applicationClose();

      if (Boolean.getBoolean("idea.test.guimode")) {
        //noinspection TestOnlyProblems
        ShutDownTracker.getInstance().run();
        return null;
      }

      IdeaLogger.dropFrequentExceptionsCaches();
      if (restart) {
        if (canRestart) {
          try {
            Restarter.scheduleRestart(BitUtil.isSet(flags, ELEVATE), List.of(beforeRestart));
          }
          catch (Throwable t) {
            logErrorDuringExit("Failed to restart the application", t);
          }
        }
        else {
          getLogger().warn("Restart not supported; exiting");
        }
        if (exitCode == 0) {
          exitCode = AppExitCodes.RESTART_FAILED;
        }
      }
      return exitCode;
    }
    finally {
      exitSpan.end();
      myExitInProgress = false;
    }
  }

  private static void logErrorDuringExit(String message, Throwable err) {
    // A special class to bypass problems with logging ControlFlowException.
    class ApplicationExitException extends RuntimeException {
      ApplicationExitException(Throwable cause) {
        super(cause);
      }
    }
    try {
      getLogger().error(message, new ApplicationExitException(err));
    }
    catch (Throwable ignored) {
      // Do nothing.
    }
  }

  private static boolean isInstantShutdownPossible() {
    return InstantShutdown.isAllowed() && !ProgressManager.getInstance().hasProgressIndicator();
  }

  private @NotNull CompletableFuture<@NotNull ProgressWindow> createProgressWindowAsyncIfNeeded(
    @NotNull @NlsContexts.ProgressTitle String progressTitle,
    boolean canBeCanceled,
    boolean shouldShowModalWindow,
    @Nullable Project project,
    @Nullable JComponent parentComponent,
    @Nullable @NlsContexts.Button String cancelText
  ) {
    if (EDT.isCurrentThreadEdt()) {
      return CompletableFuture.completedFuture(
        createProgressWindow(progressTitle, canBeCanceled, shouldShowModalWindow, project, parentComponent, cancelText));
    }
    else {
      return CompletableFuture.supplyAsync(
        () -> createProgressWindow(progressTitle, canBeCanceled, shouldShowModalWindow, project, parentComponent, cancelText),
        this::invokeLater);
    }
  }

  private ProgressWindow createProgressWindow(
    @NlsContexts.ProgressTitle String progressTitle,
    boolean canBeCanceled,
    boolean shouldShowModalWindow,
    @Nullable Project project,
    @Nullable JComponent parentComponent,
    @Nullable @NlsContexts.Button String cancelText
  ) {
    var progress = new ProgressWindow(canBeCanceled, !shouldShowModalWindow, project, parentComponent, cancelText);
    Disposer.register(this, progress);  // to dispose the progress even when `ProgressManager#runProcess` is not called
    progress.setTitle(progressTitle);
    return progress;
  }

  private static boolean confirmExitIfNeeded(boolean exitConfirmed) {
    var hasUnsafeBgTasks = ProgressManager.getInstance().hasUnsafeProgressIndicator();
    if (exitConfirmed && !hasUnsafeBgTasks) {
      return true;
    }

    var option = new DoNotAskOption() {
      @Override
      public boolean isToBeShown() {
        return GeneralSettings.getInstance().isConfirmExit() && ProjectManager.getInstance().getOpenProjects().length > 0;
      }

      @Override
      public void setToBeShown(boolean value, int exitCode) {
        GeneralSettings.getInstance().setConfirmExit(value);
      }

      @Override
      public boolean canBeHidden() {
        return !hasUnsafeBgTasks;
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return false;
      }

      @Override
      public @NotNull String getDoNotShowMessage() {
        return IdeBundle.message("do.not.ask.me.again");
      }
    };

    if (!hasUnsafeBgTasks && !option.isToBeShown()) {
      return true;
    }

    if (hasUnsafeBgTasks && ApplicationManager.getApplication().isHeadlessEnvironment()) {
      getLogger().error("Headless application has been completed but background tasks are still running! Application will be terminated.",
                        new Attachment("stacktrace.txt", ThreadDumper.dumpThreadsToString()));
      return true;
    }

    var alreadyGone = new AtomicBoolean(false);
    if (hasUnsafeBgTasks) {
      var dialogRemover = Messages.createMessageDialogRemover(null);
      var task = new Runnable() {
        @Override
        public void run() {
          if (alreadyGone.get()) return;
          if (!ProgressManager.getInstance().hasUnsafeProgressIndicator()) {
            alreadyGone.set(true);
            dialogRemover.run();
          }
          else {
            AppExecutorUtil.getAppScheduledExecutorService().schedule(this, 1, TimeUnit.SECONDS);
          }
        }
      };
      AppExecutorUtil.getAppScheduledExecutorService().schedule(task, 1, TimeUnit.SECONDS);
    }

    var message = ApplicationBundle.message(hasUnsafeBgTasks ? "exit.confirm.prompt.tasks" : "exit.confirm.prompt");
    exitConfirmed = MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"), message)
      .yesText(ApplicationBundle.message("command.exit"))
      .noText(CommonBundle.getCancelButtonText())
      .doNotAsk(option)
      .guessWindowAndAsk();
    if (alreadyGone.getAndSet(true)) {
      if (!option.isToBeShown()) {
        return true;
      }
      exitConfirmed =
        MessageDialogBuilder.okCancel(ApplicationBundle.message("exit.confirm.title"), ApplicationBundle.message("exit.confirm.prompt"))
          .yesText(ApplicationBundle.message("command.exit"))
          .doNotAsk(option)
          .guessWindowAndAsk();
    }
    return exitConfirmed;
  }

  private boolean canExit(boolean restart) {
    for (var applicationListener : myDispatcher.getListeners()) {
      if (restart && !applicationListener.canRestartApplication()
          || !restart && !applicationListener.canExitApplication()) {
        return false;
      }
    }

    var projectManager = ProjectManagerEx.getInstanceExIfCreated();
    if (projectManager == null) {
      return true;
    }

    var projects = projectManager.getOpenProjects();
    for (var project : projects) {
      if (!projectManager.canClose(project)) {
        return false;
      }
    }

    return true;
  }

  @ApiStatus.Internal
  public boolean isCurrentWriteOnEdt() {
    return false;
  }

  @Override
  public void runIntendedWriteActionOnCurrentThread(@NotNull Runnable action) {
    getThreadingSupport().runWriteIntentReadAction(runnableUnitFunction(action));
  }

  @Override
  public void runReadAction(@NotNull Runnable action) {
    getThreadingSupport().runReadAction(runnableUnitFunction(action));
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computation) {
    return getThreadingSupport().runReadAction(computation::compute);
  }

  @Override
  public <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    return getThreadingSupport().runReadAction(rethrowCheckedExceptions(computation));
  }

  @Override
  @ApiStatus.Experimental
  public boolean runWriteActionWithNonCancellableProgressInDispatchThread(
    @NotNull @NlsContexts.ProgressTitle String title,
    @Nullable Project project,
    @Nullable JComponent parentComponent,
    @NotNull Consumer<? super ProgressIndicator> action
  ) {
    return runEdtProgressWriteAction(title, project, parentComponent, null, action);
  }

  @Override
  @ApiStatus.Experimental
  public boolean runWriteActionWithCancellableProgressInDispatchThread(
    @NotNull @NlsContexts.ProgressTitle String title,
    @Nullable Project project,
    @Nullable JComponent parentComponent,
    @NotNull Consumer<? super ProgressIndicator> action
  ) {
    return runEdtProgressWriteAction(title, project, parentComponent, IdeBundle.message("action.stop"), action);
  }

  private boolean runEdtProgressWriteAction(
    @NlsContexts.ProgressTitle String title,
    @Nullable Project project,
    @Nullable JComponent parentComponent,
    @Nls(capitalization = Nls.Capitalization.Title) @Nullable String cancelText,
    @NotNull Consumer<? super @Nullable ProgressIndicator> action
  ) {
    return lock.runWriteActionBlocking(() -> {
      if (JBUIScale.isInitialized()) {
        @SuppressWarnings("DialogTitleCapitalization") var indicator = new PotemkinProgress(title, project, parentComponent, cancelText);
        indicator.runInSwingThread(() -> action.accept(indicator));
        return !indicator.isCanceled();
      }
      else {
        var indicator = new EmptyProgressIndicator();
        ProgressManager.getInstance().runProcess(() -> {
          action.accept(indicator);
        }, indicator);
        return !indicator.isCanceled();
      }
    });
  }

  @Override
  public void runWriteAction(@NotNull Runnable action) {
    incrementBackgroundWriteActionCounter();
    try {
      getThreadingSupport().runWriteActionBlocking(runnableUnitFunction(action));
    }
    finally {
      decrementBackgroundWriteActionCounter();
    }
  }

  @Override
  public <T> T runWriteAction(@NotNull Computable<T> computation) {
    incrementBackgroundWriteActionCounter();
    try {
      return getThreadingSupport().runWriteActionBlocking(computation::compute);
    }
    finally {
      decrementBackgroundWriteActionCounter();
    }
  }

  @Override
  public <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    incrementBackgroundWriteActionCounter();
    try {
      return getThreadingSupport().runWriteActionBlocking(rethrowCheckedExceptions(computation));
    }
    finally {
      decrementBackgroundWriteActionCounter();
    }
  }

  private static void incrementBackgroundWriteActionCounter() {
    if (EDT.isCurrentThreadEdt()) {
      return;
    }
    InternalThreading.incrementBackgroundWriteActionCount();
  }


  private static void decrementBackgroundWriteActionCounter() {
    if (EDT.isCurrentThreadEdt()) {
      return;
    }
    InternalThreading.decrementBackgroundWriteActionCount();
  }

  @Override
  public boolean hasWriteAction(@NotNull Class<?> actionClass) {
    ThreadingAssertions.softAssertReadAccess();
    @SuppressWarnings("deprecation") var serviceClass = WriteActionPresenceService.class;
    return Objects.requireNonNull(getService(serviceClass)).hasWriteAction(actionClass);
  }

  @Override
  public <T, E extends Throwable> T runWriteIntentReadAction(@NotNull ThrowableComputable<T, E> computation) {
    return getThreadingSupport().runWriteIntentReadAction(rethrowCheckedExceptions(computation));
  }

  @Override
  public void assertReadAccessAllowed() {
    ThreadingAssertions.softAssertReadAccess();
  }

  @Override
  public void assertReadAccessNotAllowed() {
    ThreadingAssertions.assertNoReadAccess();
  }

  @Override
  public boolean isReadAccessAllowed() {
    return getThreadingSupport().isReadAccessAllowed();
  }

  @Override
  public void assertIsDispatchThread() {
    ThreadingAssertions.assertEventDispatchThread();
  }

  @Override
  public void assertIsNonDispatchThread() {
    ThreadingAssertions.assertBackgroundThread();
  }

  @Override
  public void assertWriteIntentLockAcquired() {
    ThreadingAssertions.assertWriteIntentReadAccess();
  }

  @Override
  public void assertIsDispatchThread(@Nullable JComponent component) {
    if (component == null) return;

    if (isDispatchThread()) {
      return;
    }

    if (Boolean.TRUE.equals(component.getClientProperty(WAS_EVER_SHOWN))) {
      ThreadingAssertions.assertEventDispatchThread();
    }
    else if (component.getRootPane() != null) {
      component.putClientProperty(WAS_EVER_SHOWN, Boolean.TRUE);
      ThreadingAssertions.assertEventDispatchThread();
    }
  }

  @Override
  public boolean tryRunReadAction(@NotNull Runnable action) {
    return getThreadingSupport().tryRunReadAction(runnableUnitFunction(action));
  }

  @Override
  public boolean isActive() {
    if (isHeadlessEnvironment()) {
      return true;
    }

    if (isDisposed()) {
      return false;
    }

    var activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (activeWindow != null) {
      ApplicationActivationStateManager.INSTANCE.updateState(this, activeWindow);
    }

    return ApplicationActivationStateManager.INSTANCE.isActive();
  }

  @Override
  public boolean isWriteActionPending() {
    return getThreadingSupport().isWriteActionPending();
  }

  @Override
  public boolean isBackgroundWriteActionRunningOrPending() {
    return InternalThreading.isBackgroundWriteActionRunning();
  }

  @Override
  public boolean isWriteAccessAllowed() {
    return getThreadingSupport().isWriteAccessAllowed();
  }

  @Override
  public void assertWriteAccessAllowed() {
    ThreadingAssertions.assertWriteAccess();
  }

  /**
   * If called inside a write-action, executes the given code under modal progress with write-lock released (e.g., to allow for read-action parallelization).
   * It's the caller's responsibility to invoke this method only when the model is in an internally consistent state,
   * so that background threads with read actions don't see half-baked PSI/VFS/etc. The runnable may perform write-actions itself;
   * callers should be ready for those.
   */
  public void executeSuspendingWriteAction(
    @Nullable Project project,
    @NotNull @NlsContexts.DialogTitle String title,
    @NotNull Runnable runnable
  ) {
    ThreadingAssertions.assertWriteIntentReadAccess();
    getThreadingSupport().executeSuspendingWriteAction(runnableUnitFunction(
      () -> ProgressManager.getInstance().run(new Task.Modal(project, title, false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          runnable.run();
        }
      })
    ));
  }

  @Override
  public boolean isWriteActionInProgress() {
    return getThreadingSupport().isWriteActionInProgress();
  }

  @Override
  public void addApplicationListener(@NotNull ApplicationListener l) {
    myDispatcher.addListener(l);
  }

  @Override
  public void addApplicationListener(@NotNull ApplicationListener l, @NotNull Disposable parent) {
    myDispatcher.addListener(l, parent);
  }

  @Override
  public void removeApplicationListener(@NotNull ApplicationListener l) {
    myDispatcher.removeListener(l);
  }

  @Override
  public void saveSettings() {
    if (mySaveAllowed) {
      StoreUtil.saveSettings(this, false);
    }
  }

  @Override
  public void saveAll() {
    StoreUtil.saveDocumentsAndProjectsAndApp(false);
  }

  @Override
  public void setSaveAllowed(boolean value) {
    mySaveAllowed = value;
  }

  @Override
  public boolean isSaveAllowed() {
    return mySaveAllowed;
  }

  @Override
  public boolean isRestartCapable() {
    return Restarter.isSupported();
  }

  @Override
  public String toString() {
    var writeActionPending = isWriteActionPending();
    var writeActionInProgress = isWriteActionInProgress();
    var writeAccessAllowed =isWriteAccessAllowed();
    return "Application"
           + (containerState.get() == ContainerState.COMPONENT_CREATED ? "" : " (containerState " + getContainerStateName() + ") ")
           + (isUnitTestMode() ? " (unit test)" : "")
           + (isInternal() ? " (internal)" : "")
           + (isHeadlessEnvironment() ? " (headless)" : "")
           + (isCommandLine() ? " (command line)" : "")
           + (writeActionPending || writeActionInProgress || writeAccessAllowed ? " (WA" +
                                                                                  (writeActionPending ? " pending" : "") +
                                                                                  (writeActionInProgress ? " inProgress" : "") +
                                                                                  (writeAccessAllowed ? " allowed" : "") +
                                                                                  ")" : "")
           + (isReadAccessAllowed() ? " (RA allowed)" : "")
           + (isInImpatientReader() ? " (impatient reader)" : "")
           + (isExitInProgress() ? " (exit in progress)" : "")
      ;
  }

  @Override
  public @NotNull String activityNamePrefix() {
    return "app ";
  }

  @Override
  protected @NotNull ContainerDescriptor getContainerDescriptor(@NotNull IdeaPluginDescriptorImpl pluginDescriptor) {
    return pluginDescriptor.getAppContainerDescriptor();
  }

  @Override
  protected void logMessageBusDelivery(@NotNull Topic<?> topic, @NotNull String messageName, @NotNull Object handler, long duration) {
    super.logMessageBusDelivery(topic, messageName, handler, duration);

    if (topic == ProjectManager.TOPIC) {
      var start = System.nanoTime() - duration;
      StartUpMeasurer.addCompletedActivity(start, handler.getClass(), ActivityCategory.PROJECT_OPEN_HANDLER, null, StartUpMeasurer.MEASURE_THRESHOLD);
    }
    else if (topic == VirtualFileManager.VFS_CHANGES) {
      if (TimeUnit.NANOSECONDS.toMillis(duration) > 50) {
        getLogger().info(String.format(
          "LONG VFS PROCESSING. Topic=%s, offender=%s, message=%s, time=%dms",
          topic.getDisplayName(), handler.getClass(), messageName, TimeUnit.NANOSECONDS.toMillis(duration)));
      }
    }
  }

  @TestOnly
  @ApiStatus.Internal
  public void disableEventsUntil(@NotNull Disposable disposable) {
    myDispatcher.neuterMultiCasterWhilePerformanceTestIsRunningUntil(disposable);
  }

  @Override
  public boolean isComponentCreated() {
    return containerState.get().compareTo(ContainerState.COMPONENT_CREATED) >= 0;
  }

  @ApiStatus.Internal
  public static void postInit(@NotNull ApplicationImpl app) {
    var reported = new AtomicBoolean();
    IdeEventQueue.getInstance().addPostprocessor(e -> {
      if (app.isWriteAccessAllowed() && reported.compareAndSet(false, true)) {
        getLogger().error("AWT events are not allowed inside write action: " + e);
      }
      return true;
    }, app.getCoroutineScope());

    app.lock.addReadActionListener(app.customReadActionListener);
    app.lock.addWriteActionListener(app.appListenerDispatcherWrapper);
    app.lock.setLegacyIndicatorProvider(myLegacyIndicatorProvider);
    app.lock.setErrorHandler(lockingErrorHandler);
    SwingUtilities.invokeLater(() -> {
      SuvorovProgress.INSTANCE.init(app);
      app.lock.setLockAcquisitionInterceptor((deferred) -> {
        SuvorovProgress.dispatchEventsUntilComputationCompletes(deferred);
        return Unit.INSTANCE;
      });
    });

    app.addApplicationListener(new ApplicationListener() {
      @Override
      public void afterWriteActionFinished(@NotNull Object action) {
        reported.set(false);
      }
    }, app);
    if (app.isInternal() || app.isUnitTestMode()) {
      ContainerUtil.Options.RETURN_REALLY_UNMODIFIABLE_COLLECTION_FROM_METHODS_MARKED_UNMODIFIABLE = true;
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void flushNativeEventQueue() {
    IdeEventQueue.getInstance().flushNativeEventQueue();
  }

  @Override
  public void addWriteActionListener(@NotNull WriteActionListener listener, @NotNull Disposable parentDisposable) {
    lock.addWriteActionListener(listener);
    Disposer.register(parentDisposable, () -> lock.removeWriteActionListener(listener));
  }

  @Override
  public void addReadActionListener(@NotNull ReadActionListener listener, @NotNull Disposable parentDisposable) {
    lock.addReadActionListener(listener);
    Disposer.register(parentDisposable, () -> lock.removeReadActionListener(listener));
  }

  @Override
  public void addWriteIntentReadActionListener(@NotNull WriteIntentReadActionListener listener, @NotNull Disposable parentDisposable) {
    lock.addWriteIntentReadActionListener(listener);
    Disposer.register(parentDisposable, () -> lock.removeWriteIntentReadActionListener(listener));
  }

  public void addLockAcquisitionListener(@NotNull LockAcquisitionListener<?> listener, @NotNull Disposable parentDisposable) {
    lock.setLockAcquisitionListener(listener);
    Disposer.register(parentDisposable, () -> lock.removeLockAcquisitionListener(listener));
  }

  @Override
  public void prohibitTakingLocksInsideAndRun(@NotNull Runnable runnable, @NlsSafe String advice) {
    getThreadingSupport().prohibitTakingLocksInsideAndRun(runnableUnitFunction(runnable), advice);
  }

  @Override
  public String getLockProhibitedAdvice() {
    return getThreadingSupport().getLockingProhibitedAdvice();
  }

  @Override
  public boolean isTopmostReadAccessAllowed() {
    return getThreadingSupport().isInTopmostReadAction();
  }

  @Override
  public void addSuspendingWriteActionListener(@NotNull WriteLockReacquisitionListener listener, @NotNull Disposable parentDisposable) {
    lock.setWriteLockReacquisitionListener(listener);
    Disposer.register(parentDisposable, () -> lock.removeWriteLockReacquisitionListener(listener));
  }

  @Override
  public boolean isParallelizedReadAction(CoroutineContext context) {
    return getThreadingSupport().isParallelizedReadAction(context);
  }

  @Override
  public @NotNull ThreadingSupport getThreadingSupport() {
    return lock;
  }
}
