// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.BundleBase;
import com.intellij.CommonBundle;
import com.intellij.concurrency.JobScheduler;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.StartUpMeasurer.Phases;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.*;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.IdeaApplication;
import com.intellij.idea.Main;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.impl.PlatformComponentManagerImpl;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.PotemkinProgress;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogEarthquakeShaker;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.AppIcon;
import com.intellij.ui.Splash;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.AppScheduledExecutorService;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.Stack;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.layout.PlatformDefaults;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.MutablePicoContainer;
import sun.awt.AWTAccessor;
import sun.awt.AWTAutoShutdown;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApplicationImpl extends PlatformComponentManagerImpl implements ApplicationEx {
  // do not use PluginManager.processException() because it can force app to exit, but we want just log error and continue
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.impl.ApplicationImpl");

  final ReadMostlyRWLock myLock;

  private final ModalityInvokator myInvokator = new ModalityInvokatorImpl();

  private final EventDispatcher<ApplicationListener> myDispatcher = EventDispatcher.create(ApplicationListener.class);

  private final boolean myTestModeFlag;
  private final boolean myHeadlessMode;
  private final boolean myCommandLineMode;

  private final boolean myIsInternal;
  private final String myName;

  private final Stack<Class> myWriteActionsStack = new Stack<>(); // contents modified in write action, read in read action
  private final TransactionGuardImpl myTransactionGuard = new TransactionGuardImpl();
  private int myWriteStackBase;

  private final long myStartTime;
  private boolean mySaveAllowed;
  private volatile boolean myExitInProgress;
  private volatile boolean myDisposeInProgress;

  private final Disposable myLastDisposable = Disposer.newDisposable(); // will be disposed last

  private static final int ourDumpThreadsOnLongWriteActionWaiting = Integer.getInteger("dump.threads.on.long.write.action.waiting", 0);

  private final ExecutorService ourThreadExecutorsService = AppExecutorUtil.getAppExecutorService();
  private boolean myLoaded;
  private static final String WAS_EVER_SHOWN = "was.ever.shown";

  public ApplicationImpl(boolean isInternal,
                         boolean isUnitTestMode,
                         boolean isHeadless,
                         boolean isCommandLine,
                         @NotNull String appName) {
    super(null);

    ApplicationManager.setApplication(this, myLastDisposable); // reset back to null only when all components already disposed

    getPicoContainer().registerComponentInstance(Application.class, this);
    getPicoContainer().registerComponentInstance(TransactionGuard.class.getName(), myTransactionGuard);

    boolean strictMode = isUnitTestMode || isInternal;
    BundleBase.assertOnMissedKeys(strictMode);
    IconLoader.setStrictGlobally(strictMode);

    AWTExceptionHandler.register(); // do not crash AWT on exceptions

    Disposer.setDebugMode(isInternal || isUnitTestMode || Disposer.isDebugDisposerOn());

    myStartTime = System.currentTimeMillis();
    myName = appName;

    myIsInternal = isInternal;
    myTestModeFlag = isUnitTestMode;
    myHeadlessMode = isHeadless;
    myCommandLineMode = isCommandLine;

    mySaveAllowed = !(isUnitTestMode || isHeadless);

    if (!isUnitTestMode && !isHeadless) {
      Disposer.register(this, Disposer.newDisposable(), "ui");

      StartupUtil.addExternalInstanceListener(args -> invokeLater(() -> {
        LOG.info("ApplicationImpl.externalInstanceListener invocation");
        String currentDirectory = args.isEmpty() ? null : args.get(0);
        List<String> realArgs = args.isEmpty() ? args : args.subList(1, args.size());
        Project project = CommandLineProcessor.processExternalCommandLine(realArgs, currentDirectory);
        JFrame frame = project == null
                       ? WindowManager.getInstance().findVisibleFrame() :
                       (JFrame)WindowManager.getInstance().getIdeFrame(project);
        if (frame != null) {
          if (frame instanceof IdeFrame) {
            AppIcon.getInstance().requestFocus((IdeFrame)frame);
          }
          else {
            frame.toFront();
            DialogEarthquakeShaker.shake(frame);
          }
        }
      }));

      //noinspection AssignmentToStaticFieldFromInstanceMethod
      WindowsCommandLineProcessor.LISTENER = (currentDirectory, args) -> {
        List<String> argsList = Arrays.asList(args);
        LOG.info("Received external Windows command line: current directory " + currentDirectory + ", command line " + argsList);
        if (argsList.isEmpty()) return;
        ModalityState state = getDefaultModalityState();
        for (ApplicationStarter starter : ApplicationStarter.EP_NAME.getExtensionList()) {
          if (starter.canProcessExternalCommandLine() &&
              argsList.get(0).equals(starter.getCommandName()) &&
              starter.allowAnyModalityState()) {
            state = getAnyModalityState();
          }
        }
        invokeLater(() -> CommandLineProcessor.processExternalCommandLine(argsList, currentDirectory), state);
      };
    }

    if (isUnitTestMode && IdeaApplication.getInstance() == null) {
      String[] args = {"inspect", "", "", ""};
      Main.setFlags(args); // set both isHeadless and isCommandLine to true
      System.setProperty(IdeaApplication.IDEA_IS_UNIT_TEST, Boolean.TRUE.toString());
      assert Main.isHeadless();
      assert Main.isCommandLine();
      //noinspection ResultOfObjectAllocationIgnored
      new IdeaApplication(args);
    }
    gatherStatistics = LOG.isDebugEnabled() || isUnitTestMode() || isInternal();

    Activity activity = StartUpMeasurer.start("instantiate AppDelayQueue");
    Thread edt = UIUtil.invokeAndWaitIfNeeded(() -> {
      // instantiate AppDelayQueue which starts "Periodic task thread" which we'll mark busy to prevent this EDT to die
      // that thread was chosen because we know for sure it's running
      AppScheduledExecutorService service = (AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService();
      Thread thread = service.getPeriodicTasksThread();
      AWTAutoShutdown.getInstance().notifyThreadBusy(thread); // needed for EDT not to exit suddenly
      Disposer.register(this, () -> {
        AWTAutoShutdown.getInstance().notifyThreadFree(thread); // allow for EDT to exit - needed for Upsource
      });
      return Thread.currentThread();
    });
    activity.end();
    myLock = new ReadMostlyRWLock(edt);

    NoSwingUnderWriteAction.watchForEvents(this);
  }

  /**
   * Executes a {@code runnable} in an "impatient" mode.
   * In this mode any attempt to call {@link #runReadAction(Runnable)}
   * would fail (i.e. throw {@link ApplicationUtil.CannotRunReadActionException})
   * if there is a pending write action.
   */
  @Override
  @Deprecated
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

  private boolean disposeSelf(final boolean checkCanCloseProject) {
    final ProjectManagerEx manager = ProjectManagerEx.getInstanceEx();
    SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(this, /* isSaveAppAlso = */ false);
    if (manager != null) {
      final boolean[] canClose = {true};
      try {
        CommandProcessor.getInstance().executeCommand(null, () -> {
          if (!manager.closeAndDisposeAllProjects(checkCanCloseProject)) {
            canClose[0] = false;
          }
        }, ApplicationBundle.message("command.exit"), null);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      if (!canClose[0]) {
        return false;
      }
    }
    runWriteAction(() -> Disposer.dispose(this));

    Disposer.assertIsEmpty();
    return true;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public boolean holdsReadLock() {
    return myLock.isReadLockedByThisThread();
  }

  @NotNull
  @Override
  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getRootArea().getPicoContainer();
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

  @NotNull
  @Override
  public Future<?> executeOnPooledThread(@NotNull final Runnable action) {
    ReadMostlyRWLock.SuspensionId suspensionId = myLock.currentReadPrivilege();
    return ourThreadExecutorsService.submit(new Runnable() {
      @Override
      public String toString() {
        return action.toString();
      }

      @Override
      public void run() {
        // see the comment in "executeOnPooledThread(Callable)"
        try (AccessToken ignored = myLock.applyReadPrivilege(suspensionId)) {
          action.run();
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
    });
  }

  @NotNull
  @Override
  public <T> Future<T> executeOnPooledThread(@NotNull final Callable<T> action) {
    ReadMostlyRWLock.SuspensionId suspensionId = myLock.currentReadPrivilege();
    return ourThreadExecutorsService.submit(new Callable<T>() {
      @Override
      public T call() {
        // This is very special magic only needed by threads that need read actions and can be executed
        // during "executeSuspendingWriteAction" (e.g. dumb mode, indexing). Threads created via "executeOnPooledThread"
        // in these circumstances may run read actions immediately, instead of waiting until the write action is resumed and finished.

        // For everyone else, "executeOnPooledThread" should be equivalent to "AppExecutorUtil" AKA "PooledThreadExecutor" pool
        try (AccessToken ignored = myLock.applyReadPrivilege(suspensionId)) {
          return action.call();
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
    return myLock.isWriteThread();
  }

  @Override
  @NotNull
  public ModalityInvokator getInvokator() {
    return myInvokator;
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable) {
    invokeLater(runnable, getDisposed());
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull Condition expired) {
    invokeLater(runnable, ModalityState.defaultModalityState(), expired);
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state) {
    invokeLater(runnable, state, getDisposed());
  }

  @Override
  public void invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition expired) {
    LaterInvocator.invokeLaterWithCallback(myTransactionGuard.wrapLaterInvocation(runnable, state), state, expired, null);
  }

  @Override
  public void load(@Nullable final String configPath) {
    load(configPath, null, null);
  }

  public void load(@Nullable final String configPath, @Nullable Splash splash, @Nullable List<? extends IdeaPluginDescriptor> plugins) {
    AccessToken token = HeavyProcessLatch.INSTANCE.processStarted("Loading application components");
    try {
      StartupProgress startupProgress = splash == null ? null : (message, progress) -> splash.showProgress("", progress);

      // before totalMeasureToken to ensure that plugin loading is not part of this
      if (plugins == null) {
        plugins = PluginManagerCore.getLoadedPlugins(startupProgress);
      }

      if (!isHeadlessEnvironment()) {
        // wanted for UI, but should not affect start-up time,
        // since MigLayout is not important for start-up UI, it is ok execute it in a pooled thread
        // (call itself is cheap but leads to loading classes)
        AppExecutorUtil.getAppExecutorService().submit(() -> {
          //IDEA-170295
          PlatformDefaults.setLogicalPixelBase(PlatformDefaults.BASE_FONT_SIZE);
        });
      }

      ProgressIndicator indicator = splash == null ? null : new EmptyProgressIndicator() {
        @Override
        public void setFraction(double fraction) {
          splash.showProgress("", (float)fraction);
        }
      };
      init(plugins, indicator, () -> {
        String effectiveConfigPath = FileUtilRt.toSystemIndependentName(configPath == null ? PathManager.getConfigPath() : configPath);
        List<ApplicationLoadListener> applicationLoadListeners = ApplicationLoadListener.EP_NAME.getExtensionList();
        for (ApplicationLoadListener listener : applicationLoadListeners) {
          try {
            listener.beforeApplicationLoaded(this, effectiveConfigPath);
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }

        // we set it after beforeApplicationLoaded call, because app store can depends on stream provider state
        ServiceKt.getStateStore(this).setPath(effectiveConfigPath);

        for (ApplicationLoadListener listener : applicationLoadListeners) {
          try {
            listener.beforeComponentsCreated();
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
      });

      ourThreadExecutorsService.submit(() -> createLocatorFile());

      Activity activity = StartUpMeasurer.start(Phases.APP_INITIALIZED_CALLBACK);
      for (ApplicationInitializedListener listener : ((ExtensionsAreaImpl)Extensions.getArea(null)).<ApplicationInitializedListener>getExtensionPoint("com.intellij.applicationInitializedListener")) {
        if (listener == null) {
          break;
        }

        try {
          listener.componentsInitialized();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
      activity.end();
    }
    finally {
      token.finish();
    }
    myLoaded = true;
  }

  @Override
  protected void createComponents(@Nullable ProgressIndicator indicator) {
    // we cannot wrap "init()" call because ProgressManager instance could be created only after component registration (our "componentsRegistered" callback)
    if (indicator == null) {
      // no splash, no need to to use progress manager
      super.createComponents(null);
    }
    else {
      ProgressManager.getInstance().runProcess(() -> super.createComponents(indicator), indicator);
    }
  }

  @Override
  @Nullable
  protected ProgressIndicator getProgressIndicator() {
    // could be called before full initialization
    ProgressManager progressManager = (ProgressManager)getPicoContainer().getComponentInstance(ProgressManager.class.getName());
    return progressManager == null ? null : progressManager.getProgressIndicator();
  }

  @Override
  protected void setProgressDuringInit(@NotNull ProgressIndicator indicator) {
    float start = PluginManagerCore.PROGRESS_PART;
    indicator.setFraction(start + getPercentageOfComponentsLoaded() * (1 - start));
  }

  private static void createLocatorFile() {
    Path locatorFile = Paths.get(PathManager.getSystemPath(), ApplicationEx.LOCATOR_FILE_NAME);
    try {
      Files.write(locatorFile, PathManager.getHomePath().getBytes(StandardCharsets.UTF_8));
    }
    catch (IOException e) {
      LOG.warn("can't store a location in '" + locatorFile + "'", e);
    }
  }

  @Override
  public boolean isLoaded() {
    return myLoaded;
  }

  @Override
  public void dispose() {
    HeavyProcessLatch.INSTANCE.stopThreadPrioritizing();
    fireApplicationExiting();

    ShutDownTracker.getInstance().ensureStopperThreadsFinished();

    disposeComponents();

    AppScheduledExecutorService service = (AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService();
    service.shutdownAppScheduledExecutorService();

    super.dispose();
    Disposer.dispose(myLastDisposable); // dispose it last

    if (gatherStatistics) {
      //noinspection TestOnlyProblems
      LOG.info(writeActionStatistics());
      LOG.info(ActionUtil.ActionPauses.STAT.statistics());
      //noinspection TestOnlyProblems
      LOG.info(((AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService()).statistics()
               + "; ProcessIOExecutorService threads: "+((ProcessIOExecutorService)ProcessIOExecutorService.INSTANCE).getThreadCounter()
      );
    }
  }

  @TestOnly
  @NotNull
  public String writeActionStatistics() {
    return ActionPauses.WRITE.statistics();
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process,
                                                     @NotNull String progressTitle,
                                                     boolean canBeCanceled,
                                                     Project project) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project, null);
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process,
                                                     @NotNull final String progressTitle,
                                                     final boolean canBeCanceled,
                                                     @Nullable final Project project,
                                                     final JComponent parentComponent) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project, parentComponent, null);
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull final Runnable process,
                                                     @NotNull final String progressTitle,
                                                     final boolean canBeCanceled,
                                                     @Nullable final Project project,
                                                     final JComponent parentComponent,
                                                     final String cancelText) {
    assertIsDispatchThread();
    boolean writeAccessAllowed = isWriteAccessAllowed();
    if (writeAccessAllowed // Disallow running process in separate thread from under write action.
                           // The thread will deadlock trying to get read action otherwise.
        || isHeadlessEnvironment() && !isUnitTestMode()
      ) {
      if (writeAccessAllowed) {
        LOG.debug("Starting process with progress from within write action makes no sense");
      }
      try {
        ProgressManager.getInstance().runProcess(process, new EmptyProgressIndicator());
      }
      catch (ProcessCanceledException e) {
        // ok to ignore.
        return false;
      }
      return true;
    }

    final ProgressWindow progress = new ProgressWindow(canBeCanceled, false, project, parentComponent, cancelText);
    // in case of abrupt application exit when 'ProgressManager.getInstance().runProcess(process, progress)' below
    // does not have a chance to run, and as a result the progress won't be disposed
    Disposer.register(this, progress);

    progress.setTitle(progressTitle);

    final AtomicBoolean threadStarted = new AtomicBoolean();
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      executeOnPooledThread(() -> {
        try {
          ProgressManager.getInstance().runProcess(process, progress);
        }
        catch (ProcessCanceledException e) {
          progress.cancel();
          // ok to ignore.
        }
        catch (RuntimeException e) {
          progress.cancel();
          throw e;
        }
      });
      threadStarted.set(true);
    });

    progress.startBlocking();

    LOG.assertTrue(threadStarted.get());
    LOG.assertTrue(!progress.isRunning());

    return !progress.isCanceled();
  }


  @Override
  public boolean runProcessWithProgressSynchronouslyInReadAction(@Nullable final Project project,
                                                                 @NotNull final String progressTitle,
                                                                 final boolean canBeCanceled,
                                                                 final String cancelText,
                                                                 final JComponent parentComponent,
                                                                 @NotNull final Runnable process) {
    assertIsDispatchThread();
    boolean writeAccessAllowed = isWriteAccessAllowed();
    if (writeAccessAllowed // Disallow running process in separate thread from under write action.
                           // The thread will deadlock trying to get read action otherwise.
      ) {
      throw new IncorrectOperationException("Starting process with progress from within write action makes no sense");
    }

    final ProgressWindow progress = new ProgressWindow(canBeCanceled, false, project, parentComponent, cancelText);
    // in case of abrupt application exit when 'ProgressManager.getInstance().runProcess(process, progress)' below
    // does not have a chance to run, and as a result the progress won't be disposed
    Disposer.register(this, progress);

    progress.setTitle(progressTitle);

    final Semaphore readActionAcquired = new Semaphore();
    readActionAcquired.down();
    final Semaphore modalityEntered = new Semaphore();
    modalityEntered.down();
    executeOnPooledThread(() -> {
      try {
        ApplicationManager.getApplication().runReadAction(() -> {
          readActionAcquired.up();
          modalityEntered.waitFor();
          ProgressManager.getInstance().runProcess(process, progress);
        });
      }
      catch (ProcessCanceledException e) {
        progress.cancel();
        // ok to ignore.
      }
      catch (RuntimeException e) {
        progress.cancel();
        throw e;
      }
    });

    readActionAcquired.waitFor();
    progress.startBlocking(modalityEntered::up);

    LOG.assertTrue(!progress.isRunning());

    return !progress.isCanceled();
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    if (isDispatchThread()) {
      runnable.run();
      return;
    }

    if (holdsReadLock()) {
      throw new IllegalStateException("Calling invokeAndWait from read-action leads to possible deadlock.");
    }

    LaterInvocator.invokeAndWait(myTransactionGuard.wrapLaterInvocation(runnable, modalityState), modalityState);
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable) throws ProcessCanceledException {
    invokeAndWait(runnable, ModalityState.defaultModalityState());
  }

  @Override
  @NotNull
  public ModalityState getCurrentModalityState() {
    return LaterInvocator.getCurrentModalityState();
  }

  @Override
  @NotNull
  public ModalityState getModalityStateForComponent(@NotNull Component c) {
    if (!isDispatchThread()) LOG.debug("please, use application dispatch thread to get a modality state");
    Window window = UIUtil.getWindow(c);
    if (window == null) return getNoneModalityState(); //?
    return LaterInvocator.modalityStateForWindow(window);
  }

  @Override
  @NotNull
  public ModalityState getAnyModalityState() {
    return AnyModalityState.ANY;
  }

  @Override
  @NotNull
  public ModalityState getDefaultModalityState() {
    return isDispatchThread() ? getCurrentModalityState() : CoreProgressManager.getCurrentThreadProgressModality();
  }

  @Override
  @NotNull
  public ModalityState getNoneModalityState() {
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
  public void exit() {
    exit(false, false);
  }

  @Override
  public void exit(boolean force, final boolean exitConfirmed) {
    exit(false, exitConfirmed, false);
  }

  @Override
  public void restart() {
    restart(false);
  }

  @Override
  public void restart(boolean exitConfirmed) {
    exit(false, exitConfirmed, true);
  }

  /**
   * Restarts the IDE with optional process elevation (on Windows).
   *
   * @param exitConfirmed if true, the IDE does not ask for exit confirmation.
   * @param elevate if true and the IDE is running on Windows, the IDE is restarted in elevated mode (with admin privileges)
   */
  public void restart(boolean exitConfirmed, boolean elevate) {
    exit(false, exitConfirmed, true, elevate, ArrayUtil.EMPTY_STRING_ARRAY);
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
  public void exit(boolean force, boolean exitConfirmed, boolean restart) {
    exit(force, exitConfirmed, restart, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void exit(boolean force, boolean exitConfirmed, boolean restart, @NotNull String[] beforeRestart) {
    exit(force, exitConfirmed, restart, false, beforeRestart);
  }

  private void exit(boolean force, boolean exitConfirmed, boolean restart, boolean elevate, @NotNull String[] beforeRestart) {
    if (!force) {
      if (myExitInProgress) return;
      if (!exitConfirmed && getDefaultModalityState() != ModalityState.NON_MODAL) return;
    }

    myExitInProgress = true;
    if (isDispatchThread()) {
      doExit(force, exitConfirmed, restart, elevate, beforeRestart);
    }
    else {
      invokeLater(() -> doExit(force, exitConfirmed, restart, elevate, beforeRestart), ModalityState.NON_MODAL);
    }
  }

  private void doExit(boolean force, boolean exitConfirmed, boolean restart, boolean elevate, String[] beforeRestart) {
    try {
      if (!force && !confirmExitIfNeeded(exitConfirmed)) {
        return;
      }

      AppLifecycleListener lifecycleListener = getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
      lifecycleListener.appClosing();

      myDisposeInProgress = true;

      if (!force && !canExit()) {
        return;
      }

      lifecycleListener.appWillBeClosed(restart);
      LifecycleUsageTriggerCollector.onIdeClose(restart);

      boolean success = disposeSelf(!force);
      if (!success || isUnitTestMode() || Boolean.getBoolean("idea.test.guimode")) {
        if (Boolean.getBoolean("idea.test.guimode")) {
          IdeaApplication.getInstance().shutdown();
        }
        return;
      }

      int exitCode = 0;
      if (restart && Restarter.isSupported()) {
        try {
          Restarter.scheduleRestart(elevate, beforeRestart);
        }
        catch (Throwable t) {
          LOG.error("Restart failed", t);
          Main.showMessage("Restart failed", t);
          exitCode = Main.RESTART_FAILED;
        }
      }
      System.exit(exitCode);
    }
    finally {
      myDisposeInProgress = false;
      myExitInProgress = false;
    }
  }

  private static boolean confirmExitIfNeeded(boolean exitConfirmed) {
    boolean hasUnsafeBgTasks = ProgressManager.getInstance().hasUnsafeProgressIndicator();
    if (exitConfirmed && !hasUnsafeBgTasks) {
      return true;
    }

    DialogWrapper.DoNotAskOption option = new DialogWrapper.DoNotAskOption() {
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

      @NotNull
      @Override
      public String getDoNotShowMessage() {
        return "Do not ask me again";
      }
    };

    if (hasUnsafeBgTasks || option.isToBeShown()) {
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
              JobScheduler.getScheduler().schedule(this, 1, TimeUnit.SECONDS);
            }
          }
        };
        JobScheduler.getScheduler().schedule(task, 1, TimeUnit.SECONDS);
      }
      String name = ApplicationNamesInfo.getInstance().getFullProductName();
      String message = ApplicationBundle.message(hasUnsafeBgTasks ? "exit.confirm.prompt.tasks" : "exit.confirm.prompt", name);
      int result = MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"), message)
        .yesText(ApplicationBundle.message("command.exit"))
        .noText(CommonBundle.message("button.cancel"))
        .doNotAsk(option).show();
      if (alreadyGone.getAndSet(true)) {
        if (!option.isToBeShown()) {
          return true;
        }
        result = MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"),
                                            ApplicationBundle.message("exit.confirm.prompt", name))
          .yesText(ApplicationBundle.message("command.exit"))
          .noText(CommonBundle.message("button.cancel"))
          .doNotAsk(option).show();
      }
      if (result != Messages.YES) {
        return false;
      }
    }

    return true;
  }

  private boolean canExit() {
    for (ApplicationListener applicationListener : myDispatcher.getListeners()) {
      if (!applicationListener.canExitApplication()) {
        return false;
      }
    }

    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project[] projects = projectManager.getOpenProjects();
    for (Project project : projects) {
      if (!projectManager.canClose(project)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public void runReadAction(@NotNull final Runnable action) {
    if (checkReadAccessAllowedAndNoPendingWrites()) {
      action.run();
    }
    else {
      startRead();
      try {
        action.run();
      }
      finally {
        endRead();
      }
    }
  }

  @Override
  public <T> T runReadAction(@NotNull final Computable<T> computation) {
    if (checkReadAccessAllowedAndNoPendingWrites()) {
      return computation.compute();
    }
    startRead();
    try {
      return computation.compute();
    }
    finally {
      endRead();
    }
  }

  @Override
  public <T, E extends Throwable> T runReadAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    if (checkReadAccessAllowedAndNoPendingWrites()) {
      return computation.compute();
    }
    startRead();
    try {
      return computation.compute();
    }
    finally {
      endRead();
    }
  }

  private void startRead() {
    myLock.readLock();
  }

  private void endRead() {
    myLock.readUnlock();
  }

  @ApiStatus.Experimental
  public boolean runWriteActionWithNonCancellableProgressInDispatchThread(@NotNull String title,
                                                                          @Nullable Project project,
                                                                          @Nullable JComponent parentComponent,
                                                                          @NotNull Consumer<? super ProgressIndicator> action) {
    return runEdtProgressWriteAction(title, project, parentComponent, null, action);
  }

  @ApiStatus.Experimental
  public boolean runWriteActionWithCancellableProgressInDispatchThread(@NotNull String title,
                                                                       @Nullable Project project,
                                                                       @Nullable JComponent parentComponent,
                                                                       @NotNull Consumer<? super ProgressIndicator> action) {
    return runEdtProgressWriteAction(title, project, parentComponent, IdeBundle.message("action.stop"), action);
  }

  private boolean runEdtProgressWriteAction(@NotNull String title,
                                            @Nullable Project project,
                                            @Nullable JComponent parentComponent,
                                            @Nullable String cancelText,
                                            @NotNull Consumer<? super ProgressIndicator> action) {
    return runWriteActionWithClass(action.getClass(), ()->{
      PotemkinProgress indicator = new PotemkinProgress(title, project, parentComponent, cancelText);
      indicator.runInSwingThread(() -> action.consume(indicator));
      return !indicator.isCanceled();
    });
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
  public void runWriteAction(@NotNull final Runnable action) {
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
  public <T> T runWriteAction(@NotNull final Computable<T> computation) {
    Class<? extends Computable> clazz = computation.getClass();
    return runWriteActionWithClass(clazz, () -> computation.compute());
  }

  @Override
  public <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    Class<? extends ThrowableComputable> clazz = computation.getClass();
    return runWriteActionWithClass(clazz, computation);
  }

  @Override
  public boolean hasWriteAction(@NotNull Class<?> actionClass) {
    assertReadAccessAllowed();

    for (int i = myWriteActionsStack.size() - 1; i >= 0; i--) {
      Class action = myWriteActionsStack.get(i);
      if (actionClass == action || ReflectionUtil.isAssignable(actionClass, action)) return true;
    }
    return false;
  }

  @Override
  public void assertReadAccessAllowed() {
    if (!isReadAccessAllowed()) {
      LOG.error(
        "Read access is allowed from event dispatch thread or inside read-action only" +
        " (see com.intellij.openapi.application.Application.runReadAction())",
        "Current thread: " + describe(Thread.currentThread()), "; dispatch thread: " + EventQueue.isDispatchThread() +"; isDispatchThread(): "+isDispatchThread(),
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
    return isDispatchThread() || myLock.isReadLockedByThisThread();
  }

  private boolean checkReadAccessAllowedAndNoPendingWrites() throws ApplicationUtil.CannotRunReadActionException {
    return isDispatchThread() || myLock.checkReadLockedByThisThreadAndNoPendingWrites();
  }

  @Override
  public void assertIsDispatchThread() {
    if (isDispatchThread()) return;
    if (ShutDownTracker.isShutdownHookRunning()) return;
    assertIsDispatchThread("Access is allowed from event dispatch thread only.");
  }

  private void assertIsDispatchThread(String message) {
    if (isDispatchThread()) return;
    throw new RuntimeExceptionWithAttachments(
      message,
      "EventQueue.isDispatchThread()=" + EventQueue.isDispatchThread() +
      " Toolkit.getEventQueue()=" + Toolkit.getDefaultToolkit().getSystemEventQueue() +
      "\nCurrent thread: " + describe(Thread.currentThread()) +
      "\nSystemEventQueueThread: " + describe(getEventQueueThread()),
      new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString()));
  }

  @Override
  public void assertIsDispatchThread(@Nullable final JComponent component) {
    if (component == null) return;

    if (isDispatchThread()) {
      return;
    }

    if (Boolean.TRUE.equals(component.getClientProperty(WAS_EVER_SHOWN))) {
      assertIsDispatchThread();
    }
    else {
      final JRootPane root = component.getRootPane();
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
    if (checkReadAccessAllowedAndNoPendingWrites()) {
      action.run();
    }
    else {
      if (!myLock.tryReadLock()) return false;
      try {
        action.run();
      }
      finally {
        endRead();
      }
    }
    return true;
  }

  @Override
  public boolean isActive() {
    if (isHeadlessEnvironment()) return true;

    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();

    if (ApplicationActivationStateManager.getState().isInactive()
      && activeWindow != null) {
      ApplicationActivationStateManager.updateState(activeWindow);
    }

    return ApplicationActivationStateManager.getState().isActive();
  }

  @NotNull
  @Override
  public AccessToken acquireReadActionLock() {
    // if we are inside read action, do not try to acquire read lock again since it will deadlock if there is a pending writeAction
    return checkReadAccessAllowedAndNoPendingWrites() ? AccessToken.EMPTY_ACCESS_TOKEN : new ReadAccessToken();
  }

  private volatile boolean myWriteActionPending;

  @Override
  public boolean isWriteActionPending() {
    return myWriteActionPending;
  }

  private final boolean gatherStatistics;
  private static class ActionPauses {
    private static final PausesStat WRITE = new PausesStat("Write action");
  }

  private void startWrite(@NotNull Class clazz) {
    if (!isWriteAccessAllowed()) {
      assertIsDispatchThread("Write access is allowed from event dispatch thread only");
    }
    HeavyProcessLatch.INSTANCE.stopThreadPrioritizing(); // let non-cancellable read actions complete faster, if present
    boolean writeActionPending = myWriteActionPending;
    if (gatherStatistics && myWriteActionsStack.isEmpty() && !writeActionPending) {
      ActionPauses.WRITE.started();
    }
    myWriteActionPending = true;
    try {
      ActivityTracker.getInstance().inc();
      fireBeforeWriteActionStart(clazz);

      if (!myLock.isWriteLocked()) {
        Future<?> reportSlowWrite = ourDumpThreadsOnLongWriteActionWaiting <= 0 ? null :
                                    JobScheduler.getScheduler()
                                      .scheduleWithFixedDelay(() -> PerformanceWatcher.getInstance().dumpThreads("waiting", true),
                                                              ourDumpThreadsOnLongWriteActionWaiting,
                                                              ourDumpThreadsOnLongWriteActionWaiting, TimeUnit.MILLISECONDS);
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

  private void endWrite(@NotNull Class clazz) {
    try {
      fireWriteActionFinished(clazz);
      // fire listeners before popping stack because if somebody starts write action in a listener,
      // there is a danger of unlocking the write lock before other listeners have been run (since write lock became non-reentrant).
    }
    finally {
      myWriteActionsStack.pop();
      if (gatherStatistics && myWriteActionsStack.isEmpty() && !myWriteActionPending) {
        ActionPauses.WRITE.finished("write action ("+clazz+")");
      }
      if (myWriteActionsStack.size() == myWriteStackBase) {
        myLock.writeUnlock();
      }
      if (myWriteActionsStack.isEmpty()) {
        fireAfterWriteActionFinished(clazz);
      }
    }
  }

  @NotNull
  @Override
  public AccessToken acquireWriteActionLock(@NotNull Class clazz) {
    return new WriteAccessToken(clazz);
  }

  private class WriteAccessToken extends AccessToken {
    @NotNull private final Class clazz;

    WriteAccessToken(@NotNull Class clazz) {
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
        final Thread thread = Thread.currentThread();
        thread.setName(thread.getName() + id);
      }
    }

    private void unmarkThreadNameInStackTrace() {
      String id = id();

      if (id != null) {
        final Thread thread = Thread.currentThread();
        String name = thread.getName();
        name = StringUtil.replace(name, id, "");
        thread.setName(name);
      }
    }

    private String id() {
      Class aClass = getClass();
      String name = aClass.getName();
      while (name == null) {
        aClass = aClass.getSuperclass();
        name = aClass.getName();
      }

      name = name.substring(name.lastIndexOf('.') + 1);
      name = name.substring(name.lastIndexOf('$') + 1);
      if (!name.equals("AccessToken")) {
        return " [" + name+"]";
      }
      return null;
    }
  }

  private class ReadAccessToken extends AccessToken {
    private ReadAccessToken() {
      startRead();
    }

    @Override
    public void finish() {
      endRead();
    }
  }

  @Override
  public void assertWriteAccessAllowed() {
    LOG.assertTrue(isWriteAccessAllowed(),
                   "Write access is allowed inside write-action only (see com.intellij.openapi.application.Application.runWriteAction())");
  }

  @Override
  public boolean isWriteAccessAllowed() {
    return isDispatchThread() && myLock.isWriteLocked();
  }

  @Override
  public boolean isWriteActionInProgress() {
    return myLock.isWriteLocked();
  }

  public void executeSuspendingWriteAction(@Nullable Project project, @NotNull String title, @NotNull Runnable runnable) {
    assertIsDispatchThread();
    if (!myLock.isWriteLocked()) {
      runModalProgress(project, title, runnable);
      return;
    }

    myTransactionGuard.submitTransactionAndWait(() -> {
      int prevBase = myWriteStackBase;
      myWriteStackBase = myWriteActionsStack.size();
      try (AccessToken ignored = myLock.writeSuspend()) {
        runModalProgress(project, title, () -> {
          try (AccessToken ignored1 = myLock.grantReadPrivilege()) {
            runnable.run();
          }
        });
      } finally {
        myWriteStackBase = prevBase;
      }
    });
  }

  private static void runModalProgress(@Nullable Project project, @NotNull String title, @NotNull Runnable runnable) {
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

  private void fireApplicationExiting() {
    myDispatcher.getMulticaster().applicationExiting();
  }
  private void fireBeforeWriteActionStart(@NotNull Class action) {
    myDispatcher.getMulticaster().beforeWriteActionStart(action);
  }

  private void fireWriteActionStarted(@NotNull Class action) {
    myDispatcher.getMulticaster().writeActionStarted(action);
  }

  private void fireWriteActionFinished(@NotNull Class action) {
    myDispatcher.getMulticaster().writeActionFinished(action);
  }

  private void fireAfterWriteActionFinished(@NotNull Class action) {
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

  @NotNull
  @Override
  public <T> T[] getExtensions(@NotNull final ExtensionPointName<T> extensionPointName) {
    return Extensions.getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  @Override
  public boolean isDisposeInProgress() {
    return myDisposeInProgress || ShutDownTracker.isShutdownHookRunning();
  }

  @Override
  public boolean isRestartCapable() {
    return Restarter.isSupported();
  }

  @TestOnly
  public void setDisposeInProgress(boolean disposeInProgress) {
    myDisposeInProgress = disposeInProgress;
  }

  @Override
  public String toString() {
    return "Application" +
           (isDisposed() ? " (Disposed)" : "") +
           (isUnitTestMode() ? " (Unit test)" : "") +
           (isInternal() ? " (Internal)" : "") +
           (isHeadlessEnvironment() ? " (Headless)" : "") +
           (isCommandLine() ? " (Command line)" : "");
  }

  @Nullable
  @Override
  protected String activityNamePrefix() {
    return "app ";
  }

  @NotNull
  @Override
  protected List<ServiceDescriptor> getServices(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    return ((IdeaPluginDescriptorImpl)pluginDescriptor).getAppServices();
  }

  @TestOnly
  void disableEventsUntil(@NotNull Disposable disposable) {
    myDispatcher.neuterMultiCasterWhilePerformanceTestIsRunningUntil(disposable);
  }
}
