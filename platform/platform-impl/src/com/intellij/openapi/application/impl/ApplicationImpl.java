// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.CommonBundle;
import com.intellij.concurrency.ThreadContext;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.PluginException;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.*;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.idea.AppExitCodes;
import com.intellij.idea.AppMode;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ModalityKt;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.client.ClientAwareComponentManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.ProgressResult;
import com.intellij.openapi.progress.impl.ProgressRunner;
import com.intellij.openapi.progress.util.PotemkinProgress;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.psi.util.ReadActionCache;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.*;
import com.intellij.util.concurrency.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DisposableWrapperList;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.EDT;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function0;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.EventListener;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.codeWithMe.ClientId.decorateCallable;
import static com.intellij.codeWithMe.ClientId.decorateRunnable;
import static com.intellij.ide.ShutdownKt.cancelAndJoinBlocking;
import static com.intellij.openapi.application.CoroutinesKt.isBackgroundWriteAction;
import static com.intellij.openapi.application.ModalityKt.asContextElement;
import static com.intellij.openapi.application.RuntimeFlagsKt.getReportInvokeLaterWithoutModality;
import static com.intellij.openapi.application.impl.AppImplKt.rethrowCheckedExceptions;
import static com.intellij.openapi.application.impl.AppImplKt.runnableUnitFunction;
import static com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope;
import static com.intellij.util.concurrency.AppExecutorUtil.propagateContext;
import static com.intellij.util.concurrency.Propagation.isContextAwareComputation;

@ApiStatus.Internal
public final class ApplicationImpl extends ClientAwareComponentManager implements ApplicationEx {
  private static @NotNull Logger getLogger() {
    return Logger.getInstance(ApplicationImpl.class);
  }

  /** @deprecated see {@link ModalityInvokator} notice */
  @Deprecated
  private final ModalityInvokator myInvokator = new ModalityInvokatorImpl();

  private final EventDispatcher<ApplicationListener> myDispatcher = EventDispatcher.create(ApplicationListener.class);

  private final LockDispatchListener myLockDispatcherListener = new LockDispatchListener();

  private final boolean myTestModeFlag;
  private final boolean myHeadlessMode;
  private final boolean myCommandLineMode;

  private final boolean myIsInternal;

  // contents modified in write action, read in read action
  private final TransactionGuardImpl myTransactionGuard = new TransactionGuardImpl();

  private final ReadActionCacheImpl myReadActionCacheImpl = new ReadActionCacheImpl();

  private final ThreadLocal<Boolean> myImpatientReader = ThreadLocal.withInitial(() -> false);

  private final long myStartTime = System.currentTimeMillis();
  private boolean mySaveAllowed;
  private volatile boolean myExitInProgress;

  private final @Nullable Disposable myLastDisposable;  // the last to be disposed

  private static final String WAS_EVER_SHOWN = "was.ever.shown";

  private static final LegacyProgressIndicatorProvider myLegacyIndicatorProvider = () -> {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
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
    myTestModeFlag = false;
    myHeadlessMode = AppMode.isHeadless();
    myCommandLineMode = AppMode.isCommandLine();
    if (!myHeadlessMode || SystemProperties.getBooleanProperty("allow.save.application.headless", false)) {
      mySaveAllowed = true;
    }

    myLastDisposable = null;
  }

  private static void registerFakeServices(ApplicationImpl app) {
    app.registerServiceInstance(TransactionGuard.class, app.myTransactionGuard, fakeCorePluginDescriptor);
    app.registerServiceInstance(Application.class, app, fakeCorePluginDescriptor);
    app.registerServiceInstance(ReadActionCache.class, app.myReadActionCacheImpl, fakeCorePluginDescriptor);
  }

  @TestOnly
  ThreadingSupport getRwLock() {
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

  @TestOnly
  public void disposeContainer() {
    cancelAndJoinBlocking(this);
    runWriteAction(() -> {
      startDispose();
      Disposer.dispose(this);
    });
    Disposer.assertIsEmpty();
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
    Runnable actionDecorated = decorateRunnable(action);
    return AppExecutorUtil.getAppExecutorService().submit(new Runnable() {
      @Override
      public void run() {
        if (isDisposed()) {
          return;
        }

        try {
          actionDecorated.run();
        }
        catch (ProcessCanceledException e) {
          // ignore
        }
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
  public @NotNull <T> Future<T> executeOnPooledThread(@NotNull Callable<T> action) {
    Callable<T> actionDecorated = decorateCallable(action);
    return AppExecutorUtil.getAppExecutorService().submit(new Callable<T>() {
      @Override
      public T call() {
        if (isDisposed()) {
          return null;
        }
        try {
          return actionDecorated.call();
        }
        catch (ProcessCanceledException e) {
          // ignore
        }
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
    return getThreadingSupport().isWriteIntentLocked();
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable) {
    invokeLater(runnable, getDisposed());
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull Condition<?> expired) {
    ModalityState state = getDefaultModalityState();
    if (getReportInvokeLaterWithoutModality() && state == ModalityState.any()) {
      getLogger().error("Application.invokeLater() was called without modality state and default modality state is ANY\n" +
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
    final boolean ctxAware = isContextAwareComputation(runnable);
    // Start from inner layer: transaction guard
    final Runnable guarded = myTransactionGuard.wrapLaterInvocation(runnable, state);
    // Middle layer: lock and modality
    final Runnable locked = wrapWithRunIntendedWriteActionAndModality(guarded, ctxAware ? null : state);
    Runnable finalRunnable = locked;
    // Outer layer, optional: context capture & reset
    if (propagateContext()) {
      Pair<Runnable, Condition<?>> captured = Propagation.capturePropagationContext(locked, expired, runnable);
      finalRunnable = captured.getFirst();
      expired = captured.getSecond();
    }
    LaterInvocator.invokeLater(state, expired, finalRunnable);
  }

  @ApiStatus.Internal
  @Override
  public void dispatchCoroutineOnEDT(Runnable runnable, ModalityState state) {
    LaterInvocator.invokeLater(state, Conditions.alwaysFalse(), myTransactionGuard.wrapCoroutineInvocation(runnable, state));
  }

  @Override
  public void dispose() {
    getThreadingSupport().removeReadActionListener(myLockDispatcherListener);
    getThreadingSupport().removeWriteActionListener(myLockDispatcherListener);
    getThreadingSupport().removeWriteIntentReadActionListener(myLockDispatcherListener);
    getThreadingSupport().removeLockAcquisitionListener(myLockDispatcherListener);
    getThreadingSupport().removeWriteLockReacquisitionListener(myLockDispatcherListener);
    getThreadingSupport().removeLegacyIndicatorProvider(myLegacyIndicatorProvider);

    //noinspection deprecation
    myDispatcher.getMulticaster().applicationExiting();

    IComponentStore componentStore = getServiceIfCreated(IComponentStore.class);
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
  @SuppressWarnings("UsagesOfObsoleteApi")
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
      catch (ProcessCanceledException e) {
        // ok to ignore.
        return false;
      }
      return true;
    }

    CompletableFuture<@NotNull ProgressWindow> progress =
      createProgressWindowAsyncIfNeeded(progressTitle, canBeCanceled, shouldShowModalWindow, project, parentComponent, cancelText);

    // Event pumping (`ProgressRunner.modal()`) is not correct without entering the modality (`shouldShowModalWindow == false`),
    // because one of the events might show a dialog scheduled in outer modality,
    // which will start another nested loop and prevent the pumping from exit until the dialog closed (IDEA-307428):
    // - modal progress: `enterModal`;
    // - modal progress: schedule modal dialog to show after 300ms;
    // - modal progress: `pumpEventsForHierarchy`;
    // - one of events runs `isConditionalModal() && !shouldStartInBackground()` task;
    // - on EDT such tasks are executed synchronously;
    // - task starts nested `pumpEventsForHierarchy` without entering the modality;
    // - nested `pumpEventsForHierarchy` shows scheduled modal progress dialog;
    // - nested `pumpEventsForHierarchy` cannot finish because scheduled modal progress dialog runs nested event loop;
    // - modal dialog cannot finish until task is finished because it's synchronous.
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

    ProgressResult<?> result = progressRunner.submitAndGet();

    Throwable exception = result.getThrowable();
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

    if (holdsReadLock()) {
      throw new IllegalStateException("Calling invokeAndWait from read-action leads to possible deadlock.");
    }

    final boolean ctxAware = isContextAwareComputation(runnable);
    // Start from inner layer: transaction guard
    final Runnable guarded = myTransactionGuard.wrapLaterInvocation(runnable, state);
    // Middle layer: lock and modality
    final Runnable locked = wrapWithLocks ? wrapWithRunIntendedWriteActionAndModality(guarded, ctxAware ? null : state) : guarded;
    // Outer layer context capture & reset
    final Runnable finalRunnable = AppImplKt.rethrowExceptions(AppScheduledExecutorService::captureContextCancellationForRunnableThatDoesNotOutliveContextScope, locked);

    LaterInvocator.invokeAndWait(state, finalRunnable);
  }

  private @NotNull Runnable wrapWithRunIntendedWriteActionAndModality(@NotNull Runnable runnable, @Nullable ModalityState modalityState) {
    return modalityState != null ?
           new Runnable() {
             @Override
             public void run() {
               try (AccessToken ignored = ThreadContext.installThreadContext(
                 ThreadContext.currentThreadContext().plus(asContextElement(modalityState)), true)) {
                 runIntendedWriteActionOnCurrentThread(runnable);
               }
             }

             @Override
             public String toString() {
               return runnable.toString();
             }
           }
                                 :
           new Runnable() {
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
    Window window = ComponentUtil.getWindow(c);
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
    int flags = SAVE;
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
    int flags = SAVE;
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
    IJTracer tracer = TelemetryManager.getInstance().getTracer(new com.intellij.platform.diagnostic.telemetry.Scope("exitApp", null));
    Span exitSpan = tracer.spanBuilder("application.exit").startSpan();
    boolean force = BitUtil.isSet(flags, FORCE_EXIT);
    try (Scope scope = exitSpan.makeCurrent()) {
      if (!force && !confirmExitIfNeeded(BitUtil.isSet(flags, EXIT_CONFIRMED))) {
        return null;
      }

      AppLifecycleListener lifecycleListener = getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
      lifecycleListener.appClosing();

      if (!force && !canExit(restart)) {
        return null;
      }

      stopServicePreloading();

      if (BitUtil.isSet(flags, SAVE)) {
        try {
          TraceKt.use(tracer.spanBuilder("saveSettingsOnExit"),
                      __ -> SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(this));
        }
        catch (Throwable e) {
          logErrorDuringExit("Failed to save settings", e);
        }
      }

      if (isInstantShutdownPossible()) {
        for (Frame frame : Frame.getFrames()) {
          frame.setVisible(false);
        }
      }

      try {
        lifecycleListener.appWillBeClosed(restart);
      }
      catch (Throwable t) {
        logErrorDuringExit("Failed to invoke lifecycle listeners", t);
      }

      LifecycleUsageTriggerCollector.onIdeClose(restart);

      boolean success = true;
      ProjectManagerEx manager = ProjectManagerEx.getInstanceExIfCreated();
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
      try (var ignored = Cancellation.withNonCancelableSection()) {
        scope.close();
        exitSpan.end();
        //noinspection TestOnlyProblems
        disposeContainer();
      }
      catch (Throwable t) {
        logErrorDuringExit("Failed to dispose the container", t);
      }

      if (!success || isUnitTestMode()) {
        return null;
      }

      IdeEventQueue.applicationClose();

      //noinspection SpellCheckingInspection
      if (Boolean.getBoolean("idea.test.guimode")) {
        //noinspection TestOnlyProblems
        ShutDownTracker.getInstance().run();
        return null;
      }

      IdeaLogger.dropFrequentExceptionsCaches();
      if (restart) {
        if (Restarter.isSupported()) {
          try {
            Restarter.scheduleRestart(BitUtil.isSet(flags, ELEVATE), beforeRestart);
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

  private boolean isInstantShutdownPossible() {
    InstantShutdown instantShutdown = Objects.requireNonNull(getService(InstantShutdown.class));
    return instantShutdown.isAllowed() && !ProgressManager.getInstance().hasProgressIndicator();
  }

  private @NotNull CompletableFuture<@NotNull ProgressWindow> createProgressWindowAsyncIfNeeded(
    @NotNull @NlsContexts.ProgressTitle String progressTitle,
    boolean canBeCanceled,
    boolean shouldShowModalWindow,
    @Nullable Project project,
    @Nullable JComponent parentComponent,
    @Nullable @NlsContexts.Button String cancelText
  ) {
    if (SwingUtilities.isEventDispatchThread()) {
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
    @SuppressWarnings("UsagesOfObsoleteApi") var progress = new ProgressWindow(canBeCanceled, !shouldShowModalWindow, project, parentComponent, cancelText);
    Disposer.register(this, progress);  // to dispose the progress even when `ProgressManager#runProcess` is not called
    progress.setTitle(progressTitle);
    return progress;
  }

  private static boolean confirmExitIfNeeded(boolean exitConfirmed) {
    boolean hasUnsafeBgTasks = ProgressManager.getInstance().hasUnsafeProgressIndicator();
    if (exitConfirmed && !hasUnsafeBgTasks) {
      return true;
    }

    DoNotAskOption option = new DoNotAskOption() {
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
      getLogger().error("Headless application has been completed but background tasks are still running! Application will be terminated." +
                        "\nThread dump:\n" + ThreadDumper.dumpThreadsToString());
      return true;
    }

    AtomicBoolean alreadyGone = new AtomicBoolean(false);
    if (hasUnsafeBgTasks) {
      Runnable dialogRemover = Messages.createMessageDialogRemover(null);
      Runnable task = new Runnable() {
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

    String message = ApplicationBundle.message(hasUnsafeBgTasks ? "exit.confirm.prompt.tasks" : "exit.confirm.prompt");
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
    for (ApplicationListener applicationListener : myDispatcher.getListeners()) {
      if (restart && !applicationListener.canRestartApplication() 
          || !restart && !applicationListener.canExitApplication()) {
        return false;
      }
    }

    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceExIfCreated();
    if (projectManager == null) {
      return true;
    }

    Project[] projects = projectManager.getOpenProjects();
    for (Project project : projects) {
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
  @SuppressWarnings("RedundantThrows")
  public <T, E extends Throwable> T runUnlockingIntendedWrite(@NotNull ThrowableComputable<T, E> action) throws E {
    return getThreadingSupport().runUnlockingIntendedWrite(rethrowCheckedExceptions(action));
  }

  @Override
  public void runReadAction(@NotNull Runnable action) {
    getThreadingSupport().runReadAction(action.getClass(), runnableUnitFunction(action));
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computation) {
    return getThreadingSupport().runReadAction(computation.getClass(), computation::compute);
  }

  @Override
  public <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    return getThreadingSupport().runReadAction(computation.getClass(), rethrowCheckedExceptions(computation));
  }

  @Override
  @ApiStatus.Experimental
  public boolean runWriteActionWithNonCancellableProgressInDispatchThread(@NotNull @NlsContexts.ProgressTitle String title,
                                                                          @Nullable Project project,
                                                                          @Nullable JComponent parentComponent,
                                                                          @NotNull Consumer<? super ProgressIndicator> action) {
    return runEdtProgressWriteAction(title, project, parentComponent, null, action);
  }

  @Override
  @ApiStatus.Experimental
  public boolean runWriteActionWithCancellableProgressInDispatchThread(@NotNull @NlsContexts.ProgressTitle String title,
                                                                       @Nullable Project project,
                                                                       @Nullable JComponent parentComponent,
                                                                       @NotNull Consumer<? super ProgressIndicator> action) {
    return runEdtProgressWriteAction(title, project, parentComponent, IdeBundle.message("action.stop"), action);
  }

  private static boolean runEdtProgressWriteAction(
    @NlsContexts.ProgressTitle String title,
    @Nullable Project project,
    @Nullable JComponent parentComponent,
    @Nls(capitalization = Nls.Capitalization.Title) @Nullable String cancelText,
    @NotNull Consumer<? super @Nullable ProgressIndicator> action
  ) {
    return getThreadingSupport().runWriteAction(action.getClass(), () -> {
      var indicator = new PotemkinProgress(title, project, parentComponent, cancelText);
      indicator.runInSwingThread(() -> {
        action.accept(indicator);
      });
      return !indicator.isCanceled();
    });
  }

  private static void checkWriteActionAllowedOnCurrentThread() {
    if (EDT.isCurrentThreadEdt()) {
      return;
    }
    if (!isBackgroundWriteAction(ThreadContext.currentThreadContext())) {
      throw new IllegalStateException(
        "Background write action is not permitted on this thread. Consider using `backgroundWriteAction`, or switch to EDT");
    }
  }

  @Override
  public void runWriteAction(@NotNull Runnable action) {
    checkWriteActionAllowedOnCurrentThread();
    getThreadingSupport().runWriteAction(action.getClass(), runnableUnitFunction(action));
  }

  @Override
  public <T> T runWriteAction(@NotNull Computable<T> computation) {
    checkWriteActionAllowedOnCurrentThread();
    return getThreadingSupport().runWriteAction(computation.getClass(), computation::compute);
  }

  @Override
  public <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    checkWriteActionAllowedOnCurrentThread();
    return getThreadingSupport().runWriteAction(computation.getClass(), rethrowCheckedExceptions(computation));
  }

  @Override
  public boolean hasWriteAction(@NotNull Class<?> actionClass) {
    ThreadingAssertions.softAssertReadAccess();
    return getThreadingSupport().hasWriteAction(actionClass);
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
    else {
      JRootPane root = component.getRootPane();
      if (root != null) {
        component.putClientProperty(WAS_EVER_SHOWN, Boolean.TRUE);
        ThreadingAssertions.assertEventDispatchThread();
      }
    }
  }

  @Override
  public boolean tryRunReadAction(@NotNull Runnable action) {
    return getThreadingSupport().tryRunReadAction(action);
  }

  @Override
  public boolean isActive() {
    if (isHeadlessEnvironment()) {
      return true;
    }

    if (isDisposed()) {
      return false;
    }

    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (activeWindow != null) {
      ApplicationActivationStateManager.INSTANCE.updateState(this, activeWindow);
    }

    return ApplicationActivationStateManager.INSTANCE.isActive();
  }

  @Override
  @SuppressWarnings("deprecation")
  public @NotNull AccessToken acquireReadActionLock() {
    PluginException.reportDeprecatedUsage("Application.acquireReadActionLock", "Use `runReadAction()` instead");
    return getThreadingSupport().acquireReadActionLock();
  }

  @Override
  public boolean isWriteActionPending() {
    return getThreadingSupport().isWriteActionPending();
  }

  @Override
  public boolean isWriteAccessAllowed() {
    return getThreadingSupport().isWriteAccessAllowed();
  }

  @Override
  @SuppressWarnings("deprecation")
  public @NotNull AccessToken acquireWriteActionLock(@NotNull Class<?> clazz) {
    PluginException.reportDeprecatedUsage("Application#acquireWriteActionLock", "Use `runWriteAction()` instead");
    return getThreadingSupport().acquireWriteActionLock(clazz);
  }

  @Override
  @SuppressWarnings("UsagesOfObsoleteApi")
  public void assertWriteAccessAllowed() {
    ThreadingAssertions.assertWriteAccess();
  }

  /**
   * If called inside a write-action, executes the given code under modal progress with write-lock released (e.g., to allow for read-action parallelization).
   * It's the caller's responsibility to invoke this method only when the model is in an internally consistent state,
   * so that background threads with read actions don't see half-baked PSI/VFS/etc. The runnable may perform write-actions itself;
   * callers should be ready for those.
   */
  public void executeSuspendingWriteAction(@Nullable Project project,
                                           @NotNull @NlsContexts.DialogTitle String title,
                                           @NotNull Runnable runnable) {
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

  private void fireBeforeWriteActionStart(@NotNull Class<?> action) {
    myDispatcher.getMulticaster().beforeWriteActionStart(action);
  }

  private void fireWriteActionStarted(@NotNull Class<?> action) {
    myDispatcher.getMulticaster().writeActionStarted(action);
  }

  private void fireWriteActionFinished(@NotNull Class<?> action) {
    myDispatcher.getMulticaster().writeActionFinished(action);
  }

  private void fireAfterWriteActionFinished(@NotNull Class<?> action) {
    myDispatcher.getMulticaster().afterWriteActionFinished(action);
    otelMonitor.get().writeActionExecuted();
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
    boolean writeActionPending = isWriteActionPending();
    boolean writeActionInProgress = isWriteActionInProgress();
    boolean writeAccessAllowed =isWriteAccessAllowed();
    return "Application"
           + (getContainerState().get() == ContainerState.COMPONENT_CREATED ? "" : " (containerState " + getContainerStateName() + ") ")
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
      long start = System.nanoTime() - duration;
      StartUpMeasurer.addCompletedActivity(start, handler.getClass(), ActivityCategory.PROJECT_OPEN_HANDLER, null,
                                           StartUpMeasurer.MEASURE_THRESHOLD);
    }
    else if (topic == VirtualFileManager.VFS_CHANGES) {
      if (TimeUnit.NANOSECONDS.toMillis(duration) > 50) {
        getLogger().info(String.format("LONG VFS PROCESSING. Topic=%s, offender=%s, message=%s, time=%dms",
                                       topic.getDisplayName(), handler.getClass(), messageName, TimeUnit.NANOSECONDS.toMillis(duration)));
      }
    }
  }

  @TestOnly
  void disableEventsUntil(@NotNull Disposable disposable) {
    myDispatcher.neuterMultiCasterWhilePerformanceTestIsRunningUntil(disposable);
  }

  @Override
  public boolean isComponentCreated() {
    return getContainerState().get().compareTo(ContainerState.COMPONENT_CREATED) >= 0;
  }

  @ApiStatus.Internal
  public static void postInit(@NotNull ApplicationImpl app) {
    AtomicBoolean reported = new AtomicBoolean();
    IdeEventQueue.getInstance().addPostprocessor(e -> {
      if (app.isWriteAccessAllowed() && reported.compareAndSet(false, true)) {
        getLogger().error("AWT events are not allowed inside write action: " + e);
      }
      return true;
    }, app.getCoroutineScope());

    getThreadingSupport().setReadActionListener(app.myLockDispatcherListener);
    getThreadingSupport().setWriteActionListener(app.myLockDispatcherListener);
    getThreadingSupport().setWriteIntentReadActionListener(app.myLockDispatcherListener);
    getThreadingSupport().setLockAcquisitionListener(app.myLockDispatcherListener);
    getThreadingSupport().setWriteLockReacquisitionListener(app.myLockDispatcherListener);
    getThreadingSupport().setLegacyIndicatorProvider(myLegacyIndicatorProvider);

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
    myLockDispatcherListener.addWriteActionListener(listener, parentDisposable);
  }

  @Override
  public void addReadActionListener(@NotNull ReadActionListener listener, @NotNull Disposable parentDisposable) {
    myLockDispatcherListener.addReadActionListener(listener, parentDisposable);
  }

  @Override
  public void addWriteIntentReadActionListener(@NotNull WriteIntentReadActionListener listener, @NotNull Disposable parentDisposable) {
    myLockDispatcherListener.addWriteIntentReadActionListener(listener, parentDisposable);
  }

  @Override
  public void addLockAcquisitionListener(@NotNull LockAcquisitionListener listener, @NotNull Disposable parentDisposable) {
    myLockDispatcherListener.addLockAcquisitionListener(listener, parentDisposable);
  }

  @Override
  public void prohibitTakingLocksInsideAndRun(@NotNull Runnable runnable, boolean failSoftly, @NlsSafe String advice) {
    getThreadingSupport().prohibitTakingLocksInsideAndRun(runnable, failSoftly, advice);
  }

  @Override
  public void allowTakingLocksInsideAndRun(@NotNull Runnable runnable) {
    getThreadingSupport().allowTakingLocksInsideAndRun(runnable);
  }

  @Override
  public String isLockingProhibited() {
    return getThreadingSupport().getLockingProhibitedAdvice();
  }

  @Override
  public boolean isTopmostReadAccessAllowed() {
    return getThreadingSupport().isInTopmostReadAction();
  }

  @Override
  public void addSuspendingWriteActionListener(@NotNull WriteLockReacquisitionListener listener, @NotNull Disposable parentDisposable) {
    myLockDispatcherListener.addSuspendingWriteActionListener(listener, parentDisposable);
  }

  /**
   * inner class by intention
   */
  private class LockDispatchListener
    implements ReadActionListener, WriteActionListener, WriteIntentReadActionListener, LockAcquisitionListener,
               WriteLockReacquisitionListener {

    private final DisposableWrapperList<WriteActionListener> myWriteActionListeners = new DisposableWrapperList<>();

    private final DisposableWrapperList<ReadActionListener> myReadActionListeners = new DisposableWrapperList<>();

    private final DisposableWrapperList<WriteIntentReadActionListener> myWriteIntentReadActionListeners =
      new DisposableWrapperList<>();

    private final DisposableWrapperList<LockAcquisitionListener> myLockAcquisitionListeners =
      new DisposableWrapperList<>();

    private final DisposableWrapperList<WriteLockReacquisitionListener> myWriteLockReacquisitionListeners =
      new DisposableWrapperList<>();

    public void addWriteActionListener(WriteActionListener listener, Disposable disposable) {
      addListener(myWriteActionListeners, listener, disposable);
    }

    public void addReadActionListener(ReadActionListener listener, Disposable disposable) {
      addListener(myReadActionListeners, listener, disposable);
    }

    public void addWriteIntentReadActionListener(WriteIntentReadActionListener listener, Disposable disposable) {
      addListener(myWriteIntentReadActionListeners, listener, disposable);
    }

    public void addSuspendingWriteActionListener(WriteLockReacquisitionListener listener, Disposable disposable) {
      addListener(myWriteLockReacquisitionListeners, listener, disposable);
    }

    public void addLockAcquisitionListener(LockAcquisitionListener listener, Disposable disposable) {
      addListener(myLockAcquisitionListeners, listener, disposable);
    }

    private static <T extends EventListener> void addListener(DisposableWrapperList<T> list, T listener, Disposable disposable) {
      list.add(listener, disposable);
    }

    @Override
    public void writeActionFinished(@NotNull Class<?> action) {
      ApplicationImpl.this.fireWriteActionFinished(action);
      invokeListeners(myWriteActionListeners, WriteActionListener::writeActionFinished, action);
    }

    @Override
    public void afterWriteActionFinished(@NotNull Class<?> action) {
      ApplicationImpl.this.fireAfterWriteActionFinished(action);
      invokeListeners(myWriteActionListeners, WriteActionListener::afterWriteActionFinished, action);
    }

    @Override
    public void beforeWriteLockAcquired() {
      invokeListeners(myLockAcquisitionListeners, LockAcquisitionListener::beforeWriteLockAcquired);
    }

    @Override
    public void afterWriteLockAcquired() {
      invokeListeners(myLockAcquisitionListeners, LockAcquisitionListener::afterWriteLockAcquired);
    }

    @Override
    public void writeIntentReadActionStarted(@NotNull Class<?> action) {
      invokeListeners(myWriteIntentReadActionListeners, WriteIntentReadActionListener::writeIntentReadActionStarted, action);
    }

    @Override
    public void writeIntentReadActionFinished(@NotNull Class<?> action) {
      invokeListeners(myWriteIntentReadActionListeners, WriteIntentReadActionListener::writeIntentReadActionFinished, action);
    }

    @Override
    public void beforeWriteIntentReadActionStart(@NotNull Class<?> action) {
      invokeListeners(myWriteIntentReadActionListeners, WriteIntentReadActionListener::beforeWriteIntentReadActionStart, action);
    }

    @Override
    public void afterWriteIntentReadActionFinished(@NotNull Class<?> action) {
      invokeListeners(myWriteIntentReadActionListeners, WriteIntentReadActionListener::afterWriteIntentReadActionFinished, action);
    }

    @Override
    public void beforeWriteLockReacquired() {
      invokeListeners(myWriteLockReacquisitionListeners, WriteLockReacquisitionListener::beforeWriteLockReacquired);
    }


    @Override
    public void readActionStarted(@NotNull Class<?> action) {
      invokeListeners(myReadActionListeners, ReadActionListener::readActionStarted, action);
    }

    @Override
    public void fastPathAcquisitionFailed() {
      // Impatient reader not in non-cancellable session will not wait
      if (myImpatientReader.get() && !Cancellation.isInNonCancelableSection()) {
        throw ApplicationUtil.CannotRunReadActionException.create();
      }
    }

    @Override
    public void beforeReadActionStart(@NotNull Class<?> action) {
      invokeListeners(myReadActionListeners, ReadActionListener::beforeReadActionStart, action);
    }

    @Override
    public void afterReadActionFinished(@NotNull Class<?> action) {
      invokeListeners(myReadActionListeners, ReadActionListener::afterReadActionFinished, action);
    }

    @Override
    public void readActionFinished(@NotNull Class<?> action) {
      myReadActionCacheImpl.clear();
      invokeListeners(myReadActionListeners, ReadActionListener::readActionFinished, action);
      otelMonitor.get().readActionExecuted();
    }

    @Override
    public void beforeWriteActionStart(@NotNull Class<?> action) {
      ActivityTracker.getInstance().inc();
      ApplicationImpl.this.fireBeforeWriteActionStart(action);
      invokeListeners(myWriteActionListeners, WriteActionListener::beforeWriteActionStart, action);
    }

    @Override
    public void writeActionStarted(@NotNull Class<?> action) {
      ApplicationImpl.this.fireWriteActionStarted(action);
      invokeListeners(myWriteActionListeners, WriteActionListener::writeActionStarted, action);
    }

    private static <T extends EventListener> void invokeListeners(List<T> listeners, BiConsumer<T, Class<?>> applier, Class<?> arg) {
      invokeListeners(listeners, (listener) -> applier.accept(listener, arg));
    }

    private static <T extends EventListener> void invokeListeners(List<T> listeners, Consumer<T> applier) {
      List<Throwable> exceptions = new SmartList<>();
      for (T listener : listeners) {
        try {
          applier.accept(listener);
        }
        catch (Throwable t) {
          exceptions.add(t);
        }
      }
      for (Throwable exception : exceptions) {
        if (exception instanceof CancellationException || exception instanceof ControlFlowException) {
          continue;
        }
        getLogger().error(exception);
      }
    }
  }


  @Override
  public kotlin.Pair<CoroutineContext, AccessToken> getLockStateAsCoroutineContext(CoroutineContext baseContext, boolean shared) {
    return getThreadingSupport().getPermitAsContextElement(baseContext, shared);
  }

  @Override
  public boolean isParallelizedReadAction(CoroutineContext context) {
    return getThreadingSupport().isParallelizedReadAction(context);
  }

  private static @NotNull ThreadingSupport getThreadingSupport() {
    return AppImplKt.getGlobalThreadingSupport();
  }
}
