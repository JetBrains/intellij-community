/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.*;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.impl.ApplicationPathMacroManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.components.impl.stores.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.PsiLock;
import com.intellij.ui.Splash;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ReflectionCache;
import com.intellij.util.Restarter;
import com.intellij.util.containers.Stack;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.MutablePicoContainer;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class ApplicationImpl extends ComponentManagerImpl implements ApplicationEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.impl.ApplicationImpl");
  private final ModalityState MODALITY_STATE_NONE = ModalityState.NON_MODAL;

  // about writer preference: the way the java.util.concurrent.locks.ReentrantReadWriteLock.NonfairSync is implemented, the
  // writer thread will be always at the queue head and therefore, java.util.concurrent.locks.ReentrantReadWriteLock.NonfairSync.readerShouldBlock()
  // will return true if the write action is pending, exactly as we need
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock(false);

  private final ModalityInvokator myInvokator = new ModalityInvokatorImpl();

  private final EventDispatcher<ApplicationListener> myDispatcher = EventDispatcher.create(ApplicationListener.class);

  private IApplicationStore myComponentStore;

  private boolean myTestModeFlag;
  private final boolean myHeadlessMode;
  private final boolean myCommandLineMode;

  private final boolean myIsInternal;
  private final String myName;

  private final Stack<Class> myWriteActionsStack = new Stack<Class>(); // accessed from EDT only, no need to sync

  private volatile Runnable myExceptionalThreadWithReadAccessRunnable;

  private int myInEditorPaintCounter = 0;
  private long myStartTime = 0;
  @Nullable
  private Splash mySplash;
  private boolean myDoNotSave;
  private volatile boolean myDisposeInProgress = false;

  private int myRestartCode = 0;
  private volatile int myExitCode = 0;
  private final Disposable myLastDisposable = Disposer.newDisposable(); // will be disposed last
  
  private boolean myHandlingInitComponentError;

  private final AtomicBoolean mySaveSettingsIsInProgress = new AtomicBoolean(false);
  @SuppressWarnings({"UseOfArchaicSystemPropertyAccessors"})
  private static final int ourDumpThreadsOnLongWriteActionWaiting = Integer.getInteger("dump.threads.on.long.write.action.waiting", 0);
  private final AtomicInteger myAliveThreads = new AtomicInteger(0);
  private static final int ourReasonableThreadPoolSize = Registry.intValue("core.pooled.threads");

  private final ExecutorService ourThreadExecutorsService = new ThreadPoolExecutor(
    3,
    Integer.MAX_VALUE,
    5 * 60L,
    TimeUnit.SECONDS,
    new SynchronousQueue<Runnable>(),
    new ThreadFactory() {
      int i;
      @Override
      public Thread newThread(Runnable r) {
        final int count = myAliveThreads.incrementAndGet();
        final Thread thread = new Thread(r, "ApplicationImpl pooled thread "+i++) {
          @Override
          public void interrupt() {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Interrupted worker, will remove from pool");
            }
            super.interrupt();
          }

          @Override
          public void run() {
            try {
              super.run();
            }
            catch (Throwable t) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Worker exits due to exception", t);
              }
            }
            myAliveThreads.decrementAndGet();
          }
        };
        if (ApplicationInfoImpl.getShadowInstance().isEAP() && count > ourReasonableThreadPoolSize) {
          LOG.info("Not enough pooled threads; creating one at:", new Throwable());
        }
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
      }
    }
  );
  private boolean myIsFiringLoadingEvent = false;
  private boolean myLoaded = false;
  @NonNls private static final String WAS_EVER_SHOWN = "was.ever.shown";

  private Boolean myActive;

  private static final ThreadLocal<Integer> ourEdtSafe = new ThreadLocal<Integer>();
  @NonNls private static final ModalityState ANY = new ModalityState() {
    @Override
    public boolean dominates(@NotNull ModalityState anotherState) {
      return false;
    }

    @NonNls
    @Override
    public String toString() {
      return "ANY";
    }
  };

  @Override
  protected void bootstrapPicoContainer() {
    super.bootstrapPicoContainer();
    getPicoContainer().registerComponentImplementation(IComponentStore.class, StoresFactory.getApplicationStoreClass());
    getPicoContainer().registerComponentImplementation(ApplicationPathMacroManager.class);
  }

  @NotNull
  public synchronized IApplicationStore getStateStore() {
    if (myComponentStore == null) {
      myComponentStore = (IApplicationStore)getPicoContainer().getComponentInstance(IComponentStore.class);
    }
    return myComponentStore;
  }

  @Override
  public void initializeComponent(Object component, boolean service) {
    getStateStore().initComponent(component, service);
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

    CommonBundle.assertKeyIsFound = isUnitTestMode;
    AWTExceptionHandler.register(); // do not crash AWT on exceptions
    if ((isInternal || isUnitTestMode) && !Comparing.equal("off", System.getProperty("idea.disposer.debug"))) {
      Disposer.setDebugMode(true);
    }
    myStartTime = System.currentTimeMillis();
    mySplash = splash;
    myName = appName;

    myIsInternal = isInternal;
    myTestModeFlag = isUnitTestMode;
    myHeadlessMode = isHeadless;
    myCommandLineMode = isCommandLine;

    myDoNotSave = myTestModeFlag || myHeadlessMode;

    loadApplicationComponents();

    if (myTestModeFlag) {
      registerShutdownHook();
    }

    if (!isUnitTestMode && !isHeadless) {
      Disposer.register(this, Disposer.newDisposable(), "ui");

      StartupUtil.addExternalInstanceListener(new Consumer<List<String>>() {
        @Override
        public void consume(final List<String> args) {
          invokeLater(new Runnable() {
            @Override
            public void run() {
              final Project project = CommandLineProcessor.processExternalCommandLine(args);
              final IdeFrame frame;
              if (project != null) {
                frame = WindowManager.getInstance().getIdeFrame(project);
              }
              else {
                frame = WindowManager.getInstance().getAllFrames() [0];
              }
              ((IdeFrameImpl)frame).requestFocus();
            }
          });
        }
      });
    }

    myRestartCode = Restarter.getRestartCode();

    registerFont("/fonts/Inconsolata.ttf");
    registerFont("/fonts/SourceCodePro-Regular.ttf");
    registerFont("/fonts/SourceCodePro-Bold.ttf");
  }

  private void registerFont(@NonNls String name) {
    if (isHeadlessEnvironment()) return;

    InputStream is = null;
    try {
      is = getClass().getResourceAsStream(name);
      final Font font = Font.createFont(Font.TRUETYPE_FONT, is);
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
    }
    catch (Exception e) {
      LOG.info(e);
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void registerShutdownHook() {
    ShutDownTracker.getInstance(); // Necessary to avoid creating an instance while already shutting down.

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        if (isDisposed() || isDisposeInProgress()) {
          return;
        }
        ShutDownTracker.invokeAndWait(isUnitTestMode(), true, new Runnable() {
          @Override
          public void run() {
            if (ApplicationManager.getApplication() != ApplicationImpl.this) return;
            try {
              myDisposeInProgress = true;
              saveAll();
            }
            finally {
              if (!disposeSelf(true)) {
                myDisposeInProgress = false;
              }
            }
          }
        });
      }
    });
  }

  private boolean disposeSelf(final boolean checkCanCloseProject) {
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    final Ref<Boolean> canClose = new Ref<Boolean>(true);
    for (final Project project : ProjectManagerEx.getInstanceEx().getOpenProjects()) {
      try {
        commandProcessor.executeCommand(project, new Runnable() {
          @Override
          public void run() {
            final ProjectManagerImpl manager = (ProjectManagerImpl)ProjectManagerEx.getInstanceEx();
            if (!manager.closeProject(project, true, true, checkCanCloseProject)) {
              canClose.set(false);
            }
          }
        }, ApplicationBundle.message("command.exit"), null);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      if (!canClose.get()) {
        return false;
      }
    }
    Disposer.dispose(this);

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
    return myLock.getReadHoldCount() != 0;
  }

  @Override
  protected void handleInitComponentError(final Throwable ex, final boolean fatal, final String componentClassName) {
    if (myHandlingInitComponentError) {
      return;
    }
    myHandlingInitComponentError = true;
    try {
      if (PluginManager.isPluginClass(componentClassName)) {
        LOG.error(ex);
        PluginId pluginId = PluginManager.getPluginByClassName(componentClassName);
        @NonNls final String errorMessage =
          "Plugin " + pluginId.getIdString() + " failed to initialize and will be disabled:\n" + ex.getMessage() +
          "\nPlease restart " + ApplicationNamesInfo.getInstance().getFullProductName() + ".";
        PluginManager.disablePlugin(pluginId.getIdString());
        if (!myHeadlessMode) {
          JOptionPane.showMessageDialog(null, errorMessage);
        }
        else {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println(errorMessage);
          System.exit(1);
        }
        return;  // do not call super
      }
      if (fatal) {
        LOG.error(ex);
        @NonNls final String errorMessage = "Fatal error initializing class " + componentClassName + ":\n" +
                                            StringUtil.trimLog(ex.toString(), 239) +
                                            "\nComplete error stacktrace was written to " + PathManager.getLogPath() + "/idea.log";
        if (!myHeadlessMode) {
          JOptionPane.showMessageDialog(null, errorMessage);
        }
        else {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println(errorMessage);
        }
      }
      super.handleInitComponentError(ex, fatal, componentClassName);
    }
    finally {
      myHandlingInitComponentError = false;
    }
  }

  private void loadApplicationComponents() {
    PluginManager.initPlugins(mySplash);
    final IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (PluginManager.shouldSkipPlugin(plugin)) continue;
      loadComponentsConfiguration(plugin.getAppComponents(), plugin, false);
    }
  }

  @Override
  protected synchronized Object createComponent(Class componentInterface) {
    Object component = super.createComponent(componentInterface);
    if (mySplash != null) {
      mySplash.showProgress("", (float)(0.65f + getPercentageOfComponentsLoaded() * 0.35f));
    }
    return component;
  }

  @Override
  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getRootArea().getPicoContainer();
  }

  @Override
  public boolean isInternal() {
    return myIsInternal;
  }

  @Override
  public boolean isUnitTestMode() {
    return myTestModeFlag;
  }

  public void setUnitTestMode(boolean testModeFlag) {
    myTestModeFlag = testModeFlag;
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
  public Future<?> executeOnPooledThread(@NotNull final Runnable action) {
    return ourThreadExecutorsService.submit(new Runnable() {
      @Override
      public void run() {
        try {
          action.run();
        }
        catch (ProcessCanceledException e) {
          // ignore
        }
        catch (Throwable t) {
          LOG.error(t);
        }
        finally {
          //ReflectionUtil.resetThreadLocals();
          Thread.interrupted(); // reset interrupted status
        }
      }
    });
  }

  @Override
  public <T> Future<T> executeOnPooledThread(@NotNull final Callable<T> action) {
    return ourThreadExecutorsService.submit(new Callable<T>() {
      @Override
      public T call() {
        try {
          return action.call();
        }
        catch (ProcessCanceledException e) {
          // ignore
        }
        catch (Throwable t) {
          LOG.error(t);
        }
        finally {
          //ReflectionUtil.resetThreadLocals();
          Thread.interrupted(); // reset interrupted status
        }
        return null;
      }
    });
  }

  private static Thread ourDispatchThread = null;

  @Override
  public boolean isDispatchThread() {
    return EventQueue.isDispatchThread();
  }

  @Override
  @NotNull
  public ModalityInvokator getInvokator() {
    return myInvokator;
  }


  @Override
  public void invokeLater(@NotNull final Runnable runnable) {
    myInvokator.invokeLater(runnable);
  }

  @Override
  public void invokeLater(@NotNull final Runnable runnable, @NotNull final Condition expired) {
    myInvokator.invokeLater(runnable, expired);
  }

  @Override
  public void invokeLater(@NotNull final Runnable runnable, @NotNull final ModalityState state) {
    myInvokator.invokeLater(runnable, state);
  }

  @Override
  public void invokeLater(@NotNull final Runnable runnable, @NotNull final ModalityState state, @NotNull final Condition expired) {
    myInvokator.invokeLater(runnable, state, expired);
  }

  @Override
  public void load(String path) throws IOException, InvalidDataException {
    getStateStore().setOptionsPath(path);
    getStateStore().setConfigPath(PathManager.getConfigPath());
    myIsFiringLoadingEvent = true;
    try {
      fireBeforeApplicationLoaded();
    }
    finally {
      myIsFiringLoadingEvent = false;
    }

    loadComponentRoamingTypes();

    HeavyProcessLatch.INSTANCE.processStarted();
    try {
      getStateStore().load();
    }
    catch (StateStorageException e) {
      throw new IOException(e.getMessage());
    }
    finally {
      HeavyProcessLatch.INSTANCE.processFinished();
    }
    myLoaded = true;
  }

  @Override
  public boolean isLoaded() {
    return myLoaded;
  }

  @Override
  protected <T> T getComponentFromContainer(final Class<T> interfaceClass) {
    if (myIsFiringLoadingEvent) {
      return null;
    }
    return super.getComponentFromContainer(interfaceClass);
  }

  private static void loadComponentRoamingTypes() {
    ExtensionPoint<RoamingTypeExtensionPointBean> point = Extensions.getRootArea().getExtensionPoint("com.intellij.ComponentRoamingType");
    final RoamingTypeExtensionPointBean[] componentRoamingTypes = point.getExtensions();

    for (RoamingTypeExtensionPointBean object : componentRoamingTypes) {

      assert object.componentName != null;
      assert object.roamingType != null;

      final RoamingType type = RoamingType.valueOf(object.roamingType);

      assert type != null;

      ComponentRoamingManager.getInstance().setRoamingType(object.componentName, type);
    }
  }

  private void fireBeforeApplicationLoaded() {
    ExtensionPoint<ApplicationLoadListener> point = Extensions.getRootArea().getExtensionPoint("com.intellij.ApplicationLoadListener");
    final ApplicationLoadListener[] objects = point.getExtensions();
    for (ApplicationLoadListener object : objects) {
      try {
        object.beforeApplicationLoaded(this);
      }
      catch(Exception e) {
        LOG.error(e);
      }
    }
  }


  @Override
  public void dispose() {
    fireApplicationExiting();

    ShutDownTracker.getInstance().ensureStopperThreadsFinished();

    disposeComponents();

    ourThreadExecutorsService.shutdownNow();
    myComponentStore = null;
    super.dispose();
    Disposer.dispose(myLastDisposable); // dispose it last
  }

  private final Object lock = new Object();
  private void makeChangesVisibleToEDT() {
    synchronized (lock) {
      lock.hashCode();
    }
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

    if (myExceptionalThreadWithReadAccessRunnable != null ||
        ApplicationManager.getApplication().isUnitTestMode() ||
        ApplicationManager.getApplication().isHeadlessEnvironment()) {
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
    progress.setTitle(progressTitle);

    try {
      myExceptionalThreadWithReadAccessRunnable = process;
      final boolean[] threadStarted = {false};
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (myExceptionalThreadWithReadAccessRunnable != process) {
              LOG.error("myExceptionalThreadWithReadAccessRunnable != process, process = " + myExceptionalThreadWithReadAccessRunnable);
          }

          executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              if (myExceptionalThreadWithReadAccessRunnable != process) {
                LOG.error("myExceptionalThreadWithReadAccessRunnable != process, process = " + myExceptionalThreadWithReadAccessRunnable);
              }

              final boolean old = setExceptionalThreadWithReadAccessFlag(true);
              LOG.assertTrue(isReadAccessAllowed());
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
              finally {
                setExceptionalThreadWithReadAccessFlag(old);
                makeChangesVisibleToEDT();
              }
            }
          });
          threadStarted[0] = true;
        }
      });

      progress.startBlocking();

      LOG.assertTrue(threadStarted[0]);
      LOG.assertTrue(!progress.isRunning());
    }
    finally {
      myExceptionalThreadWithReadAccessRunnable = null;
      makeChangesVisibleToEDT();
    }

    return !progress.isCanceled();
  }

  @Override
  public boolean isInModalProgressThread() {
    if (myExceptionalThreadWithReadAccessRunnable == null || !isExceptionalThreadWithReadAccess()) {
      return false;
    }
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    return progressIndicator.isModal() && ((ProgressIndicatorEx)progressIndicator).isModalityEntered();
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    if (isDispatchThread()) {
      LOG.error("invokeAndWait must not be called from event queue thread");
      runnable.run();
      return;
    }

    if (isExceptionalThreadWithReadAccess()) { //OK if we're in exceptional thread.
      LaterInvocator.invokeAndWait(runnable, modalityState);
      return;
    }

    if (holdsReadLock()) {
      LOG.error("Calling invokeAndWait from read-action leads to possible deadlock.");
    }

    LaterInvocator.invokeAndWait(runnable, modalityState);
  }

  @Override
  @NotNull
  public ModalityState getCurrentModalityState() {
    Object[] entities = LaterInvocator.getCurrentModalEntities();
    return entities.length > 0 ? new ModalityStateEx(entities) : getNoneModalityState();
  }

  @Override
  @NotNull
  public ModalityState getModalityStateForComponent(@NotNull Component c) {
    Window window = c instanceof Window ? (Window)c : SwingUtilities.windowForComponent(c);
    if (window == null) return getNoneModalityState(); //?
    return LaterInvocator.modalityStateForWindow(window);
  }

  @Override
  public ModalityState getAnyModalityState() {
    return ANY;
  }

  @Override
  @NotNull
  public ModalityState getDefaultModalityState() {
    if (EventQueue.isDispatchThread()) {
      return getCurrentModalityState();
    }
    else {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      return progress == null ? getNoneModalityState() : progress.getModalityState();
    }
  }

  @Override
  @NotNull
  public ModalityState getNoneModalityState() {
    return MODALITY_STATE_NONE;
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
    exit(false);
  }

  @Override
  public void exit(final boolean force) {
    exit(force, true);
  }

  public void exit(final boolean force, final boolean allowListenersToCancel) {
    if (!force && getDefaultModalityState() != ModalityState.NON_MODAL) {
      return;
    }

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (!force && !showConfirmation()) {
          saveAll();
          myExitCode = 0;
          return;
        }

        getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).appClosing();
        myDisposeInProgress = true;
        if (!doExit(allowListenersToCancel)) {
          myDisposeInProgress = false;
          myExitCode = 0;
        }
      }
    };

    if (!isDispatchThread()) {
      invokeLater(runnable, ModalityState.NON_MODAL);
    }
    else {
      runnable.run();
    }
  }

  private boolean doExit(boolean allowListenersToCancel) {
    saveSettings();

    if (allowListenersToCancel && !canExit()) {
      return false;
    }

    final boolean success = disposeSelf(allowListenersToCancel);
    if (!success || isUnitTestMode()) {
      return false;
    }

    System.exit(myExitCode);
    return true;
  }

  private static boolean showConfirmation() {
    final boolean hasUnsafeBgTasks = ProgressManager.getInstance().hasUnsafeProgressIndicator();

    DialogWrapper.DoNotAskOption option = new DialogWrapper.DoNotAskOption() {
      @Override
      public boolean isToBeShown() {
        return GeneralSettings.getInstance().isConfirmExit();
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
      public String getDoNotShowMessage() {
        return "Do not ask me again";
      }
    };

    if (hasUnsafeBgTasks || option.isToBeShown()) {
      String message = ApplicationBundle
        .message(hasUnsafeBgTasks ? "exit.confirm.prompt.tasks" : "exit.confirm.prompt",
                 ApplicationNamesInfo.getInstance().getFullProductName());

      if (DialogWrapper.OK_EXIT_CODE != Messages.showYesNoDialog(message, ApplicationBundle.message("exit.confirm.title"),
                                        ApplicationBundle.message("command.exit"), "Cancel",
                                        Messages.getQuestionIcon(), option)) {
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
      assertReadActionAllowed();
      try {
        myLock.readLock().lockInterruptibly();
      }
      catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
      try {
        action.run();
      }
      finally {
        myLock.readLock().unlock();
      }
    }
  }
  @Override
  public <T> T runReadAction(@NotNull final Computable<T> computation) {
    if (isReadAccessAllowed()) {
      return computation.compute();
    }
    else {
      assertReadActionAllowed();
      try {
        myLock.readLock().lockInterruptibly();
      }
      catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
      try {
        return computation.compute();
      }
      finally {
        myLock.readLock().unlock();
      }
    }
  }

  private static final ThreadLocal<Boolean> exceptionalThreadWithReadAccessFlag = new ThreadLocal<Boolean>();

  private static boolean isExceptionalThreadWithReadAccess() {
    Boolean flag = exceptionalThreadWithReadAccessFlag.get();
    return flag == Boolean.TRUE;
  }

  public static boolean setExceptionalThreadWithReadAccessFlag(boolean flag) {
    boolean old = isExceptionalThreadWithReadAccess();
    if (flag) {
      exceptionalThreadWithReadAccessFlag.set(Boolean.TRUE);
    }
    else {
      exceptionalThreadWithReadAccessFlag.remove();
    }
    return old;
  }

  @Override
  public void runWriteAction(@NotNull final Runnable action) {
    final AccessToken token = acquireWriteActionLock(action.getClass());
    try {
      action.run();
    }
    finally {
      token.finish();
    }
  }

  @Override
  public <T> T runWriteAction(@NotNull final Computable<T> computation) {
    final AccessToken token = acquireWriteActionLock(computation.getClass());
    try {
      return computation.compute();
    }
    finally {
      token.finish();
    }
  }

  @Override
  public boolean hasWriteAction(@Nullable Class<?> actionClass) {
    assertCanRunWriteAction();

    for (int i = myWriteActionsStack.size() - 1; i >= 0; i--) {
      Class action = myWriteActionsStack.get(i);
      if (actionClass == action || action != null && actionClass != null && ReflectionCache.isAssignable(actionClass, action)) return true;
    }
    return false;
  }

  @Override
  public void assertReadAccessAllowed() {
    if (myHeadlessMode) return;
    if (!isReadAccessAllowed()) {
      LOG.error(
        "Read access is allowed from event dispatch thread or inside read-action only (see com.intellij.openapi.application.Application.runReadAction())",
        "Current thread: " + describe(Thread.currentThread()), "Our dispatch thread:" + describe(ourDispatchThread),
        "SystemEventQueueThread: " + describe(getEventQueueThread()));
    }
  }

  @NonNls
  private static String describe(Thread o) {
    if (o == null) return "null";
    return o.toString() + " " + System.identityHashCode(o);
  }

  @Nullable
  private static Thread getEventQueueThread() {
    EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    try {
      Method method = EventQueue.class.getDeclaredMethod("getDispatchThread");
      method.setAccessible(true);
      return (Thread)method.invoke(eventQueue);
    }
    catch (Exception e1) {
      // ok
    }
    return null;
  }

  @Override
  public boolean isReadAccessAllowed() {
    Thread currentThread = Thread.currentThread();
    return ourDispatchThread == currentThread ||
           isExceptionalThreadWithReadAccess() ||
           holdsReadLock() ||
           isDispatchThread();
  }

  private static void assertCanRunWriteAction() {
    assertIsDispatchThread("Write access is allowed from event dispatch thread only");
  }

  @Override
  public void assertIsDispatchThread() {
    if (ShutDownTracker.isShutdownHookRunning()) return;
    Integer safeCounter = ourEdtSafe.get();
    if (safeCounter != null && safeCounter > 0) return;
    assertIsDispatchThread("Access is allowed from event dispatch thread only.");
  }

  private static void assertIsDispatchThread(String message) {
    final Thread currentThread = Thread.currentThread();
    if (ourDispatchThread == currentThread) return;

    if (EventQueue.isDispatchThread()) {
      ourDispatchThread = currentThread;
    }
    if (ourDispatchThread == currentThread) return;

    LOG.error(message,
              "Current thread: " + describe(Thread.currentThread()),
              "Our dispatch thread:" + describe(ourDispatchThread),
              "SystemEventQueueThread: " + describe(getEventQueueThread()));
  }

  @Override
  public void runEdtSafeAction(@NotNull Runnable runnable) {
    Integer value = ourEdtSafe.get();
    if (value == null) {
      value = Integer.valueOf(0);
    }

    ourEdtSafe.set(value + 1);

    try {
      runnable.run();
    }
    finally {
      int newValue = ourEdtSafe.get() - 1;
      ourEdtSafe.set(newValue >= 1 ? newValue : null);
    }
  }

  @Override
  public void assertIsDispatchThread(@Nullable final JComponent component) {
    if (component == null) return;

    Thread curThread = Thread.currentThread();
    if (ourDispatchThread == curThread) {
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
    boolean mustAcquire = !isReadAccessAllowed();

    if (mustAcquire) {
      LOG.assertTrue(myTestModeFlag || !Thread.holdsLock(PsiLock.LOCK), "Thread must not hold PsiLock while performing readAction");
      try {
        // timed version of tryLock() respects fairness unlike the no-args method
        if (!myLock.readLock().tryLock(0, TimeUnit.MILLISECONDS)) return false;
      }
      catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    try {
      action.run();
    }
    finally {
      if (mustAcquire) {
        myLock.readLock().unlock();
      }
    }
    return true;
  }

  public boolean tryToApplyActivationState(boolean active, Window window) {
    final Component frame = UIUtil.findUltimateParent(window);

    if (frame instanceof IdeFrame) {
      final IdeFrame ideFrame = (IdeFrame)frame;
      if (isActive() != active) {
        myActive = Boolean.valueOf(active);
        System.setProperty("idea.active", myActive.toString());
        ApplicationActivationListener publisher = getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC);
        if (active) {
          publisher.applicationActivated(ideFrame);
        }
        else {
          publisher.applicationDeactivated(ideFrame);
        }
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean isActive() {
    if (isUnitTestMode()) return true;

    if (myActive == null) {
      Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      return active != null;
    }

    return myActive;
  }

  @Override
  public AccessToken acquireReadActionLock() {
    // if we are inside read action, do not try to acquire read lock again since it will deadlock if there is a pending writeAction
    if (isReadAccessAllowed()) return AccessToken.EMPTY_ACCESS_TOKEN;

    return new ReadAccessToken();
  }

  @Override
  public AccessToken acquireWriteActionLock(Class clazz) {
    return new WriteAccessToken(clazz);
  }

  private class WriteAccessToken extends AccessToken {
    private final Class clazz;

    public WriteAccessToken(Class _clazz) {
      clazz = _clazz;
      assertCanRunWriteAction();

      ActivityTracker.getInstance().inc();
      fireBeforeWriteActionStart(_clazz);
      final AtomicBoolean stopped = new AtomicBoolean(false);

      LOG.assertTrue(isWriteAccessAllowed() || !Thread.holdsLock(PsiLock.LOCK), "Thread must not hold PsiLock while performing writeAction");
      try {
        if (!myLock.writeLock().tryLock()) {
          if (ourDumpThreadsOnLongWriteActionWaiting > 0) {
            executeOnPooledThread(new Runnable() {
              @Override
              public void run() {
                while (!stopped.get()) {
                  try {
                    Thread.sleep(ourDumpThreadsOnLongWriteActionWaiting);
                    if (!stopped.get()) {
                      PerformanceWatcher.getInstance().dumpThreads(true);
                    }
                  }
                  catch (InterruptedException ignored) {
                  }
                }
              }
            });
          }
          myLock.writeLock().lockInterruptibly();
        }
        acquired();
      }
      catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
      stopped.set(true);

      myWriteActionsStack.push(_clazz);

      fireWriteActionStarted(_clazz);
    }

    @Override
    public void finish() {
      try {
        fireWriteActionFinished(clazz);

        myWriteActionsStack.pop();
      }
      finally {
        myLock.writeLock().unlock();
        released();
      }
    }

    @Override
    protected void acquired() {
      String id = id();

      if (id != null) {
        final Thread thread = Thread.currentThread();
        thread.setName(thread.getName() + id);
      }
    }

    @Override
    protected void released() {
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
      assertReadActionAllowed();
      try {
        myLock.readLock().lockInterruptibly();
        acquired();
      }
      catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }

    @Override
    public void finish() {
      myLock.readLock().unlock();
      released();
    }
  }

  private static void assertReadActionAllowed() {
    LOG.assertTrue(!Thread.holdsLock(PsiLock.LOCK), "Thread must not hold PsiLock while performing readAction");
  }

  @Override
  public void assertWriteAccessAllowed() {
    LOG.assertTrue(isWriteAccessAllowed(),
                   "Write access is allowed inside write-action only (see com.intellij.openapi.application.Application.runWriteAction())");
  }

  @Override
  public boolean isWriteAccessAllowed() {
    return myLock.writeLock().isHeldByCurrentThread();
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

  private void fireBeforeWriteActionStart(Class action) {
    myDispatcher.getMulticaster().beforeWriteActionStart(action);
  }

  private void fireWriteActionStarted(Class action) {
    myDispatcher.getMulticaster().writeActionStarted(action);
  }

  private void fireWriteActionFinished(Class action) {
    myDispatcher.getMulticaster().writeActionFinished(action);
  }

  public void _saveSettings() { // public for testing purposes
    if (mySaveSettingsIsInProgress.compareAndSet(false, true)) {
      try {
        StoreUtil.doSave(getStateStore());
      }
      catch (final Throwable ex) {
        if (isUnitTestMode()) {
          System.out.println("Saving application settings failed");
          ex.printStackTrace();
        }
        else {
          LOG.info("Saving application settings failed", ex);
          invokeLater(new Runnable() {
            @Override
            public void run() {
              if (ex instanceof PluginException) {
                final PluginException pluginException = (PluginException)ex;
                PluginManager.disablePlugin(pluginException.getPluginId().getIdString());
                Messages.showMessageDialog("The plugin " +
                                           pluginException.getPluginId() +
                                           " failed to save settings and has been disabled. Please restart " +
                                           ApplicationNamesInfo.getInstance().getFullProductName(), CommonBundle.getErrorTitle(),
                                           Messages.getErrorIcon());
              }
              else {
                Messages.showMessageDialog(ApplicationBundle.message("application.save.settings.error", ex.getLocalizedMessage()),
                                           CommonBundle.getErrorTitle(), Messages.getErrorIcon());

              }
            }
          });
        }
      }
      finally {
        mySaveSettingsIsInProgress.set(false);
      }
    }
  }

  @Override
  public void saveSettings() {
    if (myDoNotSave) return;
    _saveSettings();
  }

  @Override
  public void saveAll() {
    if (myDoNotSave) return;

    FileDocumentManager.getInstance().saveAllDocuments();

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      ProjectEx project = (ProjectEx)openProject;
      project.save();
    }

    saveSettings();
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

  @Override
  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    return Extensions.getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  @Override
  public boolean isDisposeInProgress() {
    return myDisposeInProgress || ShutDownTracker.isShutdownHookRunning();
  }

  @Override
  public boolean isRestartCapable() {
    return Restarter.isSupported() || myRestartCode > 0;
  }

  @Override
  public void restart() {
    boolean restarted = false;
    try {
      restarted = Restarter.restart();
    }
    catch (Restarter.CannotRestartException e) {
      LOG.warn(e);
    }

    if (!restarted) {
      myExitCode = myRestartCode;
    }

    exit(true);
  }

  public boolean isSaving() {
    if (getStateStore().isSaving()) return true;
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      ProjectEx project = (ProjectEx)openProject;
      if (project.getStateStore().isSaving()) return true;
    }

    return false;
  }

  @Override
  protected boolean logSlowComponents() {
    return super.logSlowComponents() || ApplicationInfoImpl.getShadowInstance().isEAP();
  }

  @TestOnly
  public void setDisposeInProgress(boolean disposeInProgress) {
    myDisposeInProgress = disposeInProgress;
  }

  @NonNls
  @Override
  public String toString() {
    return "Application" +
           (isDisposed() ? " (Disposed)" : "") +
           (isUnitTestMode() ? " (Unit test)" : "") +
           (isInternal() ? " (Internal)" : "") +
           (isHeadlessEnvironment() ? " (Headless)" : "") +
           (isCommandLine() ? " (Command line)" : "");
  }
}
