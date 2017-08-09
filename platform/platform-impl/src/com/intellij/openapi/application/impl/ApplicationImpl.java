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
package com.intellij.openapi.application.impl;

import com.intellij.BundleBase;
import com.intellij.CommonBundle;
import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory;
import com.intellij.concurrency.JobScheduler;
import com.intellij.diagnostic.LogEventException;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.*;
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
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.impl.PlatformComponentManagerImpl;
import com.intellij.openapi.components.impl.ServiceManagerImpl;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.PotemkinProgress;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.ui.DialogEarthquakeShaker;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;
import org.picocontainer.MutablePicoContainer;
import sun.awt.AWTAccessor;
import sun.awt.AWTAutoShutdown;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApplicationImpl extends PlatformComponentManagerImpl implements ApplicationEx {
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
  private volatile Thread myWriteActionThread;

  private int myInEditorPaintCounter; // EDT only
  private final long myStartTime;
  @Nullable
  private Splash mySplash;
  private boolean myDoNotSave;
  private volatile boolean myExitInProgress;
  private volatile boolean myDisposeInProgress;

  private final Disposable myLastDisposable = Disposer.newDisposable(); // will be disposed last

  private final AtomicBoolean mySaveSettingsIsInProgress = new AtomicBoolean(false);
  @SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
  private static final int ourDumpThreadsOnLongWriteActionWaiting = Integer.getInteger("dump.threads.on.long.write.action.waiting", 0);

  private final ExecutorService ourThreadExecutorsService = PooledThreadExecutor.INSTANCE;
  private boolean myLoaded;
  private static final String WAS_EVER_SHOWN = "was.ever.shown";

  private static final ModalityState ANY = new ModalityState() {
    @Override
    public boolean dominates(@NotNull ModalityState anotherState) {
      return false;
    }

    @Override
    public String toString() {
      return "ANY";
    }
  };

  static {
    IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool();
  }

  public ApplicationImpl(boolean isInternal,
                         boolean isUnitTestMode,
                         boolean isHeadless,
                         boolean isCommandLine,
                         @NotNull String appName,
                         @Nullable Splash splash) {
    super(null);

    ApplicationManager.setApplication(this, myLastDisposable); // reset back to null only when all components already disposed

    getPicoContainer().registerComponentInstance(Application.class, this);
    getPicoContainer().registerComponentInstance(TransactionGuard.class.getName(), myTransactionGuard);

    BundleBase.assertKeyIsFound = IconLoader.STRICT = isUnitTestMode || isInternal;

    AWTExceptionHandler.register(); // do not crash AWT on exceptions

    Disposer.setDebugMode(isInternal || isUnitTestMode || Disposer.isDebugDisposerOn());

    myStartTime = System.currentTimeMillis();
    mySplash = splash;
    myName = appName;

    myIsInternal = isInternal;
    myTestModeFlag = isUnitTestMode;
    myHeadlessMode = isHeadless;
    myCommandLineMode = isCommandLine;

    myDoNotSave = isUnitTestMode || isHeadless;

    if (!isUnitTestMode && !isHeadless) {
      Disposer.register(this, Disposer.newDisposable(), "ui");

      StartupUtil.addExternalInstanceListener(args -> invokeLater(() -> {
        LOG.info("ApplicationImpl.externalInstanceListener invocation");
        String currentDirectory = args.isEmpty() ? null : args.get(0);
        List<String> realArgs = args.isEmpty() ? args : args.subList(1, args.size());
        final Project project = CommandLineProcessor.processExternalCommandLine(realArgs, currentDirectory);
        JFrame frame = project == null ? WindowManager.getInstance().findVisibleFrame() :
                       (JFrame)WindowManager.getInstance().getIdeFrame(project);
        if (frame != null) {
          if (frame instanceof IdeFrame) {
            AppIcon.getInstance().requestFocus((IdeFrame)frame);
          } else {
            frame.toFront();
            DialogEarthquakeShaker.shake(frame);
          }
        }
      }));

      WindowsCommandLineProcessor.LISTENER = (currentDirectory, commandLine) -> {
        LOG.info("Received external Windows command line: current directory " + currentDirectory + ", command line " + commandLine);
        invokeLater(() -> {
          final List<String> args = StringUtil.splitHonorQuotes(commandLine, ' ');
          args.remove(0);   // process name
          CommandLineProcessor.processExternalCommandLine(args, currentDirectory);
        });
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
    myLock = new ReadMostlyRWLock(edt);

    NoSwingUnderWriteAction.watchForEvents(this);
  }

  /**
   * Executes a {@code runnable} in an "impatient" mode.
   * In this mode any attempt to call {@link #runReadAction(Runnable)}
   * would fail (i.e. throw {@link ApplicationUtil.CannotRunReadActionException})
   * if there is a pending write action.
   */
  public void executeByImpatientReader(@NotNull Runnable runnable) throws ApplicationUtil.CannotRunReadActionException {
    if (isDispatchThread()) {
      runnable.run();
    }
    else {
      myLock.executeByImpatientReader(runnable);
    }
  }

  private boolean disposeSelf(final boolean checkCanCloseProject) {
    final ProjectManagerImpl manager = (ProjectManagerImpl)ProjectManagerEx.getInstanceEx();
    if (manager == null) {
      saveSettings();
    }
    else {
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

  private static class Holder {
    private static final boolean ourIsRunningFromSources = new File(PathManager.getHomePath(), ".idea").isDirectory();
  }
  public static boolean isRunningFromSources() {
    return Holder.ourIsRunningFromSources;
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
        try (AccessToken ignored = myLock.applyReadPrivilege(suspensionId)) {
          action.run();
        }
        catch (ProcessCanceledException e) {
          // ignore
        }
        catch (Throwable t) {
          LOG.error(t);
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
        try (AccessToken ignored = myLock.applyReadPrivilege(suspensionId)) {
          return action.call();
        }
        catch (ProcessCanceledException e) {
          // ignore
        }
        catch (Throwable t) {
          LOG.error(t);
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
    myInvokator.invokeLater(myTransactionGuard.wrapLaterInvocation(runnable, state), state, expired);
  }

  @Override
  public void load() {
    load(null);
  }

  @Override
  public void load(@Nullable final String configPath) {
    AccessToken token = HeavyProcessLatch.INSTANCE.processStarted("Loading application components");
    try {
      long start = System.currentTimeMillis();
      ProgressIndicator indicator = mySplash == null ? null : new EmptyProgressIndicator() {
        @Override
        public void setFraction(double fraction) {
          mySplash.showProgress("", (float)fraction);
        }
      };
      init(indicator, () -> {
        // create ServiceManagerImpl at first to force extension classes registration
        getPicoContainer().getComponentInstance(ServiceManagerImpl.class);

        String effectiveConfigPath = FileUtilRt.toSystemIndependentName(configPath == null ? PathManager.getConfigPath() : configPath);
        ApplicationLoadListener[] applicationLoadListeners = ApplicationLoadListener.EP_NAME.getExtensions();
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
      LOG.info(getComponentConfigCount() + " application components initialized in " + (System.currentTimeMillis() - start) + "ms");
    }
    finally {
      token.finish();
    }
    myLoaded = true;
    mySplash = null;

    createLocatorFile();
  }

  @Override
  protected void createComponents(@Nullable ProgressIndicator indicator) {
    // we cannot wrap "init()" call because ProgressManager instance could be created only after component registration (our "componentsRegistered" callback)
    Runnable task = () -> super.createComponents(indicator);

    if (indicator == null) {
      // no splash, no need to to use progress manager
      task.run();
    }
    else {
      ProgressManager.getInstance().runProcess(task, indicator);
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
    float start = PluginManagerCore.PLUGINS_PROGRESS_PART + PluginManagerCore.LOADERS_PROGRESS_PART;
    indicator.setFraction(start + getPercentageOfComponentsLoaded() * (1 - start));
  }

  private static void createLocatorFile() {
    File locatorFile = new File(PathManager.getSystemPath() + "/" + ApplicationEx.LOCATOR_FILE_NAME);
    try {
      byte[] data = PathManager.getHomePath().getBytes(CharsetToolkit.UTF8_CHARSET);
      FileUtil.writeToFile(locatorFile, data);
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
    if (Thread.currentThread() == myWriteActionThread) {
      return getDefaultModalityState();
    }

    return LaterInvocator.getCurrentModalityState();
  }

  @Override
  @NotNull
  public ModalityState getModalityStateForComponent(@NotNull Component c) {
    Window window = UIUtil.getWindow(c);
    if (window == null) return getNoneModalityState(); //?
    return LaterInvocator.modalityStateForWindow(window);
  }

  @Override
  @NotNull
  public ModalityState getAnyModalityState() {
    return ANY;
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
    assertIsDispatchThread();
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
    if (!force) {
      if (myExitInProgress) return;
      if (!exitConfirmed && getDefaultModalityState() != ModalityState.NON_MODAL) return;
    }

    myExitInProgress = true;
    if (isDispatchThread()) {
      doExit(force, exitConfirmed, restart, beforeRestart);
    }
    else {
      invokeLater(() -> doExit(force, exitConfirmed, restart, beforeRestart), ModalityState.NON_MODAL);
    }
  }

  private void doExit(boolean force, boolean exitConfirmed, boolean restart, String[] beforeRestart) {
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
          Restarter.scheduleRestart(beforeRestart);
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

    ProjectManagerEx projectManager = (ProjectManagerEx)ProjectManager.getInstance();
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
    if (isReadAccessAllowed()) {
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
    if (isReadAccessAllowed()) {
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
    if (isReadAccessAllowed()) {
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
  public boolean runWriteActionWithProgressInDispatchThread(
    @NotNull String title, @Nullable Project project, @Nullable JComponent parentComponent, @Nullable String cancelText,
    @NotNull Consumer<ProgressIndicator> action
  ) {
    Class<?> clazz = action.getClass();
    startWrite(clazz);
    try {
      PotemkinProgress indicator = new PotemkinProgress(title, project, parentComponent, cancelText);
      indicator.runInSwingThread(() -> action.consume(indicator));
      return !indicator.isCanceled();
    }
    finally {
      endWrite(clazz);
    }
  }

  @ApiStatus.Experimental
  public boolean runWriteActionWithProgressInBackgroundThread(
    @NotNull String title, @Nullable Project project, @Nullable JComponent parentComponent, @Nullable String cancelText,
    @NotNull Consumer<ProgressIndicator> action
  ) {
    Class<?> clazz = action.getClass();
    startWrite(clazz);
    try {
      PotemkinProgress indicator = new PotemkinProgress(title, project, parentComponent, cancelText);
      indicator.runInBackground(() -> {
        assert myWriteActionThread == null;
        myWriteActionThread = Thread.currentThread();
        try {
          action.consume(indicator);
        } finally {
          myWriteActionThread = null;
        }
      });
      return !indicator.isCanceled();
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
    startWrite(clazz);
    try {
      return computation.compute();
    }
    finally {
      endWrite(clazz);
    }
  }

  @Override
  public <T, E extends Throwable> T runWriteAction(@NotNull ThrowableComputable<T, E> computation) throws E {
    Class<? extends ThrowableComputable> clazz = computation.getClass();
    startWrite(clazz);
    try {
      return computation.compute();
    }
    finally {
      endWrite(clazz);
    }
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
    if (o == null) return "null";
    return o + " " + System.identityHashCode(o);
  }

  private static Thread getEventQueueThread() {
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    return AWTAccessor.getEventQueueAccessor().getDispatchThread(eventQueue);
  }

  @Override
  public boolean isReadAccessAllowed() {
    if (isDispatchThread()) {
      return myWriteActionThread == null; // no reading from EDT during background write action
    }
    return myLock.isReadLockedByThisThread() || myWriteActionThread == Thread.currentThread();
  }

  @Override
  public void assertIsDispatchThread() {
    if (isDispatchThread()) return;
    if (ShutDownTracker.isShutdownHookRunning()) return;
    assertIsDispatchThread("Access is allowed from event dispatch thread only.");
  }

  private void assertIsDispatchThread(@NotNull String message) {
    if (isDispatchThread()) return;
    final Attachment dump = new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString());
    throw new LogEventException(message,
              " EventQueue.isDispatchThread()="+EventQueue.isDispatchThread()+
              " isDispatchThread()="+isDispatchThread()+
              " Toolkit.getEventQueue()="+Toolkit.getDefaultToolkit().getSystemEventQueue()+
              " Current thread: " + describe(Thread.currentThread())+
              " SystemEventQueueThread: " + describe(getEventQueueThread()), dump);
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
    if (isReadAccessAllowed()) {
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
    return isReadAccessAllowed() ? AccessToken.EMPTY_ACCESS_TOKEN : new ReadAccessToken();
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
      ActionPauses.WRITE.started("write action ("+clazz+")");
    }
    myWriteActionPending = true;
    try {
      ActivityTracker.getInstance().inc();
      fireBeforeWriteActionStart(clazz);

      if (!myLock.isWriteLocked() && !myLock.tryWriteLock()) {
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

    public WriteAccessToken(@NotNull Class clazz) {
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
    return isDispatchThread() && myLock.isWriteLocked() || myWriteActionThread == Thread.currentThread();
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

  public void editorPaintStart() {
    myInEditorPaintCounter++;
  }

  public void editorPaintFinish() {
    myInEditorPaintCounter--;
    LOG.assertTrue(myInEditorPaintCounter >= 0);
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
    if (myDoNotSave) return;

    if (mySaveSettingsIsInProgress.compareAndSet(false, true)) {
      HeavyProcessLatch.INSTANCE.prioritizeUiActivity();
      try {
        StoreUtil.save(ServiceKt.getStateStore(this), null);
      }
      finally {
        mySaveSettingsIsInProgress.set(false);
      }
    }
  }

  @Override
  public void saveAll() {
    if (myDoNotSave) return;

    StoreUtil.saveDocumentsAndProjectsAndApp();
  }

  @Override
  public void doNotSave() {
    doNotSave(true);
  }

  @Override
  public void doNotSave(boolean value) {
    myDoNotSave = value;
  }

  @Override
  public boolean isDoNotSave() {
    return myDoNotSave;
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

  @Override
  protected boolean logSlowComponents() {
    return super.logSlowComponents() || ApplicationInfoImpl.getShadowInstance().isEAP();
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

  @TestOnly
  void disableEventsUntil(@NotNull Disposable disposable) {
    final List<ApplicationListener> listeners = new ArrayList<>(myDispatcher.getListeners());
    myDispatcher.getListeners().removeAll(listeners);
    Disposer.register(disposable, () -> myDispatcher.getListeners().addAll(listeners));
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated duplicate parameters; use {@link #exit(boolean, boolean, boolean)} instead (to be removed in IDEA 17) */
  public void exit(boolean force, boolean exitConfirmed, boolean allowListenersToCancel, boolean restart) {
    exit(force, exitConfirmed, restart);
  }
  //</editor-fold>
}