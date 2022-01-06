// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.BundleBase;
import com.intellij.CommonBundle;
import com.intellij.codeWithMe.ClientId;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.diagnostic.*;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.*;
import com.intellij.ide.plugins.ContainerDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.ApplicationLoader;
import com.intellij.idea.IdeaLogger;
import com.intellij.idea.Main;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.client.ClientAwareComponentManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.concurrency.Propagation;
import com.intellij.util.containers.Stack;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.EdtInvocationManager;
import kotlin.sequences.Sequence;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.*;
import sun.awt.AWTAccessor;
import sun.awt.AWTAutoShutdown;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.intellij.util.concurrency.AppExecutorUtil.propagateContextOrCancellation;
import static kotlinx.coroutines.JobKt.Job;

@ApiStatus.Internal
public class ApplicationImpl extends ClientAwareComponentManager implements ApplicationEx {
  // do not use PluginManager.processException() because it can force app to exit, but we want just to log an error and continue
  private static final Logger LOG = Logger.getInstance(ApplicationImpl.class);

  /**
   * This boolean controls whether to use thread(s) other than EDT for acquiring IW lock (i.e. running write actions) or not.
   * If value is {@code false}, IW lock will be granted on EDT at all times, guaranteeing the same execution model as before
   * IW lock introduction.
   */
  public static final boolean USE_SEPARATE_WRITE_THREAD = StartupUtil.isUsingSeparateWriteThread();

  final ReadMostlyRWLock myLock;

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

  private final long myStartTime = System.currentTimeMillis();
  private boolean mySaveAllowed;
  private volatile boolean myExitInProgress;

  private final Disposable myLastDisposable = Disposer.newDisposable();  // the last to be disposed

  // defer reading isUnitTest flag until it's initialized
  private static class Holder {
    private static final int ourDumpThreadsOnLongWriteActionWaiting =
      ApplicationManager.getApplication().isUnitTestMode() ? 0 : Integer.getInteger("dump.threads.on.long.write.action.waiting", 0);
  }

  private final ExecutorService ourThreadExecutorsService = AppExecutorUtil.getAppExecutorService();
  private static final String WAS_EVER_SHOWN = "was.ever.shown";

  public ApplicationImpl(boolean isInternal, boolean isUnitTestMode, boolean isHeadless, boolean isCommandLine) {
    super(null);

    registerServiceInstance(TransactionGuard.class, myTransactionGuard, ComponentManagerImpl.fakeCorePluginDescriptor);
    registerServiceInstance(ApplicationInfo.class, ApplicationInfoImpl.getShadowInstance(), ComponentManagerImpl.fakeCorePluginDescriptor);
    registerServiceInstance(Application.class, this, ComponentManagerImpl.fakeCorePluginDescriptor);

    if (isUnitTestMode || isInternal) {
      BundleBase.assertOnMissedKeys(true);
    }

    // do not crash AWT on exceptions
    AWTExceptionHandler.register();

    Disposer.setDebugMode(isInternal || isUnitTestMode || Disposer.isDebugDisposerOn());

    myIsInternal = isInternal;
    myTestModeFlag = isUnitTestMode;
    myHeadlessMode = isHeadless;
    myCommandLineMode = isCommandLine;
    if (isUnitTestMode) {
      //noinspection TestOnlyProblems
      Logger.setUnitTestMode();
    }

    mySaveAllowed = !isUnitTestMode && !isHeadless;

    // reset back to null only when all components already disposed
    ApplicationManager.setApplication(this, myLastDisposable);

    AtomicReference<Thread> edtThread = new AtomicReference<>();
    EdtInvocationManager.invokeAndWaitIfNeeded(() -> {
      preventAwtAutoShutdown(this);
      edtThread.set(Thread.currentThread());
    });
    myLock = new ReadMostlyRWLock(edtThread.get());
    // Acquire IW lock on EDT indefinitely in legacy mode
    if (!USE_SEPARATE_WRITE_THREAD || isUnitTestMode) {
      EdtInvocationManager.invokeAndWaitIfNeeded(() -> acquireWriteIntentLock(getClass().getName()));
    }

    NoSwingUnderWriteAction.watchForEvents(this);
  }

  // this constructor must be called only by ApplicationLoader
  public ApplicationImpl(boolean isInternal, boolean isHeadless, boolean isCommandLine, @NotNull Thread edtThread) {
    super(null);

    registerServiceInstance(TransactionGuard.class, myTransactionGuard, ComponentManagerImpl.fakeCorePluginDescriptor);
    registerServiceInstance(ApplicationInfo.class, ApplicationInfoImpl.getShadowInstance(), ComponentManagerImpl.fakeCorePluginDescriptor);
    registerServiceInstance(Application.class, this, ComponentManagerImpl.fakeCorePluginDescriptor);

    myIsInternal = isInternal;
    myTestModeFlag = false;
    myHeadlessMode = isHeadless;
    myCommandLineMode = isCommandLine;
    if (!isHeadless) {
      mySaveAllowed = true;
    }

    Activity activity = StartUpMeasurer.startActivity("AppDelayQueue instantiation", ActivityCategory.DEFAULT);
    myLock = new ReadMostlyRWLock(edtThread);
    // Acquire IW lock on EDT indefinitely in legacy mode
    if (!USE_SEPARATE_WRITE_THREAD) {
      EventQueue.invokeLater(() -> acquireWriteIntentLock(getClass().getName()));
    }
    activity.end();

    NoSwingUnderWriteAction.watchForEvents(this);

    // reset back to null only when all components already disposed
    ApplicationManager.setApplication(this, myLastDisposable);
  }

  public static void preventAwtAutoShutdown(@NotNull Disposable parentDisposable) {
    // Instantiate `AppDelayQueue` which starts "periodic tasks thread" which we'll mark busy to prevent this EDT from dying.
    // That thread was chosen because we know for sure it's running.
    Thread thread = AppScheduledExecutorService.getPeriodicTasksThread();
    AWTAutoShutdown.getInstance().notifyThreadBusy(thread); // needed for EDT not to exit suddenly
    Disposer.register(parentDisposable, () -> {
      AWTAutoShutdown.getInstance().notifyThreadFree(thread); // allow for EDT to exit - needed for Upsource
    });
  }

  /**
   * Executes a {@code runnable} in an "impatient" mode.
   * In this mode any attempt to call {@link #runReadAction(Runnable)}
   * would fail (i.e. throw {@link ApplicationUtil.CannotRunReadActionException})
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
    return myLock.isInImpatientReader();
  }

  @TestOnly
  public void disposeContainer() {
    runWriteAction(() -> {
      startDispose();
      Disposer.dispose(this);
    });
    Disposer.assertIsEmpty();
  }

  private boolean disposeSelf(boolean checkCanCloseProject) {
    ProjectManagerEx manager = ProjectManagerEx.getInstanceExIfCreated();
    if (manager != null) {
      try {
        if (!manager.closeAndDisposeAllProjects(checkCanCloseProject)) {
          return false;
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    //noinspection TestOnlyProblems
    disposeContainer();
    return true;
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
  public final boolean isLightEditMode() {
    return Main.isLightEdit();
  }

  @Override
  public @NotNull Future<?> executeOnPooledThread(@NotNull Runnable action) {
    Runnable actionDecorated = ClientId.decorateRunnable(action);
    return ourThreadExecutorsService.submit(new Runnable() {
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
          LOG.error(e);
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
    return ourThreadExecutorsService.submit(new Callable<>() {
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
          LOG.error(e);
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
    return isWriteThread() && EDT.isCurrentThreadEdt();
  }

  @Override
  public boolean isWriteThread() {
    return myLock.isWriteThread();
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
    invokeLater(runnable, ModalityState.defaultModalityState(), expired);
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
    invokeLater(runnable, state, getDisposed());
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition<?> expired) {
    if (!(runnable instanceof FutureTask) && propagateContextOrCancellation()) {
      // see com.intellij.util.concurrency.AppScheduledExecutorService#handleCommand
      if (Propagation.propagateCancellation()) {
        //noinspection TestOnlyProblems
        Job job = Cancellation.currentJob();
        CompletableJob childJob = Job(job);
        expired = cancelIfExpired(expired, childJob);
        runnable = new CancellationRunnable(childJob, runnable);
      }
      runnable = Propagation.handleContext(runnable);
    }
    Runnable r = myTransactionGuard.wrapLaterInvocation(runnable, state);
    LaterInvocator.invokeLater(state, expired, wrapWithRunIntendedWriteAction(r));
  }

  private static <T> @NotNull Condition<T> cancelIfExpired(@NotNull Condition<T> expiredCondition, @NotNull Job childJob) {
    return (t) -> {
      boolean expired = expiredCondition.value(t);
      if (expired) {
        // Cancel to avoid a hanging child job which will prevent completion of the parent one.
        childJob.cancel(null);
        return true;
      }
      else {
        // Treat runnable as expired if its job was already cancelled.
        return childJob.isCancelled();
      }
    };
  }

  @Override
  public final void load() {
    PluginManagerCore.scheduleDescriptorLoading();
    Sequence<IdeaPluginDescriptorImpl> modules = PluginManagerCore.getInitPluginFuture().join().getEnabledModules();

    registerComponents(modules, this, null, null);
    ApplicationLoader.initConfigurationStore(this);
    preloadServices(modules, "", false).sync.join();
    loadComponents();
    ForkJoinTask.invokeAll(ApplicationLoader.callAppInitialized(this));
  }

  public final void loadComponents() {
    createComponents(null);
    StartUpMeasurer.setCurrentState(LoadingState.COMPONENTS_LOADED);
  }

  @Override
  public void dispose() {
    //noinspection deprecation
    myDispatcher.getMulticaster().applicationExiting();

    ShutDownTracker.getInstance().ensureStopperThreadsFinished();

    super.dispose();
    // Remove IW lock from EDT as EDT might be re-created, which might lead to deadlock if anybody uses this disposed app
    if (!USE_SEPARATE_WRITE_THREAD || isUnitTestMode()) {
      invokeLater(() -> releaseWriteIntentLock(), ModalityState.NON_MODAL);
    }

    // FileBasedIndexImpl can schedule some more activities to execute, so, shutdown executor only after service disposing
    AppExecutorUtil.shutdownApplicationScheduledExecutorService();

    Disposer.dispose(myLastDisposable);
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
      LOG.debug("Starting process with progress from within write action makes no sense");
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
      runnable.run();
      return;
    }
    if (SwingUtilities.isEventDispatchThread()) {
      runIntendedWriteActionOnCurrentThread(runnable);
      return;
    }

    if (holdsReadLock()) {
      throw new IllegalStateException("Calling invokeAndWait from read-action leads to possible deadlock.");
    }

    Runnable r = myTransactionGuard.wrapLaterInvocation(runnable, modalityState);
    LaterInvocator.invokeAndWait(modalityState, wrapWithRunIntendedWriteAction(r));
  }

  @NotNull
  private Runnable wrapWithRunIntendedWriteAction(@NotNull Runnable runnable) {
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
    invokeAndWait(runnable, ModalityState.defaultModalityState());
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
    return ModalityState.NON_MODAL;
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
  public final void restart(boolean exitConfirmed) {
    int flags = SAVE;
    if (exitConfirmed) {
      flags |= EXIT_CONFIRMED;
    }
    exit(flags, true, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  @Override
  public final void restart(boolean exitConfirmed, boolean elevate) {
    int flags = SAVE;
    if (exitConfirmed) {
      flags |= EXIT_CONFIRMED;
    }
    if (elevate) {
      flags |= ELEVATE;
    }
    exit(flags, true, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  /**
   * There are two ways we can get an exit notification.
   *  1. From user input i.e. ExitAction
   *  2. From the native system.
   *  We should not process any quit notifications if we are handling another one
   *
   *  Note: there are possible scenarios when we get a quit notification at a moment when another
   *  quit message is shown. In that case, showing multiple messages sounds contra-intuitive as well
   */
  @Override
  public final void exit(boolean force, boolean exitConfirmed, boolean restart) {
    int flags = SAVE;
    if (force) {
      flags |= FORCE_EXIT;
    }
    if (exitConfirmed) {
      flags |= EXIT_CONFIRMED;
    }
    exit(flags, restart, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public void restart(int flags, String @NotNull [] beforeRestart) {
    exit(flags, true, beforeRestart);
  }

  @Override
  public final void exit(int flags) {
    exit(flags, false, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private void exit(int flags, boolean restart, String @NotNull [] beforeRestart) {
    if (!BitUtil.isSet(flags, FORCE_EXIT) &&
        (myExitInProgress || (!BitUtil.isSet(flags, EXIT_CONFIRMED) && getDefaultModalityState() != ModalityState.NON_MODAL))) {
      return;
    }

    myExitInProgress = true;
    if (isDispatchThread()) {
      doExit(flags, restart, beforeRestart);
    }
    else {
      invokeLater(() -> doExit(flags, restart, beforeRestart), ModalityState.NON_MODAL);
    }
  }

  @Override
  public final boolean isExitInProgress() {
    return myExitInProgress;
  }

  private void doExit(int flags, boolean restart, String @NotNull [] beforeRestart) {
    boolean force = BitUtil.isSet(flags, FORCE_EXIT);
    try {
      if (!force && !confirmExitIfNeeded(BitUtil.isSet(flags, EXIT_CONFIRMED))) {
        return;
      }

      AppLifecycleListener lifecycleListener = getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
      lifecycleListener.appClosing();

      if (!force && !canExit()) {
        return;
      }

      stopServicePreloading();

      if (isInstantShutdownPossible()) {
        for (Frame frame : Frame.getFrames()) {
          frame.setVisible(false);
        }
      }

      try {
        lifecycleListener.appWillBeClosed(restart);
      }
      catch (Throwable t) {
        LOG.error(t);
      }

      LifecycleUsageTriggerCollector.onIdeClose(restart);

      if (BitUtil.isSet(flags, SAVE)) {
        SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(this);
      }

      boolean success = disposeSelf(!force);
      //noinspection SpellCheckingInspection
      if (!success || isUnitTestMode() || Boolean.getBoolean("idea.test.guimode")) {
        //noinspection SpellCheckingInspection
        if (Boolean.getBoolean("idea.test.guimode")) {
          shutdown();
        }
        return;
      }

      IdeaLogger.dropFrequentExceptionsCaches();
      int exitCode = 0;
      if (restart && Restarter.isSupported()) {
        try {
          Restarter.scheduleRestart(BitUtil.isSet(flags, ELEVATE), beforeRestart);
        }
        catch (Throwable t) {
          LOG.error("Restart failed", t);
          Main.showMessage(BootstrapBundle.message("restart.failed.title"), t);
          exitCode = Main.RESTART_FAILED;
        }
      }
      System.exit(exitCode);
    }
    finally {
      myExitInProgress = false;
    }
  }

  private static boolean isInstantShutdownPossible() {
    if (!Registry.is("ide.instant.shutdown")) {
      return false;
    }

    return !ProgressManager.getInstance().hasProgressIndicator();
  }

  public final @NotNull CompletableFuture<@NotNull ProgressWindow> createProgressWindowAsyncIfNeeded(
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
      LOG.error("Headless application has been completed but background tasks are still running! Application will be terminated." +
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
      exitConfirmed = MessageDialogBuilder.okCancel(ApplicationBundle.message("exit.confirm.title"), ApplicationBundle.message("exit.confirm.prompt"))
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

  public boolean isCurrentWriteOnEdt() {
    return EDT.isEdt(myLock.writeThread);
  }

  @Override
  public void invokeLaterOnWriteThread(@NotNull Runnable action, @NotNull ModalityState modal) {
    invokeLaterOnWriteThread(action, modal, getDisposed());
  }

  @Override
  public void invokeLaterOnWriteThread(@NotNull Runnable action, @NotNull ModalityState modal, @NotNull Condition<?> expired) {
    Runnable r = myTransactionGuard.wrapLaterInvocation(action, modal);
    // EDT == Write Thread in legacy mode
    LaterInvocator.invokeLater(modal, expired, wrapWithRunIntendedWriteAction(r));
  }

  @Override
  public void invokeLaterOnWriteThread(@NotNull Runnable action) {
    invokeLaterOnWriteThread(action, ModalityState.defaultModalityState());
  }

  @Override
  public void runIntendedWriteActionOnCurrentThread(@NotNull Runnable action) {
    if (isWriteThread()) {
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
    if (isWriteThread() && USE_SEPARATE_WRITE_THREAD) {
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
    ReadMostlyRWLock.Reader status = myLock.startRead();
    try {
      action.run();
    }
    finally {
      if (status != null) {
        myLock.endRead(status);
      }
    }
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computation) {
    ReadMostlyRWLock.Reader status = myLock.startRead();
    try {
      return computation.compute();
    }
    finally {
      if (status != null) {
        myLock.endRead(status);
      }
    }
  }

  @Override
  public <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    ReadMostlyRWLock.Reader status = myLock.startRead();
    try {
      return computation.compute();
    }
    finally {
      if (status != null) {
        myLock.endRead(status);
      }
    }
  }

  @Override
  public void acquireWriteIntentLock(@NotNull String invokedClassFqn) {
    myLock.writeIntentLock();
  }

  @Override
  public void releaseWriteIntentLock() {
    myLock.writeIntentUnlock();
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
    if (!USE_SEPARATE_WRITE_THREAD) {
      // Use the Potemkin progress in legacy mode; in the new model, such execution will always move to a separate thread.
      return runWriteActionWithClass(action.getClass(), ()->{
        PotemkinProgress indicator = new PotemkinProgress(title, project, parentComponent, cancelText);
        indicator.runInSwingThread(() -> action.accept(indicator));
        return !indicator.isCanceled();
      });
    }

    ProgressWindow progress = createProgressWindow(title, cancelText != null, true, project, parentComponent, cancelText);

    ProgressResult<Object> result = new ProgressRunner<>(() -> runWriteAction(() -> action.accept(progress)))
      .sync()
      .onThread(ProgressRunner.ThreadToUse.WRITE)
      .withProgress(progress)
      .modal()
      .submitAndGet();

    if (result.getThrowable() instanceof RuntimeException) {
      throw (RuntimeException)result.getThrowable();
    }

    return true;
  }

  private <T,E extends Throwable> T runWriteActionWithClass(@NotNull Class<?> clazz, @NotNull ThrowableComputable<T, E> computable) throws E {
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
  public void assertReadAccessAllowed() {
    if (!isReadAccessAllowed()) {
      LOG.error(
        "Read access is allowed from inside read-action (or EDT) only" +
        " (see com.intellij.openapi.application.Application.runReadAction())",
        "Current thread: " + describe(Thread.currentThread()),
        "Dispatch thread: " + EventQueue.isDispatchThread() + "; isDispatchThread(): " + isDispatchThread(),
        "SystemEventQueueThread: " + describe(getEventQueueThread()));
    }
  }

  @Override
  public void assertReadAccessNotAllowed() {
    if (isReadAccessAllowed()) {
      LOG.error(
        "Read access is not allowed",
        "Current thread: " + describe(Thread.currentThread()),
        "Dispatch thread: " + EventQueue.isDispatchThread() + "; isDispatchThread(): " + isDispatchThread(),
        "SystemEventQueueThread: " + describe(getEventQueueThread()));
    }
  }

  private static String describe(Thread o) {
    return o == null ? "null" : o + " " + System.identityHashCode(o);
  }

  private static Thread getEventQueueThread() {
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    return AWTAccessor.getEventQueueAccessor().getDispatchThread(eventQueue);
  }

  @Override
  public boolean isReadAccessAllowed() {
    return isWriteThread() || myLock.isReadLockedByThisThread();
  }

  @Override
  public void assertIsDispatchThread() {
    if (isDispatchThread()) return;
    if (ShutDownTracker.isShutdownHookRunning()) return;
    throwThreadAccessException("Access is allowed from event dispatch thread only");
  }

  @Override
  public void assertIsNonDispatchThread() {
    if (isUnitTestMode() || isHeadlessEnvironment()) return;
    if (!isDispatchThread()) return;
    if (ShutDownTracker.isShutdownHookRunning()) return;
    throwThreadAccessException("Access from event dispatch thread is not allowed.");
  }

  private static void throwThreadAccessException(@NotNull String message) {
    throw new RuntimeExceptionWithAttachments(
      message,
      "EventQueue.isDispatchThread()=" + EventQueue.isDispatchThread() +
      "\nCurrent thread: " + describe(Thread.currentThread()) +
      "\nSystemEventQueueThread: " + describe(getEventQueueThread()),
      new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString()));
  }

  @Override
  public void assertIsWriteThread() {
    if (isWriteThread()) return;
    if (ShutDownTracker.isShutdownHookRunning()) return;
    throw new RuntimeExceptionWithAttachments(
      "Access is allowed from write thread only.",
      "EventQueue.isDispatchThread()=" + EventQueue.isDispatchThread() +
      "\nCurrent thread: " + describe(Thread.currentThread()) +
      "\nWrite thread: " + describe(myLock.writeThread),
      new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString()));
  }

  @Override
  public void assertIsDispatchThread(@Nullable JComponent component) {
    if (component == null) return;

    if (isDispatchThread()) {
      return;
    }

    if (Boolean.TRUE.equals(component.getClientProperty(WAS_EVER_SHOWN))) {
      assertIsDispatchThread();
    }
    else {
      JRootPane root = component.getRootPane();
      if (root != null) {
        component.putClientProperty(WAS_EVER_SHOWN, Boolean.TRUE);
        assertIsDispatchThread();
      }
    }
  }

  @Override
  public void assertTimeConsuming() {
    if (myTestModeFlag || myHeadlessMode || ShutDownTracker.isShutdownHookRunning()) return;
    LOG.assertTrue(!isDispatchThread(), "This operation is time consuming and must not be called on EDT");
  }

  @Override
  public boolean tryRunReadAction(@NotNull Runnable action) {
    //if we are inside read action, do not try to acquire read lock again since it will deadlock if there is a pending writeAction
    ReadMostlyRWLock.Reader status = myLock.startTryRead();
    if (status != null && !status.readRequested) {
      return false;
    }
    try {
      action.run();
    }
    finally {
      if (status != null) {
        myLock.endRead(status);
      }
    }
    return true;
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
      ApplicationActivationStateManager.updateState(this, activeWindow);
    }

    return ApplicationActivationStateManager.isActive();
  }

  @Override
  public @NotNull AccessToken acquireReadActionLock() {
    PluginException.reportDeprecatedUsage("Application.acquireReadActionLock", "Use `runReadAction()` instead");

    // if we are inside read action, do not try to acquire read lock again since it will deadlock if there is a pending writeAction
    return isWriteThread() || myLock.isReadLockedByThisThread() ? AccessToken.EMPTY_ACCESS_TOKEN : new ReadAccessToken();
  }

  private volatile boolean myWriteActionPending;

  @Override
  public boolean isWriteActionPending() {
    return myWriteActionPending;
  }

  private void startWrite(@NotNull Class<?> clazz) {
    assertIsWriteThread();
    boolean writeActionPending = myWriteActionPending;
    myWriteActionPending = true;
    try {
      ActivityTracker.getInstance().inc();
      fireBeforeWriteActionStart(clazz);

      if (!myLock.isWriteLocked()) {
        int delay = Holder.ourDumpThreadsOnLongWriteActionWaiting;
        Future<?> reportSlowWrite = delay <= 0 ? null :
                                    AppExecutorUtil.getAppScheduledExecutorService()
                                      .scheduleWithFixedDelay(() -> PerformanceWatcher.getInstance().dumpThreads("waiting", true),
                                                              delay, delay, TimeUnit.MILLISECONDS);
        long t = LOG.isDebugEnabled() ? System.currentTimeMillis() : 0;
        myLock.writeLock();
        if (LOG.isDebugEnabled()) {
          long elapsed = System.currentTimeMillis() - t;
          if (elapsed != 0) {
            LOG.debug("Write action wait time: " + elapsed);
          }
        }
        if (reportSlowWrite != null) {
          reportSlowWrite.cancel(false);
        }
      }
    }
    finally {
      myWriteActionPending = writeActionPending;
    }

    myWriteActionsStack.push(clazz);
    fireWriteActionStarted(clazz);
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
    }
  }

  @Override
  public @NotNull AccessToken acquireWriteActionLock(@NotNull Class<?> clazz) {
    PluginException.reportDeprecatedUsage("Application#acquireWriteActionLock", "Use `runWriteAction()` instead");

    return new WriteAccessToken(clazz);
  }

  private class WriteAccessToken extends AccessToken {
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

    private void markThreadNameInStackTrace() {
      String id = id();

      if (id != null) {
        Thread thread = Thread.currentThread();
        thread.setName(thread.getName() + id);
      }
    }

    private void unmarkThreadNameInStackTrace() {
      String id = id();

      if (id != null) {
        Thread thread = Thread.currentThread();
        String name = thread.getName();
        name = StringUtil.replace(name, id, "");
        thread.setName(name);
      }
    }

    private @Nullable String id() {
      Class<?> aClass = getClass();
      String name = aClass.getName();
      name = name.substring(name.lastIndexOf('.') + 1);
      name = name.substring(name.lastIndexOf('$') + 1);
      if (!name.equals("AccessToken")) {
        return " [" + name+"]";
      }
      return null;
    }
  }

  /**
   * @deprecated use {@link #runReadAction(Runnable)} instead
   */
  @Deprecated
  private final class ReadAccessToken extends AccessToken {
    private final ReadMostlyRWLock.Reader myReader;

    private ReadAccessToken() {
      myReader = myLock.startRead();
    }

    @Override
    public void finish() {
      myLock.endRead(myReader);
    }
  }

  @Override
  public void assertWriteAccessAllowed() {
    LOG.assertTrue(isWriteAccessAllowed(),
                   "Write access is allowed inside write-action only (see com.intellij.openapi.application.Application.runWriteAction())");
  }

  @Override
  public boolean isWriteAccessAllowed() {
    return isWriteThread() && myLock.isWriteLocked();
  }

  @Override
  public boolean isWriteActionInProgress() {
    return myLock.isWriteLocked();
  }

  /**
   * If called inside a write-action, executes the given code under modal progress with write-lock released (e.g. to allow for read-action parallelization).
   * It's the caller's responsibility to invoke this method only when the model is in an internally consistent state,
   * so that background threads with read actions don't see half-baked PSI/VFS/etc. The runnable may perform write-actions itself;
   * callers should be ready for those.
   */
  public void executeSuspendingWriteAction(@Nullable Project project, @NotNull @NlsContexts.DialogTitle String title, @NotNull Runnable runnable) {
    assertIsWriteThread();
    if (!myLock.isWriteLocked()) {
      runModalProgress(project, title, runnable);
      return;
    }

    int prevBase = myWriteStackBase;
    myWriteStackBase = myWriteActionsStack.size();
    try {
      myLock.writeSuspendWhilePumpingIdeEventQueueHopingForTheBest(()->runModalProgress(project, title, runnable));
    }
    finally {
      myWriteStackBase = prevBase;
    }
  }

  private static void runModalProgress(@Nullable Project project, @NotNull @NlsContexts.DialogTitle String title, @NotNull Runnable runnable) {
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
    return "Application (containerState=" + getContainerStateName() + ") " +
           (isUnitTestMode() ? " (Unit test)" : "") +
           (isInternal() ? " (Internal)" : "") +
           (isHeadlessEnvironment() ? " (Headless)" : "") +
           (isCommandLine() ? " (Command line)" : "");
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
      long start = StartUpMeasurer.getCurrentTime() - duration;
      StartUpMeasurer.addCompletedActivity(start, handler.getClass(), ActivityCategory.PROJECT_OPEN_HANDLER, null, StartUpMeasurer.MEASURE_THRESHOLD);
    }
    else if (topic == VirtualFileManager.VFS_CHANGES) {
      if (TimeUnit.NANOSECONDS.toMillis(duration) > 50) {
        LOG.info(String.format("LONG VFS PROCESSING. Topic=%s, offender=%s, message=%s, time=%dms",
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
}
