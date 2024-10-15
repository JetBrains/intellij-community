// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.application.ModalityKt;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.client.ClientAwareComponentManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.ProgressResult;
import com.intellij.openapi.progress.impl.ProgressRunner;
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
import static com.intellij.util.concurrency.AppExecutorUtil.propagateContext;
import static com.intellij.util.concurrency.Propagation.isContextAwareComputation;

@ApiStatus.Internal
public final class ApplicationImpl extends ClientAwareComponentManager implements ApplicationEx, ReadActionListener, WriteActionListener {
  private static @NotNull Logger getLogger() {
    return Logger.getInstance(ApplicationImpl.class);
  }

  /** @deprecated see {@link ModalityInvokator} notice */
  @Deprecated
  private final ModalityInvokator myInvokator = new ModalityInvokatorImpl();

  private final EventDispatcher<ApplicationListener> myDispatcher = EventDispatcher.create(ApplicationListener.class);

  private final boolean myTestModeFlag;
  private final boolean myHeadlessMode;
  private final boolean myCommandLineMode;

  private final boolean myIsInternal;

  // contents modified in write action, read in read action
  private final TransactionGuardImpl myTransactionGuard = new TransactionGuardImpl();

  private final ReadActionCacheImpl myReadActionCacheImpl = new ReadActionCacheImpl();

  private final long myStartTime = System.currentTimeMillis();
  private boolean mySaveAllowed;
  private volatile boolean myExitInProgress;

  private final @Nullable Disposable myLastDisposable;  // the last to be disposed

  // defer reading isUnitTest flag until it's initialized
  static final class Holder {
    static final int ourDumpThreadsOnLongWriteActionWaiting =
      ApplicationManager.getApplication().isUnitTestMode() ? 0 : Integer.getInteger("dump.threads.on.long.write.action.waiting", 0);
  }

  private static final String WAS_EVER_SHOWN = "was.ever.shown";

  @SuppressWarnings("Convert2Lambda")
  private final Supplier<OTelReadWriteActionsMonitor> otelMonitor = new SynchronizedClearableLazy<>(new Function0<>() {
    @Override
    public OTelReadWriteActionsMonitor invoke() {
      return new OTelReadWriteActionsMonitor(TelemetryManager.getInstance().getMeter(PlatformScopesKt.EDT));
    }
  });

  @TestOnly
  public ApplicationImpl(boolean isHeadless) {
    super(GlobalScope.INSTANCE);

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
    getThreadingSupport().executeByImpatientReader(runnable);
  }

  @Override
  public boolean isInImpatientReader() {
    return getThreadingSupport().isInImpatientReader();
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
    return getThreadingSupport().executeOnPooledThread(action, this::isDisposed);
  }

  @Override
  public @NotNull <T> Future<T> executeOnPooledThread(@NotNull Callable<T> action) {
    return getThreadingSupport().executeOnPooledThread(action, this::isDisposed);
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
    invokeLater(runnable, getDefaultModalityState(), expired);
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
    getThreadingSupport().removeReadActionListener(this);
    getThreadingSupport().removeWriteActionListener(this);

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

    // Remove IW lock from EDT as EDT might be re-created, which might lead to deadlock if anybody uses this disposed app
    if (isUnitTestMode()) {
      invokeLater(() -> releaseWriteIntentLock(), ModalityState.nonModal());
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
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState state) {
    if (EDT.isCurrentThreadEdt()) {
      runIntendedWriteActionOnCurrentThread(runnable);
      return;
    }

    if (holdsReadLock()) {
      throw new IllegalStateException("Calling invokeAndWait from read-action leads to possible deadlock.");
    }

    final boolean ctxAware = isContextAwareComputation(runnable);
    // Start from inner layer: transaction guard
    final Runnable guarded = myTransactionGuard.wrapLaterInvocation(runnable, state);
    // Middle layer: lock and modality
    final Runnable locked = wrapWithRunIntendedWriteActionAndModality(guarded, ctxAware ? null : state);
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

      if (!force && !canExit()) {
        return null;
      }

      stopServicePreloading();

      if (BitUtil.isSet(flags, SAVE)) {
        TraceKt.use(tracer.spanBuilder("saveSettingsOnExit"),
                    __ -> SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(this));
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

  private boolean canExit() {
    for (ApplicationListener applicationListener : myDispatcher.getListeners()) {
      if (!applicationListener.canExitApplication()) {
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
    getThreadingSupport().runIntendedWriteActionOnCurrentThread(action);
  }

  @Override
  @SuppressWarnings("RedundantThrows")
  public <T, E extends Throwable> T runUnlockingIntendedWrite(@NotNull ThrowableComputable<T, E> action) throws E {
    return getThreadingSupport().runUnlockingIntendedWrite(action);
  }

  @Override
  public void runReadAction(@NotNull Runnable action) {
    getThreadingSupport().runReadAction(action);
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computation) {
    return getThreadingSupport().runReadAction(computation);
  }

  @Override
  public <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    return getThreadingSupport().runReadAction(computation);
  }

  @Override
  public boolean acquireWriteIntentLock(@Nullable String ignored) {
    return getThreadingSupport().acquireWriteIntentLock(ignored);
  }

  @Override
  public void releaseWriteIntentLock() {
    getThreadingSupport().releaseWriteIntentLock();
  }

  @Override
  @ApiStatus.Experimental
  public boolean runWriteActionWithNonCancellableProgressInDispatchThread(@NotNull @NlsContexts.ProgressTitle String title,
                                                                          @Nullable Project project,
                                                                          @Nullable JComponent parentComponent,
                                                                          @NotNull Consumer<? super ProgressIndicator> action) {
    return getThreadingSupport().runWriteActionWithNonCancellableProgressInDispatchThread(title, project, parentComponent, action);
  }

  @Override
  @ApiStatus.Experimental
  public boolean runWriteActionWithCancellableProgressInDispatchThread(@NotNull @NlsContexts.ProgressTitle String title,
                                                                       @Nullable Project project,
                                                                       @Nullable JComponent parentComponent,
                                                                       @NotNull java.util.function.Consumer<? super ProgressIndicator> action) {
    return getThreadingSupport().runWriteActionWithCancellableProgressInDispatchThread(title, project, parentComponent, action);
  }

  @Override
  public void runWriteAction(@NotNull Runnable action) {
    getThreadingSupport().runWriteAction(action);
  }

  @Override
  public <T> T runWriteAction(@NotNull Computable<T> computation) {
    return getThreadingSupport().runWriteAction(computation);
  }

  @Override
  public <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    return getThreadingSupport().runWriteAction(computation);
  }

  @Override
  public boolean hasWriteAction(@NotNull Class<?> actionClass) {
    return getThreadingSupport().hasWriteAction(actionClass);
  }

  @Override
  public <T, E extends Throwable> T runWriteIntentReadAction(@NotNull ThrowableComputable<T, E> computation) {
    return getThreadingSupport().runWriteIntentReadAction(computation);
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
    getThreadingSupport().executeSuspendingWriteAction(project, title, runnable);
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
    return pluginDescriptor.appContainerDescriptor;
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

    getThreadingSupport().setReadActionListener(app);
    getThreadingSupport().setWriteActionListener(app);

    app.addApplicationListener(new ApplicationListener() {
      @Override
      public void afterWriteActionFinished(@NotNull Object action) {
        reported.set(false);
      }
    }, app);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void flushNativeEventQueue() {
    IdeEventQueue.getInstance().flushNativeEventQueue();
  }

  @Override
  public void readActionFinished(@NotNull Class<?> action) {
    myReadActionCacheImpl.clear();
    otelMonitor.get().readActionExecuted();
  }

  @Override
  public void beforeWriteActionStart(@NotNull Class<?> action) {
    ActivityTracker.getInstance().inc();
    fireBeforeWriteActionStart(action);
  }

  @Override
  public void writeActionStarted(@NotNull Class<?> action) {
    fireWriteActionStarted(action);
  }

  @Override
  public void writeActionFinished(@NotNull Class<?> action) {
    fireWriteActionFinished(action);
  }

  @Override
  public void afterWriteActionFinished(@NotNull Class<?> action) {
    fireAfterWriteActionFinished(action);
    otelMonitor.get().writeActionExecuted();
  }

  @Override
  public CoroutineContext getLockStateAsCoroutineContext() {
    return getThreadingSupport().getPermitAsContextElement();
  }

  @Override
  public boolean hasLockStateInContext(CoroutineContext context) {
    return getThreadingSupport().hasPermitAsContextElement(context);
  }

  @NotNull
  private static ThreadingSupport getThreadingSupport() {
    return AnyThreadWriteThreadingSupport.INSTANCE;
  }
}
