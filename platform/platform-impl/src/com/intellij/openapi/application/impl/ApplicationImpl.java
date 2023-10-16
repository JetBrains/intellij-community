// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.CommonBundle;
import com.intellij.codeWithMe.ClientId;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.diagnostic.*;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.*;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.idea.AppExitCodes;
import com.intellij.idea.AppMode;
import com.intellij.idea.IdeaLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.client.ClientAwareComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.PlatformScopesKt;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceUtil;
import com.intellij.platform.ide.bootstrap.StartupErrorReporter;
import com.intellij.platform.ide.bootstrap.StartupUtil;
import com.intellij.psi.util.ReadActionCache;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.*;
import com.intellij.util.concurrency.*;
import com.intellij.util.containers.Stack;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.EDT;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import kotlin.jvm.functions.Function0;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.*;
import sun.awt.SunToolkit;

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
import static com.intellij.util.concurrency.AppExecutorUtil.propagateContextOrCancellation;

@ApiStatus.Internal
public final class ApplicationImpl extends ClientAwareComponentManager implements ApplicationEx, ReadActionListener {
  private static @NotNull Logger getLogger() {
    return Logger.getInstance(ApplicationImpl.class);
  }

  private ReadMostlyRWLock myLock;

  /**
   * @deprecated see {@link ModalityInvokator} notice
   */
  @Deprecated
  private final ModalityInvokator myInvokator = new ModalityInvokatorImpl();

  private final EventDispatcher<ApplicationListener> myDispatcher = EventDispatcher.create(ApplicationListener.class);

  private final boolean myTestModeFlag;
  private final boolean myHeadlessMode;
  private final boolean myCommandLineMode;

  private final boolean myIsInternal;

  // contents modified in write action, read in read action
  private final Stack<Class<?>> myWriteActionsStack = new Stack<>();
  private final TransactionGuardImpl myTransactionGuard = new TransactionGuardImpl();
  private int myWriteStackBase;

  private final ReadActionCacheImpl myReadActionCacheImpl = new ReadActionCacheImpl();

  private final long myStartTime = System.currentTimeMillis();
  private boolean mySaveAllowed;
  private volatile boolean myExitInProgress;

  private final @Nullable Disposable myLastDisposable;  // the last to be disposed

  // defer reading isUnitTest flag until it's initialized
  private static final class Holder {
    private static final int ourDumpThreadsOnLongWriteActionWaiting =
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
    myLock = IdeEventQueue.getInstance().getRwLockHolder().lock;

    registerFakeServices(this);

    myIsInternal = true;
    myTestModeFlag = true;
    myHeadlessMode = isHeadless;
    myCommandLineMode = true;
    mySaveAllowed = false;

    postInit(this);

    myLastDisposable = Disposer.newDisposable();
    // reset back to null only when all components already disposed
    ApplicationManager.setApplication(this, myLastDisposable);
  }

  private volatile boolean myWriteActionPending;

  public ApplicationImpl(@NotNull CoroutineScope parentScope, boolean isInternal) {
    super(parentScope);

    Extensions.setRootArea(getExtensionArea());

    registerFakeServices(this);

    myIsInternal = isInternal;
    myTestModeFlag = false;
    myHeadlessMode = AppMode.isHeadless();
    myCommandLineMode = AppMode.isCommandLine();
    if (!myHeadlessMode) {
      mySaveAllowed = true;
    }

    myLastDisposable = null;
  }

  private static void registerFakeServices(ApplicationImpl app) {
    app.registerServiceInstance(TransactionGuard.class, app.myTransactionGuard, ComponentManagerImpl.fakeCorePluginDescriptor);
    app.registerServiceInstance(Application.class, app, ComponentManagerImpl.fakeCorePluginDescriptor);
    app.registerServiceInstance(ReadActionCache.class, app.myReadActionCacheImpl, ComponentManagerImpl.fakeCorePluginDescriptor);
  }

  @TestOnly
  ReadMostlyRWLock getRwLock() {
    return myLock;
  }

  /**
   * Executes a {@code runnable} in an "impatient" mode.
   * In this mode any attempt to call {@link #runReadAction(Runnable)}
   * would fail (i.e., throw {@link ApplicationUtil.CannotRunReadActionException})
   * if there is a pending write action.
   */
  @Override
  public void executeByImpatientReader(@NotNull Runnable runnable) throws ApplicationUtil.CannotRunReadActionException {
    if (isDispatchThread()) {
      runnable.run();
    }
    else {
      myLock.executeByImpatientReader(runnable);
    }
  }

  @Override
  public boolean isInImpatientReader() {
    return myLock != null && myLock.isInImpatientReader();
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
    return myLock.isReadLockedByThisThread();
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
    Runnable actionDecorated = ClientId.decorateRunnable(action);
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
    Callable<T> actionDecorated = ClientId.decorateCallable(action);
    return AppExecutorUtil.getAppExecutorService().submit(new Callable<>() {
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
          Thread.interrupted(); // reset interrupted status
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
    // Write lock is good too
    ReadMostlyRWLock lock = myLock;
    return lock == null || lock.isWriteThread() && (lock.isWriteIntentLocked() || lock.isWriteAcquired());
  }

  @Deprecated
  @Override
  public @NotNull ModalityInvokator getInvokator() {
    PluginException.reportDeprecatedUsage("Application.getInvokator", "Use `invokeLater` instead");
    return myInvokator;
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
    if (myLock == null) {
      getLogger().error("Do not call invokeLater when app is not yet fully initialized");
    }

    if (propagateContextOrCancellation()) {
      Pair<Runnable, Condition<?>> captured = Propagation.capturePropagationAndCancellationContext(runnable, expired);
      runnable = captured.getFirst();
      expired = captured.getSecond();
    }
    Runnable r = myTransactionGuard.wrapLaterInvocation(runnable, state);
    // Don't need to enable implicit read, as Write Intent lock includes Explicit Read
    LaterInvocator.invokeLater(state, expired, wrapWithRunIntendedWriteAction(r));
  }

  @Override
  public void dispose() {
    //noinspection deprecation
    myDispatcher.getMulticaster().applicationExiting();

    super.dispose();
    // Remove IW lock from EDT as EDT might be re-created, which might lead to deadlock if anybody uses this disposed app
    if (!StartupUtil.isImplicitReadOnEDTDisabled() || isUnitTestMode()) {
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
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull String progressTitle,
                                                     boolean canBeCanceled,
                                                     boolean shouldShowModalWindow,
                                                     @Nullable Project project,
                                                     @Nullable JComponent parentComponent,
                                                     @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText) {
    // disallow running process in a separate thread from a write-action, or a thread will deadlock trying to acquire the read-lock
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
    // In tests and headless mode, there are is modal progress dialog, so IDEA-307428 should not be possible in tests.
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
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    if (isDispatchThread()) {
      runIntendedWriteActionOnCurrentThread(runnable);
      return;
    }
    if (EDT.isCurrentThreadEdt()) {
      runIntendedWriteActionOnCurrentThread(runnable);
      return;
    }

    if (holdsReadLock()) {
      throw new IllegalStateException("Calling invokeAndWait from read-action leads to possible deadlock.");
    }

    Runnable r =
      myTransactionGuard.wrapLaterInvocation(AppScheduledExecutorService.capturePropagationAndCancellationContext(runnable), modalityState);
    LaterInvocator.invokeAndWait(modalityState, wrapWithRunIntendedWriteAction(r));
  }

  private @NotNull Runnable wrapWithRunIntendedWriteAction(@NotNull Runnable runnable) {
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
    return isDispatchThread() ? getCurrentModalityState() : CoreProgressManager.getCurrentThreadProgressModality();
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
    IJTracer tracer = TelemetryManager.getInstance().getTracer(new com.intellij.platform.diagnostic.telemetry.Scope("exitApp", null));
    Span exitSpan = tracer.spanBuilder("application.exit").startSpan();
    boolean force = BitUtil.isSet(flags, FORCE_EXIT);
    try (Scope scope = exitSpan.makeCurrent()) {
      if (!force && !confirmExitIfNeeded(BitUtil.isSet(flags, EXIT_CONFIRMED))) {
        return;
      }

      AppLifecycleListener lifecycleListener = getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
      lifecycleListener.appClosing();

      if (!force && !canExit()) {
        return;
      }

      stopServicePreloading();


      if (BitUtil.isSet(flags, SAVE)) {
        TraceUtil.runWithSpanThrows(tracer, "saveSettingsOnExit",
                                    (span) -> SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(this));
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
        getLogger().error(t);
      }

      LifecycleUsageTriggerCollector.onIdeClose(restart);

      boolean success = true;
      ProjectManagerEx manager = ProjectManagerEx.getInstanceExIfCreated();
      if (manager != null) {
        try {
          boolean projectsClosedSuccessfully = TraceUtil.computeWithSpanThrows(tracer, "disposeProjects", (span) -> {
            return manager.closeAndDisposeAllProjects(!force);
          });
          if (!projectsClosedSuccessfully) {
            success = false;
          }
        }
        catch (Throwable e) {
          getLogger().error(e);
        }
      }
      try {
        scope.close();
        exitSpan.end();
        //noinspection TestOnlyProblems
        disposeContainer();
      }
      catch (Throwable t) {
        getLogger().error(t);
      }

      //noinspection SpellCheckingInspection
      if (!success || isUnitTestMode() || Boolean.getBoolean("idea.test.guimode")) {
        //noinspection SpellCheckingInspection
        if (Boolean.getBoolean("idea.test.guimode")) {
          shutdown();
        }
        return;
      }

      IdeaLogger.dropFrequentExceptionsCaches();
      if (restart && Restarter.isSupported()) {
        try {
          Restarter.scheduleRestart(BitUtil.isSet(flags, ELEVATE), beforeRestart);
        }
        catch (Throwable t) {
          getLogger().error("Restart failed", t);
          StartupErrorReporter.showMessage(BootstrapBundle.message("restart.failed.title"), t);
          if (exitCode == 0) {
            exitCode = AppExitCodes.RESTART_FAILED;
          }
        }
      }
      System.exit(exitCode);
    }
    finally {
      exitSpan.end();
      myExitInProgress = false;
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

  private @NotNull ProgressWindow createProgressWindow(@NotNull @NlsContexts.ProgressTitle String progressTitle,
                                                       boolean canBeCanceled,
                                                       boolean shouldShowModalWindow,
                                                       @Nullable Project project,
                                                       @Nullable JComponent parentComponent,
                                                       @Nullable @NlsContexts.Button String cancelText) {
    ProgressWindow progress = new ProgressWindow(canBeCanceled, !shouldShowModalWindow, project, parentComponent, cancelText);
    // in case of abrupt application exit when 'ProgressManager.getInstance().runProcess(process, progress)' below
    // does not have a chance to run, and as a result the progress won't be disposed
    Disposer.register(this, progress);
    progress.setTitle(progressTitle);
    return progress;
  }

  /**
   * Used for GUI tests to stop `IdeEventQueue` dispatching when `Application` is already disposed of.
   */
  private static void shutdown() {
    IdeEventQueue.applicationClose();
    ShutDownTracker.getInstance().run();
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
    return EDT.isEdt(myLock.writeThread);
  }

  @Override
  public void runIntendedWriteActionOnCurrentThread(@NotNull Runnable action) {
    if (isWriteIntentLockAcquired()) {
      action.run();
    }
    else {
      acquireWriteIntentLock(action.getClass().getName());
      try {
        action.run();
      }
      finally {
        releaseWriteIntentLock();
      }
    }
  }

  @Override
  public <T, E extends Throwable> T runUnlockingIntendedWrite(@NotNull ThrowableComputable<T, E> action) throws E {
    // Do not ever unlock IW in legacy mode (EDT is holding lock at all times)
    if (isWriteIntentLockAcquired() && StartupUtil.isImplicitReadOnEDTDisabled()) {
      releaseWriteIntentLock();
      try {
        return action.compute();
      }
      finally {
        acquireWriteIntentLock(action.getClass().getName());
      }
    }
    else {
      return action.compute();
    }
  }

  @Override
  public void runReadAction(@NotNull Runnable action) {
    IdeEventQueue.getInstance().getRwLockHolder().runReadAction(action);
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computation) {
    return IdeEventQueue.getInstance().getRwLockHolder().runReadAction(computation);
  }

  @Override
  public <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    return IdeEventQueue.getInstance().getRwLockHolder().runReadAction(computation);
  }

  @Override
  public boolean acquireWriteIntentLock(@Nullable String ignored) {
    return IdeEventQueue.getInstance().getRwLockHolder().acquireWriteIntentLock(ignored);
  }

  @Override
  public void releaseWriteIntentLock() {
    IdeEventQueue.getInstance().getRwLockHolder().releaseWriteIntentLock();
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
                                                                       @NotNull java.util.function.Consumer<? super ProgressIndicator> action) {
    return runEdtProgressWriteAction(title, project, parentComponent, IdeBundle.message("action.stop"), action);
  }

  private boolean runEdtProgressWriteAction(@NotNull @NlsContexts.ProgressTitle String title,
                                            @Nullable Project project,
                                            @Nullable JComponent parentComponent,
                                            @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText,
                                            @NotNull java.util.function.Consumer<? super ProgressIndicator> action) {
    return runWriteActionWithClass(action.getClass(), () -> {
      PotemkinProgress indicator = new PotemkinProgress(title, project, parentComponent, cancelText);
      indicator.runInSwingThread(() -> action.accept(indicator));
      return !indicator.isCanceled();
    });
  }

  private <T, E extends Throwable> T runWriteActionWithClass(@NotNull Class<?> clazz, @NotNull ThrowableComputable<T, E> computable)
    throws E {
    startWrite(clazz);
    try {
      return computable.compute();
    }
    finally {
      endWrite(clazz);
    }
  }

  @Override
  public void runWriteAction(@NotNull Runnable action) {
    Class<? extends Runnable> clazz = action.getClass();
    startWrite(clazz);
    try {
      action.run();
    }
    finally {
      endWrite(clazz);
    }
  }

  @Override
  public <T> T runWriteAction(@NotNull Computable<T> computation) {
    return runWriteActionWithClass(computation.getClass(), () -> computation.compute());
  }

  @Override
  public <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    return runWriteActionWithClass(computation.getClass(), computation);
  }

  @Override
  public boolean hasWriteAction(@NotNull Class<?> actionClass) {
    assertReadAccessAllowed();

    for (int i = myWriteActionsStack.size() - 1; i >= 0; i--) {
      Class<?> action = myWriteActionsStack.get(i);
      if (actionClass == action || ReflectionUtil.isAssignable(actionClass, action)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public <T, E extends Throwable> T runWriteIntentReadAction(@NotNull ThrowableComputable<T, E> computation) {
    return IdeEventQueue.getInstance().getRwLockHolder().runWriteIntentReadAction(computation);
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
    ReadMostlyRWLock lock = myLock;
    return lock == null || myLock.isReadAllowed();
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
  public void assertTimeConsuming() {
    assertIsNonDispatchThread();
  }

  @Override
  public boolean tryRunReadAction(@NotNull Runnable action) {
    //if we are inside read action, do not try to acquire read lock again since it will deadlock if there is a pending writeAction
    ReadMostlyRWLock lock = myLock;
    ReadMostlyRWLock.Reader status = lock.startTryRead();
    if (status != null && !status.readRequested) {
      return false;
    }
    try {
      action.run();
    }
    finally {
      myReadActionCacheImpl.clear();
      if (status != null) {
        lock.endRead(status);
      }
      otelMonitor.get().readActionExecuted();
    }
    return true;
  }

  private void startWrite(@NotNull Class<?> clazz) {
    assertNotInsideListener();
    myWriteActionPending = true;
    try {
      ActivityTracker.getInstance().inc();
      fireBeforeWriteActionStart(clazz);

      // otherwise (when myLock is locked) there's a nesting write action:
      // - allow it,
      // - fire listeners for it (somebody can rely on having listeners fired for each write action)
      // - but do not re-acquire any locks because it could be deadlock-level dangerous
      ReadMostlyRWLock lock = myLock;
      if (!lock.isWriteAcquired()) {
        int delay = Holder.ourDumpThreadsOnLongWriteActionWaiting;
        Future<?> reportSlowWrite = delay <= 0 ? null :
                                    AppExecutorUtil.getAppScheduledExecutorService()
                                      .scheduleWithFixedDelay(() -> PerformanceWatcher.getInstance().dumpThreads("waiting", true, true),
                                                              delay, delay, TimeUnit.MILLISECONDS);
        long t = getLogger().isDebugEnabled() ? System.currentTimeMillis() : 0;
        lock.writeLock();
        if (getLogger().isDebugEnabled()) {
          long elapsed = System.currentTimeMillis() - t;
          if (elapsed != 0) {
            getLogger().debug("Write action wait time: " + elapsed);
          }
        }
        if (reportSlowWrite != null) {
          reportSlowWrite.cancel(false);
        }
      }
    }
    finally {
      myWriteActionPending = false;
    }

    myWriteActionsStack.push(clazz);
    fireWriteActionStarted(clazz);
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
  public @NotNull AccessToken acquireReadActionLock() {
    PluginException.reportDeprecatedUsage("Application.acquireReadActionLock", "Use `runReadAction()` instead");

    // if we are inside read action, do not try to acquire read lock again since it will deadlock if there is a pending writeAction
    return isWriteIntentLockAcquired() || myLock.isReadLockedByThisThread() ? AccessToken.EMPTY_ACCESS_TOKEN : new ReadAccessToken();
  }

  @Override
  public boolean isWriteActionPending() {
    return myWriteActionPending;
  }

  @Override
  public boolean isWriteAccessAllowed() {
    ReadMostlyRWLock lock = myLock;
    return lock == null || lock.isWriteThread() && lock.isWriteAcquired();
  }

  private void assertNotInsideListener() {
    if (myWriteActionPending) {
      throw new IllegalStateException("Must not start write action from inside write action listener");
    }
  }

  private void endWrite(@NotNull Class<?> clazz) {
    try {
      fireWriteActionFinished(clazz);
      // fire listeners before popping stack because if somebody starts a write-action in a listener,
      // there is a danger of releasing the write-lock before other listeners have been run (since write lock became non-reentrant).
    }
    finally {
      myWriteActionsStack.pop();
      if (myWriteActionsStack.size() == myWriteStackBase) {
        myLock.writeUnlock();
      }
      if (myWriteActionsStack.isEmpty()) {
        fireAfterWriteActionFinished(clazz);
      }
      otelMonitor.get().writeActionExecuted();
    }
  }

  @Override
  public @NotNull AccessToken acquireWriteActionLock(@NotNull Class<?> clazz) {
    PluginException.reportDeprecatedUsage("Application#acquireWriteActionLock", "Use `runWriteAction()` instead");

    return new WriteAccessToken(clazz);
  }

  private final class WriteAccessToken extends AccessToken {
    private final @NotNull Class<?> clazz;

    WriteAccessToken(@NotNull Class<?> clazz) {
      this.clazz = clazz;
      startWrite(clazz);
      markThreadNameInStackTrace();
    }

    @Override
    public void finish() {
      try {
        endWrite(clazz);
      }
      finally {
        unmarkThreadNameInStackTrace();
      }
    }

    private static void markThreadNameInStackTrace() {
      String id = id();

      Thread thread = Thread.currentThread();
      thread.setName(thread.getName() + id);
    }

    private static void unmarkThreadNameInStackTrace() {
      String id = id();

      Thread thread = Thread.currentThread();
      String name = thread.getName();
      name = StringUtil.replace(name, id, "");
      thread.setName(name);
    }

    private static @NotNull String id() {
      return " [WriteAccessToken]";
    }
  }

  /**
   * @deprecated use {@link #runReadAction(Runnable)} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  private final class ReadAccessToken extends AccessToken {
    private final ReadMostlyRWLock.Reader myReader;

    private ReadAccessToken() {
      myReader = myLock.startRead();
    }

    @Override
    public void finish() {
      myReadActionCacheImpl.clear();
      myLock.endRead(myReader);
      otelMonitor.get().readActionExecuted();
    }
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
  public void executeSuspendingWriteAction(@Nullable Project project,
                                           @NotNull @NlsContexts.DialogTitle String title,
                                           @NotNull Runnable runnable) {
    assertWriteIntentLockAcquired();
    ReadMostlyRWLock lock = myLock;
    if (!lock.isWriteAcquired()) {
      runModalProgress(project, title, runnable);
      return;
    }

    int prevBase = myWriteStackBase;
    myWriteStackBase = myWriteActionsStack.size();
    try {
      lock.writeSuspendWhilePumpingIdeEventQueueHopingForTheBest(() -> runModalProgress(project, title, runnable));
    }
    finally {
      myWriteStackBase = prevBase;
    }
  }

  @Override
  public boolean isWriteActionInProgress() {
    return myLock.isWriteAcquired();
  }

  private static void runModalProgress(@Nullable Project project,
                                       @NotNull @NlsContexts.DialogTitle String title,
                                       @NotNull Runnable runnable) {
    ProgressManager.getInstance().run(new Task.Modal(project, title, false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        runnable.run();
      }
    });
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
    boolean hasLock = myLock != null;
    boolean writeActionPending = hasLock && isWriteActionPending();
    boolean writeActionInProgress = hasLock && isWriteActionInProgress();
    boolean writeAccessAllowed = hasLock && isWriteAccessAllowed();
    return "Application"
           + (getContainerState().get() == ContainerState.COMPONENT_CREATED ? "" : " (containerState " + getContainerStateName() + ") ")
           + (isUnitTestMode() ? " (unit test)" : "")
           + (isInternal() ? " (internal)" : "")
           + (isHeadlessEnvironment() ? " (headless)" : "")
           + (isCommandLine() ? " (command line)" : "")
           + (hasLock ?
              (writeActionPending || writeActionInProgress || writeAccessAllowed ? " (WA" +
                                                                                   (writeActionPending ? " pending" : "") +
                                                                                   (writeActionInProgress ? " inProgress" : "") +
                                                                                   (writeAccessAllowed ? " allowed" : "") +
                                                                                   ")" : "")
                      : " (lock is not ready)"
           )
           + (isReadAccessAllowed() ? " (RA allowed)" : "")
           + (StartupUtil.isImplicitReadOnEDTDisabled() ? " (IR on EDT disabled)" : "")
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

  @Override
  public void runWithoutImplicitRead(@NotNull Runnable runnable) {
    IdeEventQueue.getInstance().getRwLockHolder().runWithoutImplicitRead(runnable);
  }

  @Override
  public void runWithImplicitRead(@NotNull Runnable runnable) {
    IdeEventQueue.getInstance().getRwLockHolder().runWithImplicitRead(runnable);
  }

  @ApiStatus.Internal
  public static void postInit(@NotNull ApplicationImpl app) {
    app.myLock = IdeEventQueue.getInstance().getRwLockHolder().lock;
    AtomicBoolean reported = new AtomicBoolean();
    IdeEventQueue.getInstance().addPostprocessor(e -> {
      if (app.isWriteAccessAllowed() && reported.compareAndSet(false, true)) {
        getLogger().error("AWT events are not allowed inside write action: " + e);
      }
      return true;
    }, app.getCoroutineScope());

    IdeEventQueue.getInstance().getRwLockHolder().addReadActionListener(app, app);

    app.addApplicationListener(new ApplicationListener() {
      @Override
      public void afterWriteActionFinished(@NotNull Object action) {
        reported.set(false);
      }
    }, app);
  }

  @Override
  public void flushNativeEventQueue() {
    SunToolkit.flushPendingEvents();
  }

  @Override
  public void readActionFinished(@NotNull Object action) {
    myReadActionCacheImpl.clear();
  }

  @Override
  public void afterReadActionFinished(@NotNull Object action) {
    otelMonitor.get().readActionExecuted();
  }
}
