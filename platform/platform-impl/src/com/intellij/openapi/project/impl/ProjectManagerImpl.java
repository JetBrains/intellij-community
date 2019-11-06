// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConversionResult;
import com.intellij.conversion.ConversionService;
import com.intellij.diagnostic.*;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.impl.DummyProject;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.*;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ZipHandler;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.UnsafeWeakList;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ProjectManagerImpl extends ProjectManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectManagerImpl.class);

  private static final Key<List<ProjectManagerListener>> LISTENERS_IN_PROJECT_KEY = Key.create("LISTENERS_IN_PROJECT_KEY");

  private static final ExtensionPointName<ProjectCloseHandler> CLOSE_HANDLER_EP = new ExtensionPointName<>("com.intellij.projectCloseHandler");

  private @NotNull Project[] myOpenProjects = {}; // guarded by lock
  private final Map<String, Project> myOpenProjectByHash = ContainerUtil.newConcurrentMap();
  private final Object lock = new Object();

  // we cannot use the same approach to migrate to message bus as CompilerManagerImpl because of method canCloseProject
  private final List<ProjectManagerListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final DefaultProject myDefaultProject = new DefaultProject();
  private final ExcludeRootsCache myExcludeRootsCache;

  @NotNull
  private static List<ProjectManagerListener> getListeners(@NotNull Project project) {
    List<ProjectManagerListener> array = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (array == null) return Collections.emptyList();
    return array;
  }

  public ProjectManagerImpl() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        for (ProjectManagerListener listener : getAllListeners(project)) {
          try {
            listener.projectOpened(project);
          }
          catch (Exception e) {
            handleListenerError(e, listener);
          }
        }
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        for (ProjectManagerListener listener : getAllListeners(project)) {
          try {
            listener.projectClosed(project);
          }
          catch (Exception e) {
            handleListenerError(e, listener);
          }
        }
      }

      @Override
      public void projectClosing(@NotNull Project project) {
        for (ProjectManagerListener listener : getAllListeners(project)) {
          try {
            listener.projectClosing(project);
          }
          catch (Exception e) {
            handleListenerError(e, listener);
          }
        }
      }

      @Override
      public void projectClosingBeforeSave(@NotNull Project project) {
        for (ProjectManagerListener listener : getAllListeners(project)) {
          try {
            listener.projectClosingBeforeSave(project);
          }
          catch (Exception e) {
            handleListenerError(e, listener);
          }
        }
      }
    });
    myExcludeRootsCache = new ExcludeRootsCache(connection);
  }

  @NotNull
  private static ProjectManagerListener getPublisher() {
    return ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC);
  }

  private static void handleListenerError(@NotNull Throwable e, @NotNull ProjectManagerListener listener) {
    if (e instanceof ProcessCanceledException) {
      throw (ProcessCanceledException)e;
    }
    else {
      LOG.error("From the listener " + listener + " (" + listener.getClass() + ")", e);
    }
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    // dispose manually, because TimedReference.dispose() can already be called (in Timed.disposeTimed()) and then default project resurrected
    Disposer.dispose(myDefaultProject);
  }

  @SuppressWarnings("StaticNonFinalField") public static int TEST_PROJECTS_CREATED;
  private static final boolean LOG_PROJECT_LEAKAGE_IN_TESTS = Boolean.parseBoolean(System.getProperty("idea.log.leaked.projects.in.tests", "true"));
  private static final int MAX_LEAKY_PROJECTS = 5;
  private static final long LEAK_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(30);
  private static long CHECK_START = System.currentTimeMillis();
  private final Map<Project, String> myProjects = new WeakHashMap<>();

  @Override
  @Nullable
  public Project newProject(@Nullable String projectName, @NotNull String filePath, boolean useDefaultProjectSettings, boolean isDummy) {
    OpenProjectTask options = new OpenProjectTask();
    options.useDefaultProjectAsTemplate = useDefaultProjectSettings;
    options.isNewProject = true;
    return newProject(Paths.get(toCanonicalName(filePath)), projectName, options);
  }

  @Override
  @Nullable
  public Project newProject(@NotNull Path projectFile, @Nullable String projectName, @NotNull OpenProjectTask options) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      TEST_PROJECTS_CREATED++;
      //noinspection TestOnlyProblems
      checkProjectLeaksInTests();
    }

    if (Files.isRegularFile(projectFile)) {
      try {
        FileUtil.delete(projectFile);
      }
      catch (IOException ignored) {
      }
    }
    else {
      try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(projectFile.resolve(Project.DIRECTORY_STORE_FOLDER))) {
        for (Path file : directoryStream) {
          FileUtil.delete(file);
        }
      }
      catch (IOException ignore) {
      }
    }

    ProjectImpl project = doCreateProject(projectName, projectFile);
    try {
      Project template = options.useDefaultProjectAsTemplate ? getDefaultProject() : null;
      initProject(projectFile, project, options.isRefreshVfsNeeded, template, ProgressManager.getInstance().getProgressIndicator());
      if (LOG_PROJECT_LEAKAGE_IN_TESTS) {
        myProjects.put(project, null);
      }
      return project;
    }
    catch (Throwable t) {
      LOG.warn(t);
      try {
        Messages.showErrorDialog(message(t), ProjectBundle.message("project.load.default.error"));
      }
      catch (NoClassDefFoundError e) {
        // error icon not loaded
        LOG.info(e);
      }
      return null;
    }
  }

  @NonNls
  @NotNull
  private static String message(@NotNull Throwable e) {
    String message = e.getMessage();
    if (message != null) return message;
    message = e.getLocalizedMessage();
    if (message != null) return message;
    message = e.toString();
    Throwable cause = e.getCause();
    if (cause != null) {
      String causeMessage = message(cause);
      return message + " (cause: " + causeMessage + ")";
    }

    return message;
  }

  @TestOnly
  private void checkProjectLeaksInTests() {
    if (!LOG_PROJECT_LEAKAGE_IN_TESTS || getLeakedProjectsCount() < MAX_LEAKY_PROJECTS) {
      return;
    }

    long currentTime = System.currentTimeMillis();
    if (currentTime - CHECK_START < LEAK_CHECK_INTERVAL) {
      return; // check every N minutes
    }

    for (int i = 0; i < 3 && getLeakedProjectsCount() >= MAX_LEAKY_PROJECTS; i++) {
      GCUtil.tryGcSoftlyReachableObjects();
    }

    //noinspection AssignmentToStaticFieldFromInstanceMethod
    CHECK_START = currentTime;

    if (getLeakedProjectsCount() >= MAX_LEAKY_PROJECTS) {
      //noinspection CallToSystemGC
      System.gc();
      Collection<Project> copy = getLeakedProjects();
      myProjects.clear();
      if (ContainerUtil.collect(copy.iterator()).size() >= MAX_LEAKY_PROJECTS) {
        throw new TooManyProjectLeakedException(copy);
      }
    }
  }

  @TestOnly
  private Collection<Project> getLeakedProjects() {
    myProjects.remove(DummyProject.getInstance()); // process queue
    return myProjects.keySet().stream()
      .filter(p -> p.isDisposed() && !((ProjectImpl)p).isTemporarilyDisposed())
      .collect(Collectors.toCollection(UnsafeWeakList::new));
  }

  @TestOnly
  private int getLeakedProjectsCount() {
    myProjects.remove(DummyProject.getInstance()); // process queue
    return (int)myProjects.keySet().stream().filter(project -> project.isDisposed() && !((ProjectImpl)project).isTemporarilyDisposed()).count();
  }

  private static void initProject(@NotNull Path file,
                                  @NotNull ProjectImpl project,
                                  boolean isRefreshVfsNeeded,
                                  @Nullable Project template,
                                  @Nullable ProgressIndicator indicator) {
    LOG.assertTrue(!project.isDefault());
    if (indicator != null) {
      indicator.setIndeterminate(false);
      // getting project name is not cheap and not possible at this moment
      indicator.setText("Loading components...");
    }

    Activity activity = StartUpMeasurer.startMainActivity("project before loaded callbacks");
    ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).beforeProjectLoaded(project);
    activity.end();

    boolean succeed = false;
    try {
      project.registerComponents();
      project.getStateStore().setPath(file, isRefreshVfsNeeded, template);
      project.init(indicator);
      succeed = true;
    }
    finally {
      if (!succeed) {
        TransactionGuard.submitTransaction(project, () -> WriteAction.run(() -> Disposer.dispose(project)));
      }
    }
  }

  @NotNull
  private static ProjectImpl doCreateProject(@Nullable String projectName, @NotNull Path filePath) {
    Activity activity = StartUpMeasurer.startMainActivity("project instantiation");
    ProjectImpl project = new ProjectImpl(filePath, projectName);
    activity.end();
    return project;
  }

  @Override
  @NotNull
  public Project loadProject(@NotNull Path file, @Nullable String projectName) {
    ProjectImpl project = doCreateProject(projectName, file);
    initProject(file, project, /* isRefreshVfsNeeded = */ true, null, ProgressManager.getInstance().getProgressIndicator());
    return project;
  }

  @NotNull
  private static String toCanonicalName(@NotNull String filePath) {
    try {
      return FileUtil.resolveShortWindowsName(filePath);
    }
    catch (IOException e) {
      // OK. File does not yet exist, so its canonical path will be equal to its original path.
    }

    return filePath;
  }

  @Override
  public boolean isDefaultProjectInitialized() {
    return myDefaultProject.isCached();
  }

  @Override
  @NotNull
  public Project getDefaultProject() {
    LOG.assertTrue(!ApplicationManager.getApplication().isDisposed(), "Default project has been already disposed!");
    // call instance method to reset timeout
    LOG.assertTrue(!myDefaultProject.getMessageBus().isDisposed());
    LOG.assertTrue(myDefaultProject.isCached());
    return myDefaultProject;
  }

  @Override
  @NotNull
  public Project[] getOpenProjects() {
    synchronized (lock) {
      return myOpenProjects;
    }
  }

  @Override
  public boolean isProjectOpened(Project project) {
    synchronized (lock) {
      return ArrayUtil.contains(project, myOpenProjects);
    }
  }

  @Override
  public boolean openProject(@NotNull final Project project) {
    //noinspection TestOnlyProblems
    if (isLight(project)) {
      //noinspection TestOnlyProblems
      ((ProjectImpl)project).setTemporarilyDisposed(false);
      boolean isInitialized = StartupManagerEx.getInstanceEx(project).startupActivityPassed();
      if (isInitialized) {
        addToOpened(project);
        // events already fired
        return true;
      }
    }

    for (Project p : getOpenProjects()) {
      if (ProjectUtil.isSameProject(project.getProjectFilePath(), p)) {
        GuiUtils.invokeLaterIfNeeded(() -> ProjectUtil.focusProjectWindow(p, false), ModalityState.NON_MODAL);
        return false;
      }
    }

    if (!addToOpened(project)) {
      return false;
    }

    if (ApplicationManager.getApplication().isDispatchThread()) {
      AtomicBoolean success = new AtomicBoolean(true);
      TransactionGuard.getInstance().submitTransactionAndWait(() -> {
        success.set(loadProjectUnderProgress(project));
      });
      if (success.get()) {
        return true;
      }
    }
    else {
      assertInTransaction();
      if (loadProjectUnderProgress(project)) {
        return true;
      }
    }

    GuiUtils.invokeLaterIfNeeded(() -> {
      closeProject(project, false, false, false, true);
      WriteAction.run(() -> Disposer.dispose(project));
      notifyProjectOpenFailed();
    }, ModalityState.defaultModalityState());
    return false;
  }

  private static boolean loadProjectUnderProgress(@NotNull Project project) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null || ApplicationManager.getApplication().isDispatchThread()) {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        doLoadProject(project);
      }, ProjectBundle.message("project.load.progress"), canCancelProjectLoading(), project);
    }
    else {
      indicator.setText("Preparing workspace...");
      indicator.setIndeterminate(true);
      try {
        doLoadProject(project);
        return true;
      }
      catch (ProcessCanceledException e) {
        return false;
      }
    }
  }

  private static void doLoadProject(@NotNull Project project) {
    TransactionGuard.getInstance().submitTransactionAndWait(() -> fireProjectOpened(project));

    StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(project);
    startupManager.runStartupActivities();
    startupManager.runPostStartupActivitiesFromExtensions();

    GuiUtils.invokeLaterIfNeeded(() -> {
      if (project.isDisposed()) {
        return;
      }

      startupManager.runPostStartupActivities();

      Application application = ApplicationManager.getApplication();
      if (!(application.isHeadlessEnvironment() || application.isUnitTestMode())) {
        StorageUtilKt.checkUnknownMacros(project, true);
      }
      StartUpMeasurer.stopPluginCostMeasurement();
    }, ModalityState.NON_MODAL, project.getDisposed());
    ApplicationManager.getApplication().invokeLater(() -> {
      LoadingState phase = DumbService.isDumb(project)
      ? LoadingState.PROJECT_OPENED
      : LoadingState.INDEXING_FINISHED;
      StartUpMeasurer.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, phase);

      if (!project.isDisposed()) {
        startupManager.scheduleBackgroundPostStartupActivities();
      }
    }, ModalityState.NON_MODAL, project.getDisposed());
  }

  private static void assertInTransaction() {
    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        ApplicationManager.getApplication().isInternal() &&
        TransactionGuard.getInstance().getContextTransaction() == null) {
      LOG.error("Project opening should be done in a transaction",
                new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString()));
    }
  }

  private boolean addToOpened(@NotNull Project project) {
    assert !project.isDisposed() : "Must not open already disposed project";
    synchronized (lock) {
      if (isProjectOpened(project)) {
        return false;
      }
      myOpenProjects = ArrayUtil.append(myOpenProjects, project);
    }
    updateTheOnlyProjectField();
    myOpenProjectByHash.put(project.getLocationHash(), project);
    return true;
  }

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "deprecation"})
  void updateTheOnlyProjectField() {
    boolean isDefaultInitialized = isDefaultProjectInitialized();
    synchronized (lock) {
      ProjectCoreUtil.theProject = myOpenProjects.length == 1 && !isDefaultInitialized ? myOpenProjects[0] : null;
    }
  }

  private void removeFromOpened(@NotNull Project project) {
    synchronized (lock) {
      myOpenProjects = ArrayUtil.remove(myOpenProjects, project);
      myOpenProjectByHash.values().remove(project); // remove by value and not by key!
    }
  }

  @Override
  @Nullable
  public Project findOpenProjectByHash(@Nullable String locationHash) {
    return myOpenProjectByHash.get(locationHash);
  }

  private static boolean canCancelProjectLoading() {
    return !ProgressManager.getInstance().isInNonCancelableSection();
  }

  @Override
  public Project loadAndOpenProject(@NotNull String originalFilePath) {
    return loadAndOpenProject(Paths.get(FileUtilRt.toSystemIndependentName(toCanonicalName(originalFilePath))));
  }

  @Override
  @Nullable
  public Project loadAndOpenProject(@NotNull Path file) {
    ConversionResult conversionResult;
    try {
      conversionResult = ConversionService.getInstance().convert(file);
    }
    catch (CannotConvertException e) {
      conversionResult = null;
      LOG.info(e);
      showCannotConvertMessage(e, null);
    }

    ProjectImpl project;
    if (conversionResult == null || conversionResult.openingIsCanceled()) {
      project = null;
    }
    else {
      project = doCreateProject(null, file);
      //noinspection CodeBlock2Expr
      ConversionResult finalConversionResult = conversionResult;
      TransactionGuard.getInstance().submitTransactionAndWait(() -> {
        ProgressManager.getInstance().run(new Task.Modal(project, ProjectBundle.message("project.load.progress"), true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            try {
              initProject(file, project, /* isRefreshVfsNeeded = */ true, null, indicator);
            }
            catch (ProcessCanceledException e) {
              return;
            }
            catch (Throwable e) {
              LOG.error(e);
              return;
            }

            if (!finalConversionResult.conversionNotNeeded()) {
              StartupManager.getInstance(project).registerPostStartupActivity(() -> finalConversionResult.postStartupActivity(project));
            }
            openProject(project);
          }
        });
      });
    }

    if (project == null) {
      WelcomeFrame.showIfNoProjectOpened();
      return null;
    }

    if (!project.isOpen()) {
      WelcomeFrame.showIfNoProjectOpened();
      ApplicationManager.getApplication().runWriteAction(() -> {
        if (!project.isDisposed()) {
          Disposer.dispose(project);
        }
      });
    }
    return project;
  }

  public static void showCannotConvertMessage(@NotNull CannotConvertException e, @Nullable Component component) {
    AppUIUtil.invokeOnEdt(() -> {
      Messages.showErrorDialog(component, IdeBundle.message("error.cannot.convert.project", e.getMessage()),
                               IdeBundle.message("title.cannot.convert.project"));
    });
  }

  /**
   * Converts and loads the project at the specified path.
   *
   * @param path the path to open the project.
   * @return the project, or null if the user has cancelled opening the project.
   */
  @Nullable
  @ApiStatus.Internal
  public static Project convertAndLoadProject(@NotNull Path path) throws CannotConvertException {
    Activity activity = StartUpMeasurer.startMainActivity("project conversion");
    ConversionResult conversionResult = ConversionService.getInstance().convert(path);
    activity.end();
    if (conversionResult.openingIsCanceled()) {
      return null;
    }

    ProjectImpl project = doCreateProject(null, path);
    try {
      ProgressManager progressManager = ProgressManager.getInstance();
      if (!ApplicationManager.getApplication().isDispatchThread() && progressManager.getProgressIndicator() != null) {
        initProject(path, project, /* isRefreshVfsNeeded = */ true, null, progressManager.getProgressIndicator());
      }
      else {
        //noinspection CodeBlock2Expr
        progressManager.runProcessWithProgressSynchronously(() -> {
          initProject(path, project, /* isRefreshVfsNeeded = */ true, null, progressManager.getProgressIndicator());
        }, ProjectBundle.message("project.load.progress"), canCancelProjectLoading(), project);
      }
    }
    catch (ProcessCanceledException e) {
      return null;
    }

    if (!conversionResult.conversionNotNeeded()) {
      StartupManager.getInstance(project).registerPostStartupActivity(() -> conversionResult.postStartupActivity(project));
    }
    return project;
  }

  private static void notifyProjectOpenFailed() {
    Application application = ApplicationManager.getApplication();
    application.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed();
    if (application.isUnitTestMode()) return;
    WelcomeFrame.showIfNoProjectOpened();
  }

  @Override
  @TestOnly
  public void openTestProject(@NotNull final Project project) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    openProject(project);
    UIUtil.dispatchAllInvocationEvents(); // post init activities are invokeLatered
  }

  @NotNull
  @Override
  @TestOnly
  public Collection<Project> closeTestProject(@NotNull final Project project) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    forceCloseProject(project, false);
    Project[] projects = getOpenProjects();
    return projects.length == 0 ? Collections.emptyList() : Arrays.asList(projects);
  }

  @Override
  public void reloadProject(@NotNull Project project) {
    StoreReloadManager.getInstance().reloadProject(project);
  }

  @Override
  public boolean closeProject(@NotNull final Project project) {
    return closeProject(project, true, true, false, true);
  }

  @Override
  @TestOnly
  public boolean forceCloseProject(@NotNull Project project, boolean dispose) {
    return closeProject(project, false /* do not save project */, false /* do not save app */, dispose, false);
  }

  // return true if successful
  @Override
  public boolean closeAndDisposeAllProjects(boolean checkCanClose) {
    for (Project project : getOpenProjects()) {
      if (!closeProject(project, true, false, true, checkCanClose)) {
        return false;
      }
    }
    return true;
  }

  // isSaveApp is ignored if saveProject is false
  @SuppressWarnings("TestOnlyProblems")
  private boolean closeProject(@NotNull final Project project,
                               final boolean isSaveProject,
                               final boolean isSaveApp,
                               final boolean dispose,
                               boolean checkCanClose) {
    Application app = ApplicationManager.getApplication();
    if (app.isWriteAccessAllowed()) {
      throw new IllegalStateException(
        "Must not call closeProject() from under write action because fireProjectClosing() listeners must have a chance to do something useful");
    }
    app.assertIsDispatchThread();

    if (isLight(project)) {
      // if we close project at the end of the test, just mark it closed;
      // if we are shutting down the entire test framework, proceed to full dispose
      ProjectImpl projectImpl = (ProjectImpl)project;
      if (!projectImpl.isTemporarilyDisposed()) {
        projectImpl.setTemporarilyDisposed(true);
        removeFromOpened(project);
        ((ProjectManagerImpl)ProjectManager.getInstance()).updateTheOnlyProjectField();
        return true;
      }
      projectImpl.setTemporarilyDisposed(false);
    }
    else if (!isProjectOpened(project)) {
      return true;
    }

    if (checkCanClose && !canClose(project)) {
      return false;
    }

    final ShutDownTracker shutDownTracker = ShutDownTracker.getInstance();
    shutDownTracker.registerStopperThread(Thread.currentThread());
    try {
      if (project instanceof ProjectImpl) {
        ((ProjectImpl)project).stopServicePreloading();
      }

      getPublisher().projectClosingBeforeSave(project);

      if (isSaveProject) {
        FileDocumentManager.getInstance().saveAllDocuments();
        SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(project, isSaveApp);
      }

      if (checkCanClose && !ensureCouldCloseIfUnableToSave(project)) {
        return false;
      }

      // somebody can start progress here, do not wrap in write action
      fireProjectClosing(project);

      app.runWriteAction(() -> {
        if (dispose && project instanceof ProjectImpl) {
          ((ProjectImpl)project).setDisposeInProgress();
        }

        removeFromOpened(project);

        fireProjectClosed(project);

        ZipHandler.clearFileAccessorCache();
        LaterInvocator.purgeExpiredItems();

        if (dispose) {
          Disposer.dispose(project);
        }
      });
    }
    finally {
      shutDownTracker.unregisterStopperThread(Thread.currentThread());
    }

    return true;
  }

  @TestOnly
  public static boolean isLight(@NotNull Project project) {
    return project instanceof ProjectImpl && ((ProjectImpl)project).isLight();
  }

  @Override
  public boolean closeAndDispose(@NotNull Project project) {
    return closeProject(project, true /* save project */, false /* don't save app */, true /* dispose project */, true /* checkCanClose */);
  }

  private static void fireProjectClosing(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: fireProjectClosing()");
    }

    getPublisher().projectClosing(project);
  }

  @Override
  public void addProjectManagerListener(@NotNull ProjectManagerListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addProjectManagerListener(@NotNull VetoableProjectManagerListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addProjectManagerListener(@NotNull final ProjectManagerListener listener, @NotNull Disposable parentDisposable) {
    addProjectManagerListener(listener);
    Disposer.register(parentDisposable, () -> removeProjectManagerListener(listener));
  }

  @Override
  public void removeProjectManagerListener(@NotNull ProjectManagerListener listener) {
    boolean removed = myListeners.remove(listener);
    LOG.assertTrue(removed);
  }

  @Override
  public void removeProjectManagerListener(@NotNull VetoableProjectManagerListener listener) {
    boolean removed = myListeners.remove(listener);
    LOG.assertTrue(removed);
  }

  @Override
  public void addProjectManagerListener(@NotNull Project project, @NotNull ProjectManagerListener listener) {
    if (project.isDefault()) return; // nothing happens with default project
    List<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (listeners == null) {
      listeners = ((UserDataHolderEx)project)
        .putUserDataIfAbsent(LISTENERS_IN_PROJECT_KEY, ContainerUtil.createLockFreeCopyOnWriteList());
    }
    listeners.add(listener);
  }

  @Override
  public void removeProjectManagerListener(@NotNull Project project, @NotNull ProjectManagerListener listener) {
    if (project.isDefault()) return;  // nothing happens with default project
    List<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    LOG.assertTrue(listeners != null);
    boolean removed = listeners.remove(listener);
    LOG.assertTrue(removed);
  }

  private static void fireProjectOpened(@NotNull Project project) {
    LOG.debug("projectOpened");

    LifecycleUsageTriggerCollector.onProjectOpened(project);
    Activity activity = StartUpMeasurer.startMainActivity("project opened callbacks");
    getPublisher().projectOpened(project);
    // https://jetbrains.slack.com/archives/C5E8K7FL4/p1495015043685628
    // projectOpened in the project components is called _after_ message bus event projectOpened for ages
    // old behavior is preserved for now (smooth transition, to not break all), but this order is not logical,
    // because ProjectComponent.projectOpened it is part of project initialization contract, but message bus projectOpened it is just an event
    // (and, so, should be called after project initialization)
    for (ProjectComponent component : getProjectComponents(project)) {
      StartupManagerImpl.runActivity(() -> {
        ClassLoader loader = component.getClass().getClassLoader();
        String pluginId = loader instanceof PluginClassLoader ? ((PluginClassLoader)loader).getPluginIdString() : null;
        Activity componentActivity = StartUpMeasurer.startActivity(component.getClass().getName(), ActivityCategory.PROJECT_OPEN_HANDLER, pluginId);
        component.projectOpened();
        componentActivity.end();
      });
    }
    activity.end();

    ProjectImpl.ourClassesAreLoaded = true;
  }

  private static void fireProjectClosed(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("projectClosed");
    }

    LifecycleUsageTriggerCollector.onProjectClosed(project);

    getPublisher().projectClosed(project);
    // see "why is called after message bus" in the fireProjectOpened
    List<ProjectComponent> components = getProjectComponents(project);
    for (int i = components.size() - 1; i >= 0; i--) {
      ProjectComponent component = components.get(i);
      try {
        component.projectClosed();
      }
      catch (Throwable e) {
        LOG.error(component.toString(), e);
      }
    }
  }

  @NotNull
  private static List<ProjectComponent> getProjectComponents(@NotNull ComponentManager project) {
    //noinspection deprecation
    return project.getComponentInstancesOfType(ProjectComponent.class);
  }

  @Override
  public boolean canClose(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: canClose()");
    }

    for (ProjectCloseHandler handler : CLOSE_HANDLER_EP.getIterable()) {
      if (handler == null) {
        break;
      }

      try {
        if (!handler.canClose(project)) {
          LOG.debug("close canceled by " + handler);
          return false;
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    for (ProjectManagerListener listener : getAllListeners(project)) {
      try {
        @SuppressWarnings("deprecation")
        boolean canClose = listener instanceof VetoableProjectManagerListener ? ((VetoableProjectManagerListener)listener).canClose(project) : listener.canCloseProject(project);
        if (!canClose) {
          LOG.debug("close canceled by " + listener);
          return false;
        }
      }
      catch (Throwable e) {
        handleListenerError(e, listener);
      }
    }

    return true;
  }

  @NotNull
  private List<ProjectManagerListener> getAllListeners(@NotNull Project project) {
    List<ProjectManagerListener> projectLevelListeners = getListeners(project);
    if (projectLevelListeners.isEmpty()) {
      return myListeners;
    }
    if (myListeners.isEmpty()) {
      return projectLevelListeners;
    }

    List<ProjectManagerListener> result = new ArrayList<>(projectLevelListeners.size() + myListeners.size());
    // order is critically important due to backward compatibility - project level listeners must be first
    result.addAll(projectLevelListeners);
    result.addAll(myListeners);
    return result;
  }

  private static boolean ensureCouldCloseIfUnableToSave(@NotNull Project project) {
    NotificationsManager notificationManager = ApplicationManager.getApplication().getServiceIfCreated(NotificationsManager.class);
    if (notificationManager == null) {
      return true;
    }

    UnableToSaveProjectNotification[] notifications = notificationManager.getNotificationsOfType(UnableToSaveProjectNotification.class, project);
    if (notifications.length == 0) {
      return true;
    }

    StringBuilder message = new StringBuilder();
    message.append(String.format("%s was unable to save some project files,\nare you sure you want to close this project anyway?",
                                 ApplicationNamesInfo.getInstance().getProductName()));

    message.append("\n\nRead-only files:\n");
    int count = 0;
    List<VirtualFile> files = notifications[0].myFiles;
    for (VirtualFile file : files) {
      if (count == 10) {
        message.append('\n').append("and ").append(files.size() - count).append(" more").append('\n');
      }
      else {
        message.append(file.getPath()).append('\n');
        count++;
      }
    }
    return Messages.showYesNoDialog(project, message.toString(), "Unsaved Project", Messages.getWarningIcon()) == Messages.YES;
  }

  public static class UnableToSaveProjectNotification extends Notification {
    private Project myProject;

    private List<VirtualFile> myFiles;

    public void setFiles(@NotNull List<VirtualFile> files) {
      myFiles = files;
    }

    public UnableToSaveProjectNotification(@NotNull Project project, @NotNull List<VirtualFile> readOnlyFiles) {
      super("Project Settings", "Could not save project",
            "Unable to save project files. Please ensure project files are writable and you have permissions to modify them." +
            " <a href=\"\">Try to save project again</a>.", NotificationType.ERROR,
            (notification, event) -> {
              UnableToSaveProjectNotification unableToSaveProjectNotification = (UnableToSaveProjectNotification)notification;
              Project _project = unableToSaveProjectNotification.myProject;
              notification.expire();
              if (_project != null && !_project.isDisposed()) {
                _project.save();
              }
            });

      myProject = project;
      myFiles = readOnlyFiles;
    }

    @Override
    public void expire() {
      myProject = null;
      super.expire();
    }
  }

  @Override
  @NotNull
  public String[] getAllExcludedUrls() {
    return myExcludeRootsCache.getExcludedUrls();
  }
}