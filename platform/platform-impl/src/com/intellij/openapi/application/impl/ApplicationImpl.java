/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.Patches;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.ApplicationLoadListener;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdeRepaintManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateStorage;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.psi.PsiLock;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ReflectionCache;
import com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.MutablePicoContainer;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
public class ApplicationImpl extends ComponentManagerImpl implements ApplicationEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.impl.ApplicationImpl");
  private final ModalityState MODALITY_STATE_NONE = ModalityState.NON_MODAL;
  private final ModalityInvokator myInvokator = new ModalityInvokatorImpl();

  private final EventDispatcher<ApplicationListener> myDispatcher = EventDispatcher.create(ApplicationListener.class);

  private final boolean myTestModeFlag;
  private final boolean myHeadlessMode;
  private final boolean myCommandLineMode;

  private final boolean myIsInternal;
  private final String myName;

  private final ReentrantWriterPreferenceReadWriteLock myActionsLock = new ReentrantWriterPreferenceReadWriteLock();
  private final Stack<Runnable> myWriteActionsStack = new Stack<Runnable>();

  private volatile Runnable myExceptionalThreadWithReadAccessRunnable;

  private int myInEditorPaintCounter = 0;
  private long myStartTime = 0;
  private boolean myDoNotSave = false;
  private volatile boolean myDisposeInProgress = false;

  private final AtomicBoolean mySaveSettingsIsInProgress = new AtomicBoolean(false);

  private final ExecutorService ourThreadExecutorsService = new ThreadPoolExecutor(
    3,
    Integer.MAX_VALUE,
    5 * 60L,
    TimeUnit.SECONDS,
    new SynchronousQueue<Runnable>(),
    new ThreadFactory() {
      public Thread newThread(Runnable r) {
        final Thread thread = new Thread(r, "ApplicationImpl pooled thread") {
          public void interrupt() {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Interrupted worker, will remove from pool");
            }
            super.interrupt();
          }

          public void run() {
            try {
              super.run();
            }
            catch (Throwable t) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Worker exits due to exception", t);
              }
            }
          }
        };
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
      }
    }
  );
  private boolean myIsFiringLoadingEvent = false;
  @NonNls private static final String WAS_EVER_SHOWN = "was.ever.shown";

  private Boolean myActive;

  private static final ThreadLocal<Integer> ourEdtSafe = new ThreadLocal<Integer>();

  protected void boostrapPicoContainer() {
    super.boostrapPicoContainer();
    getPicoContainer().registerComponentImplementation(IComponentStore.class, StoresFactory.getApplicationStoreClass());
    getPicoContainer().registerComponentImplementation(ApplicationPathMacroManager.class);
  }

  @Override
  @NotNull
  public synchronized IApplicationStore getStateStore() {
    return (IApplicationStore)super.getStateStore();
  }

  public ApplicationImpl(boolean isInternal, boolean isUnitTestMode, boolean isHeadless, boolean isCommandLine, String appName) {
    super(null);

    getPicoContainer().registerComponentInstance(Application.class, this);

    CommonBundle.assertKeyIsFound = isUnitTestMode;

    if ((isInternal || isUnitTestMode) && !Comparing.equal("off", System.getProperty("idea.disposer.debug"))) {
      Disposer.setDebugMode(true);
    }
    myStartTime = System.currentTimeMillis();
    myName = appName;
    ApplicationManagerEx.setApplication(this);

    PluginsFacade.INSTANCE = new PluginsFacade() {
      public IdeaPluginDescriptor getPlugin(PluginId id) {
        return PluginManager.getPlugin(id);
      }

      public IdeaPluginDescriptor[] getPlugins() {
        return PluginManager.getPlugins();
      }
    };

    if (!isUnitTestMode && !isHeadless) {
      Toolkit.getDefaultToolkit().getSystemEventQueue().push(IdeEventQueue.getInstance());
      if (Patches.SUN_BUG_ID_6209673) {
        RepaintManager.setCurrentManager(new IdeRepaintManager());
      }
      IconLoader.activate();
    }

    myIsInternal = isInternal;
    myTestModeFlag = isUnitTestMode;
    myHeadlessMode = isHeadless;
    myCommandLineMode = isCommandLine;

    loadApplicationComponents();

    if (myTestModeFlag) {
      registerShutdownHook();
    }

    if (!isUnitTestMode && !isHeadless) {
      Disposer.register(this, Disposer.newDisposable(), "ui");
    }
  }

  private void registerShutdownHook() {
    ShutDownTracker.getInstance(); // Necessary to avoid creating an instance while already shutting down.

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      public void run() {
        if (isDisposed() || isDisposeInProgress()) return;
        try {
          SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
              ApplicationManagerEx.setApplication(ApplicationImpl.this);
              saveAll();
              disposeSelf();
            }
          });
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }
        catch (InvocationTargetException e) {
          LOG.error(e);
        }
      }
    });
  }

  private boolean disposeSelf() {
    myDisposeInProgress = true;
    Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
    final boolean[] canClose = {true};
    for (final Project project : openProjects) {
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      commandProcessor.executeCommand(project, new Runnable() {
        public void run() {
          canClose[0] = ProjectUtil.closeProject(project);
        }
      }, ApplicationBundle.message("command.exit"), null);
      if (!canClose[0]) return false;
    }
    Disposer.dispose(this);

    Disposer.assertIsEmpty();
    return true;
  }

  public String getName() {
    return myName;
  }

  public boolean holdsReadLock() {
    return myActionsLock.isReadLockAcquired();
  }

  @Override
  protected void handleInitComponentError(final Throwable ex, final boolean fatal, final String componentClassName) {
    if (PluginManager.isPluginClass(componentClassName)) {
      LOG.error(ex);
      PluginId pluginId = PluginManager.getPluginByClassName(componentClassName);
      @NonNls final String errorMessage = "Plugin " + pluginId.getIdString() + " failed to initialize and will be disabled:\n" + ex.getMessage() +
                                          "\nPlease restart " + ApplicationNamesInfo.getInstance().getFullProductName() + ".";
      PluginManager.disablePlugin(pluginId.getIdString());
      if (!myHeadlessMode) {
        JOptionPane.showMessageDialog(null, errorMessage);
      }
      else {
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println(errorMessage);
      }
      System.exit(1);
    }
    else if (fatal) {
      LOG.error(ex);
      @NonNls final String errorMessage = "Fatal error initializing class " + componentClassName + ":\n" +
                                          ex.toString() +
                                          "\nComplete error stacktrace was written to idea.log";
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

  private void loadApplicationComponents() {
    final IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (PluginManager.shouldSkipPlugin(plugin)) continue;
      loadComponentsConfiguration(plugin.getAppComponents(), plugin, false);
    }
  }

  protected MutablePicoContainer createPicoContainer() {
    return Extensions.getRootArea().getPicoContainer();
  }

  public boolean isInternal() {
    return myIsInternal;
  }

  public boolean isUnitTestMode() {
    return myTestModeFlag;
  }

  public boolean isHeadlessEnvironment() {
    return myHeadlessMode;
  }

  public boolean isCommandLine() {
    return myCommandLineMode;
  }

  public IdeaPluginDescriptor getPlugin(PluginId id) {
    return PluginsFacade.INSTANCE.getPlugin(id);
  }

  public IdeaPluginDescriptor[] getPlugins() {
    return PluginsFacade.INSTANCE.getPlugins();
  }

  public Future<?> executeOnPooledThread(@NotNull final Runnable action) {
    return ourThreadExecutorsService.submit(new Runnable() {
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
          Thread.interrupted(); // reset interrupted status
        }
      }
    });
  }

  private static Thread ourDispatchThread = null;

  public boolean isDispatchThread() {
    return EventQueue.isDispatchThread();
  }

  @NotNull
  public ModalityInvokator getInvokator() {
    return myInvokator;
  }


  public void invokeLater(@NotNull final Runnable runnable) {
    myInvokator.invokeLater(runnable);
  }

  public void invokeLater(@NotNull final Runnable runnable, @NotNull final Condition expired) {
    myInvokator.invokeLater(runnable, expired);
  }

  public void invokeLater(@NotNull final Runnable runnable, @NotNull final ModalityState state) {
    myInvokator.invokeLater(runnable, state);
  }

  public void invokeLater(@NotNull final Runnable runnable, @NotNull final ModalityState state, @NotNull final Condition expired) {
    myInvokator.invokeLater(runnable, state, expired);
  }

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

    try {
      getStateStore().load();
    }
    catch (StateStorage.StateStorageException e) {
      throw new IOException(e.getMessage());
    }

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
      object.beforeApplicationLoaded(this);
    }
  }


  public void dispose() {
    fireApplicationExiting();
    disposeComponents();

    ourThreadExecutorsService.shutdownNow();
    super.dispose();
  }

  private final Object lock = new Object();
  private void makeChangesVisibleToEDT() {
    synchronized (lock) {
      lock.hashCode();
    }
  }

  public boolean runProcessWithProgressSynchronously(final Runnable process, String progressTitle, boolean canBeCanceled, Project project) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project, null);
  }

  public boolean runProcessWithProgressSynchronously(final Runnable process, final String progressTitle, final boolean canBeCanceled, @Nullable final Project project,
                                                     final JComponent parentComponent) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project, parentComponent, null);
  }

  public boolean runProcessWithProgressSynchronously(final Runnable process, final String progressTitle, final boolean canBeCanceled, @Nullable final Project project,
                                                       final JComponent parentComponent, final String cancelText) {
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
        public void run() {
          if (myExceptionalThreadWithReadAccessRunnable != process) {
              LOG.error("myExceptionalThreadWithReadAccessRunnable != process, process = " + myExceptionalThreadWithReadAccessRunnable);
          }

          executeOnPooledThread(new Runnable() {
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

  public boolean isInModalProgressThread() {
    if (myExceptionalThreadWithReadAccessRunnable == null || !isExceptionalThreadWithReadAccess()) {
      return false;
    }
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    return progressIndicator.isModal() && ((ProgressIndicatorEx)progressIndicator).isModalityEntered();
  }

  public <T> List<Future<T>> invokeAllUnderReadAction(@NotNull Collection<Callable<T>> tasks, final ExecutorService executorService) throws Throwable {
    final List<Callable<T>> newCallables = new ArrayList<Callable<T>>(tasks.size());
    for (final Callable<T> task : tasks) {
      Callable<T> newCallable = new Callable<T>() {
        public T call() throws Exception {
          boolean old = setExceptionalThreadWithReadAccessFlag(true);
          try {
            LOG.assertTrue(isReadAccessAllowed());
            return task.call();
          }
          finally {
            setExceptionalThreadWithReadAccessFlag(old);
          }
        }
      };
      newCallables.add(newCallable);
    }
    final Ref<Throwable> exception = new Ref<Throwable>();
    List<Future<T>> result = runReadAction(new Computable<List<Future<T>>>() {
      public List<Future<T>> compute() {
        try {
          return ConcurrencyUtil.invokeAll(newCallables, executorService);
        }
        catch (Throwable throwable) {
          exception.set(throwable);
          return null;
        }
      }
    });
    Throwable throwable = exception.get();
    if (throwable != null) throw throwable;
    return result;
  }

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

    if (myActionsLock.isReadLockAcquired()) {
      LOG.error("Calling invokeAndWait from read-action leads to possible deadlock.");
    }

    LaterInvocator.invokeAndWait(runnable, modalityState);
  }

  public ModalityState getCurrentModalityState() {
    Object[] entities = LaterInvocator.getCurrentModalEntities();
    return entities.length > 0 ? new ModalityStateEx(entities) : getNoneModalityState();
  }

  public ModalityState getModalityStateForComponent(@NotNull Component c) {
    Window window = c instanceof Window ? (Window)c : SwingUtilities.windowForComponent(c);
    if (window == null) return getNoneModalityState(); //?
    return LaterInvocator.modalityStateForWindow(window);
  }

  public ModalityState getDefaultModalityState() {
    if (EventQueue.isDispatchThread()) {
      return getCurrentModalityState();
    }
    else {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      return progress == null ? getNoneModalityState() : progress.getModalityState();
    }
  }

  public ModalityState getNoneModalityState() {
    return MODALITY_STATE_NONE;
  }

  public long getStartTime() {
    return myStartTime;
  }

  public long getIdleTime() {
    return IdeEventQueue.getInstance().getIdleTime();
  }

  public void exit() {
    exit(false);
  }

  public void exit(final boolean force) {
    if (!force && getDefaultModalityState() != ModalityState.NON_MODAL) {
      return;
    }

    Runnable runnable = new Runnable() {
      public void run() {
        if (!force) {
          if (!showConfirmation()) {
            saveAll();
            return;
          }
        }

        FileDocumentManager.getInstance().saveAllDocuments();

        saveSettings();

        if (!canExit()) return;

        if (disposeSelf()) System.exit(0);
      }
    };
    
    if (!isDispatchThread()) {
      invokeLater(runnable, ModalityState.NON_MODAL);
    }
    else {
      runnable.run();
    }
  }

  private static boolean showConfirmation() {
    final boolean hasUnsafeBgTasks = ProgressManager.getInstance().hasUnsafeProgressIndicator();
    final ConfirmExitDialog confirmExitDialog = new ConfirmExitDialog(hasUnsafeBgTasks);
    if (confirmExitDialog.isToBeShown()) {
      confirmExitDialog.show();
      if (!confirmExitDialog.isOK()) {
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

  public void runReadAction(@NotNull final Runnable action) {
    /** if we are inside read action, do not try to acquire read lock again since it will deadlock if there is a pending writeAction
     * see {@link com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock#allowReader()} */
    boolean mustAcquire = !isReadAccessAllowed();

    if (mustAcquire) {
      LOG.assertTrue(myTestModeFlag || !Thread.holdsLock(PsiLock.LOCK), "Thread must not hold PsiLock while performing readAction");
      try {
        myActionsLock.readLock().acquire();
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
        myActionsLock.readLock().release();
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

  public <T> T runReadAction(@NotNull final Computable<T> computation) {
    final Ref<T> ref = Ref.create(null);
    runReadAction(new Runnable() {
      public void run() {
        ref.set(computation.compute());
      }
    });
    return ref.get();
  }

  public void runWriteAction(@NotNull final Runnable action) {
    assertCanRunWriteAction();

    ActivityTracker.getInstance().inc();
    fireBeforeWriteActionStart(action);

    LOG.assertTrue(myActionsLock.isWriteLockAcquired(Thread.currentThread()) || !Thread.holdsLock(PsiLock.LOCK), "Thread must not hold PsiLock while performing writeAction");
    try {
      myActionsLock.writeLock().acquire();
    }
    catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }

    try {
      synchronized (myWriteActionsStack) {
        myWriteActionsStack.push(action);
      }

      fireWriteActionStarted(action);

      action.run();
    }
    finally {
      try {
        fireWriteActionFinished(action);
        
        synchronized (myWriteActionsStack) {
          myWriteActionsStack.pop();
        }
      }
      finally {
        myActionsLock.writeLock().release();
      }
    }
  }

  public <T> T runWriteAction(@NotNull final Computable<T> computation) {
    final Ref<T> ref = Ref.create(null);
    runWriteAction(new Runnable() {
      public void run() {
        ref.set(computation.compute());
      }
    });
    return ref.get();
  }

  public <T>  T getCurrentWriteAction(@NotNull Class<T> actionClass) {
    synchronized (myWriteActionsStack) {
      for (int i = myWriteActionsStack.size() - 1; i >= 0; i--) {
        Runnable action = myWriteActionsStack.get(i);
        if (actionClass == null || ReflectionCache.isAssignable(actionClass, action.getClass())) return (T)action;
      }
    }
    return null;
  }

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

  public boolean isReadAccessAllowed() {
    Thread currentThread = Thread.currentThread();
    return ourDispatchThread == currentThread ||
           isExceptionalThreadWithReadAccess() ||
           myActionsLock.isReadLockAcquired() ||
           myActionsLock.isWriteLockAcquired() ||
           isDispatchThread();
  }

  public void assertReadAccessToDocumentsAllowed() {
    /* TODO
    Thread currentThread = Thread.currentThread();
    if (ourDispatchThread != currentThread) {
      if (myExceptionalThreadWithReadAccess == currentThread) return;
      if (myActionsLock.isReadLockAcquired(currentThread)) return;
      if (myActionsLock.isWriteLockAcquired(currentThread)) return;
      if (isDispatchThread(currentThread)) return;
      LOG.error(
        "Read access is allowed from event dispatch thread or inside read-action only (see com.intellij.openapi.application.Application.runReadAction())");
    }
    */
  }

  private void assertCanRunWriteAction() {
    assertIsDispatchThread("Write access is allowed from event dispatch thread only");

  }

  public void assertIsDispatchThread() {
    assertIsDispatchThread("Access is allowed from event dispatch thread only.");
  }

  private void assertIsDispatchThread(String message) {
    if (myHeadlessMode || ShutDownTracker.isShutdownHookRunning()) return;
    final Thread currentThread = Thread.currentThread();
    if (ourDispatchThread == currentThread) return;

    if (EventQueue.isDispatchThread()) {
      ourDispatchThread = currentThread;
    }
    if (ourDispatchThread == currentThread) return;

    Integer safeCounter = ourEdtSafe.get();
    if (safeCounter != null && safeCounter > 0) return;

    LOG.error(message,
              "Current thread: " + describe(Thread.currentThread()),
              "Our dispatch thread:" + describe(ourDispatchThread),
              "SystemEventQueueThread: " + describe(getEventQueueThread()));
  }

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

  public void assertTimeConsuming() {
    if (myTestModeFlag || myHeadlessMode || ShutDownTracker.isShutdownHookRunning()) return;
    LOG.assertTrue(!isDispatchThread(), "This operation is time consuming and must not be called on EDT");
  }

  public boolean tryRunReadAction(@NotNull Runnable action) {
    /** if we are inside read action, do not try to acquire read lock again since it will deadlock if there is a pending writeAction
     * see {@link com.intellij.util.concurrency.ReentrantWriterPreferenceReadWriteLock#allowReader()} */
    boolean mustAcquire = !isReadAccessAllowed();

    if (mustAcquire) {
      LOG.assertTrue(myTestModeFlag || !Thread.holdsLock(PsiLock.LOCK), "Thread must not hold PsiLock while performing readAction");
      try {
        if (!myActionsLock.readLock().attempt(0)) return false;
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
        myActionsLock.readLock().release();
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
        System.setProperty("idea.active", Boolean.valueOf(myActive).toString());
        if (active) {
          myDispatcher.getMulticaster().applicationActivated(ideFrame);
        }
        else {
          myDispatcher.getMulticaster().applicationDeactivated(ideFrame);
        }
        return true;
      }
    }

    return false;
  }

  public boolean isActive() {
    if (isUnitTestMode()) return true;

    if (myActive == null) {
      Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      return active != null;
    }

    return myActive;
  }

  public void assertWriteAccessAllowed() {
    LOG.assertTrue(isWriteAccessAllowed(),
                   "Write access is allowed inside write-action only (see com.intellij.openapi.application.Application.runWriteAction())");
  }

  public boolean isWriteAccessAllowed() {
    return myActionsLock.isWriteLockAcquired(Thread.currentThread());
  }

  public void editorPaintStart() {
    myInEditorPaintCounter++;
  }

  public void editorPaintFinish() {
    myInEditorPaintCounter--;
    LOG.assertTrue(myInEditorPaintCounter >= 0);
  }

  public void addApplicationListener(@NotNull ApplicationListener l) {
    myDispatcher.addListener(l);
  }

  public void addApplicationListener(@NotNull ApplicationListener l, @NotNull Disposable parent) {
    myDispatcher.addListener(l, parent);
  }

  public void removeApplicationListener(@NotNull ApplicationListener l) {
    myDispatcher.removeListener(l);
  }

  private void fireApplicationExiting() {
    myDispatcher.getMulticaster().applicationExiting();
  }

  private void fireBeforeWriteActionStart(Runnable action) {
    myDispatcher.getMulticaster().beforeWriteActionStart(action);
  }

  private void fireWriteActionStarted(Runnable action) {
    myDispatcher.getMulticaster().writeActionStarted(action);
  }

  private void fireWriteActionFinished(Runnable action) {
    myDispatcher.getMulticaster().writeActionFinished(action);
  }

  public void _saveSettings() { // public for testing purposes
    if (mySaveSettingsIsInProgress.compareAndSet(false, true)) {
      try {
        doSave();
      }
      catch (final Throwable ex) {
        if (isUnitTestMode()) {
          System.out.println("Saving application settings failed");
          ex.printStackTrace();
        }
        else {
          LOG.info("Saving application settings failed", ex);
          invokeLater(new Runnable() {
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

  public void saveSettings() {
    if (!myDoNotSave && !isUnitTestMode() && !isHeadlessEnvironment()) {
      _saveSettings();
    }
  }

  public void saveAll() {
    if (myDoNotSave || isUnitTestMode() || isHeadlessEnvironment()) return;

    FileDocumentManager.getInstance().saveAllDocuments();

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project openProject : openProjects) {
      ProjectEx project = (ProjectEx)openProject;
      project.save();
    }

    saveSettings();
  }

  public void doNotSave() {
    myDoNotSave = true;
  }


  public boolean isDoNotSave() {
    return myDoNotSave;
  }

  public <T> T[] getExtensions(final ExtensionPointName<T> extensionPointName) {
    return Extensions.getRootArea().getExtensionPoint(extensionPointName).getExtensions();
  }

  public boolean isDisposeInProgress() {
    return myDisposeInProgress;
  }

  public boolean isRestartCapable() {
    return SystemInfo.isWindows || SystemInfo.isMacOSSnowLeopard;
  }

  public void restart() {
    if (SystemInfo.isWindows) {
      Win32Restarter.restart();
    }
    else if (SystemInfo.isMacOSSnowLeopard) {
      MacRestarter.restart();
    }
    else {
      exit();
    }
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
}
