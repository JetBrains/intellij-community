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
package com.intellij.openapi.project.impl;

import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.conversion.ConversionResult;
import com.intellij.conversion.ConversionService;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.UnsafeWeakList;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ProjectManagerImpl extends ProjectManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance(ProjectManagerImpl.class);

  private static final Key<List<ProjectManagerListener>> LISTENERS_IN_PROJECT_KEY = Key.create("LISTENERS_IN_PROJECT_KEY");

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private ProjectImpl myDefaultProject; // Only used asynchronously in save and dispose, which itself are synchronized.

  private Project[] myOpenProjects = {}; // guarded by lock
  private final Map<String, Project> myOpenProjectByHash = ContainerUtil.newConcurrentMap();
  private final Object lock = new Object();

  // we cannot use the same approach to migrate to message bus as CompilerManagerImpl because of method canCloseProject
  private final List<ProjectManagerListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final ProgressManager myProgressManager;
  private volatile boolean myDefaultProjectWasDisposed;
  private final ProjectManagerListener myBusPublisher;

  @NotNull
  private static List<ProjectManagerListener> getListeners(@NotNull Project project) {
    List<ProjectManagerListener> array = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (array == null) return Collections.emptyList();
    return array;
  }

  public ProjectManagerImpl(ProgressManager progressManager) {
    myProgressManager = progressManager;
    MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    myBusPublisher = messageBus.syncPublisher(TOPIC);
    messageBus.connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
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
      public void projectClosed(Project project) {
        for (ProjectManagerListener listener : getAllListeners(project)) {
          try {
            listener.projectClosed(project);
          }
          catch (Exception e) {
            handleListenerError(e, listener);
          }
        }
        ZipHandler.clearFileAccessorCache();
        LaterInvocator.purgeExpiredItems();
      }

      @Override
      public void projectClosing(Project project) {
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
  }

  private static void handleListenerError(@NotNull Throwable e, @NotNull ProjectManagerListener listener) {
    if (e instanceof ProcessCanceledException) {
      throw (ProcessCanceledException)e;
    }
    else {
      LOG.error("From listener " + listener + " (" + listener.getClass() + ")", e);
    }
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (myDefaultProject != null) {
      Disposer.dispose(myDefaultProject);

      myDefaultProject = null;
      myDefaultProjectWasDisposed = true;
    }
  }

  @SuppressWarnings("StaticNonFinalField") public static int TEST_PROJECTS_CREATED;
  private static final boolean LOG_PROJECT_LEAKAGE_IN_TESTS = Boolean.parseBoolean(System.getProperty("idea.log.leaked.projects.in.tests", "true"));
  private static final int MAX_LEAKY_PROJECTS = 5;
  private static final long LEAK_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(30);
  private static long CHECK_START = System.currentTimeMillis();
  @SuppressWarnings("FieldCanBeLocal") private final Map<Project, String> myProjects = new WeakHashMap<>();

  @Override
  @Nullable
  public Project newProject(@Nullable String projectName, @NotNull String filePath, boolean useDefaultProjectSettings, boolean isDummy) {
    filePath = toCanonicalName(filePath);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      TEST_PROJECTS_CREATED++;
      //noinspection TestOnlyProblems
      checkProjectLeaksInTests();
    }

    File projectFile = new File(filePath);
    if (projectFile.isFile()) {
      FileUtil.delete(projectFile);
    }
    else {
      File[] files = new File(projectFile, Project.DIRECTORY_STORE_FOLDER).listFiles();
      if (files != null) {
        for (File file : files) {
          FileUtil.delete(file);
        }
      }
    }
    ProjectImpl project = createProject(projectName, filePath, false);
    try {
      initProject(project, useDefaultProjectSettings ? getDefaultProject() : null);
      if (LOG_PROJECT_LEAKAGE_IN_TESTS) {
        myProjects.put(project, null);
      }
      return project;
    }
    catch (Throwable t) {
      LOG.info(t);
      Messages.showErrorDialog(message(t), ProjectBundle.message("project.load.default.error"));
      return null;
    }
  }

  @NonNls
  @NotNull
  private static String message(@NotNull Throwable e) {
    String message = e.getMessage();
    if (message != null) return message;
    message = e.getLocalizedMessage();
    //noinspection ConstantConditions
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
      System.gc();
      List<Project> copy = getLeakedProjects();
      myProjects.clear();
      if (ContainerUtil.collect(copy.iterator()).size() >= MAX_LEAKY_PROJECTS) {
        throw new TooManyProjectLeakedException(copy);
      }
    }
  }

  @TestOnly
  private List<Project> getLeakedProjects() {
    myProjects.remove(getDefaultProject()); // process queue
    return myProjects.keySet().stream().filter(project -> project.isDisposed() && !((ProjectImpl)project).isTemporarilyDisposed()).collect(Collectors.toCollection(UnsafeWeakList::new));
  }
  @TestOnly
  private int getLeakedProjectsCount() {
    myProjects.remove(getDefaultProject()); // process queue
    return (int)myProjects.keySet().stream().filter(project -> project.isDisposed() && !((ProjectImpl)project).isTemporarilyDisposed()).count();
  }

  private void initProject(@NotNull ProjectImpl project, @Nullable Project template) {
    ProgressIndicator indicator = myProgressManager.getProgressIndicator();
    if (indicator != null && !project.isDefault()) {
      indicator.setText(ProjectBundle.message("loading.components.for", project.getName()));
    }

    ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).beforeProjectLoaded(project);

    boolean succeed = false;
    try {
      if (template != null) {
        project.getStateStore().loadProjectFromTemplate(template);
      }
      project.init();
      succeed = true;
    }
    finally {
      if (!succeed && !project.isDefault()) {
        TransactionGuard.submitTransaction(project, () -> WriteAction.run(() -> Disposer.dispose(project)));
      }
    }
  }

  @NotNull
  private static ProjectImpl createProject(@Nullable String projectName, @NotNull String filePath, boolean isDefault) {
    if (isDefault) {
      return new DefaultProject("");
    }
    return new ProjectImpl(FileUtilRt.toSystemIndependentName(filePath), projectName);
  }

  @Override
  @Nullable
  public Project loadProject(@NotNull String filePath) throws IOException {
    return loadProject(filePath, null);
  }

  @Override
  @Nullable
  public Project loadProject(@NotNull String filePath, @Nullable String projectName) throws IOException {
    try {
      ProjectImpl project = createProject(projectName, new File(filePath).getAbsolutePath(), false);
      initProject(project, null);
      return project;
    }
    catch (Throwable t) {
      LOG.info(t);
      throw new IOException(t);
    }
  }

  @NotNull
  private static String toCanonicalName(@NotNull final String filePath) {
    try {
      return FileUtil.resolveShortWindowsName(filePath);
    }
    catch (IOException e) {
      // OK. File does not yet exist so it's canonical path will be equal to its original path.
    }

    return filePath;
  }

  @TestOnly
  public synchronized boolean isDefaultProjectInitialized() {
    return myDefaultProject != null;
  }

  @Override
  @NotNull
  public synchronized Project getDefaultProject() {
    LOG.assertTrue(!myDefaultProjectWasDisposed, "Default project has been already disposed!");
    if (myDefaultProject == null) {
      ProgressManager.getInstance().executeNonCancelableSection(() -> {
        try {
          myDefaultProject = createProject(null, "", true);
          initProject(myDefaultProject, null);
        }
        catch (Throwable t) {
          PluginManager.processException(t);
        }
      });
    }
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
    if (isLight(project)) {
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

    Runnable process = () -> {
      TransactionGuard.getInstance().submitTransactionAndWait(() -> fireProjectOpened(project));

      StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(project);
      startupManager.runStartupActivities();

      // Startup activities (e.g. the one in FileBasedIndexProjectHandler) have scheduled dumb mode to begin "later"
      // Now we schedule-and-wait to the same event queue to guarantee that the dumb mode really begins now:
      // Post-startup activities should not ever see unindexed and at the same time non-dumb state
      TransactionGuard.getInstance().submitTransactionAndWait(startupManager::startCacheUpdate);

      startupManager.runPostStartupActivitiesFromExtensions();

      GuiUtils.invokeLaterIfNeeded(() -> {
        if (!project.isDisposed()) {
          startupManager.runPostStartupActivities();

          Application application = ApplicationManager.getApplication();
          if (!(application.isHeadlessEnvironment() || application.isUnitTestMode())) {
            StorageUtilKt.checkUnknownMacros(project, true);
          }
        }
        if (ApplicationManager.getApplication().isActive()) {
          JFrame projectFrame = WindowManager.getInstance().getFrame(project);
          if (projectFrame != null) {
            IdeFocusManager.getInstance(project).requestFocus(projectFrame, true);
          }
        }
      }, ModalityState.NON_MODAL);
    };

    if (!loadProjectUnderProgress(project, process)) {
      closeProject(project, false, false, false, true);
      WriteAction.run(() -> Disposer.dispose(project));
      notifyProjectOpenFailed();
      return false;
    }

    return true;
  }

  private boolean loadProjectUnderProgress(@NotNull Project project, @NotNull Runnable performLoading) {
    ProgressIndicator indicator = myProgressManager.getProgressIndicator();
    if (indicator != null) {
      indicator.setText("Preparing workspace...");
      try {
        performLoading.run();
        return true;
      }
      catch (ProcessCanceledException e) {
        return false;
      }
    }
    
    return myProgressManager.runProcessWithProgressSynchronously(performLoading, ProjectBundle.message("project.load.progress"), canCancelProjectLoading(), project);
  }

  private boolean addToOpened(@NotNull Project project) {
    assert !project.isDisposed() : "Must not open already disposed project";
    synchronized (lock) {
      if (isProjectOpened(project)) {
        return false;
      }
      myOpenProjects = ArrayUtil.append(myOpenProjects, project);
      ProjectCoreUtil.theProject = myOpenProjects.length == 1 ? project : null;
      myOpenProjectByHash.put(project.getLocationHash(), project);
    }
    return true;
  }

  private void removeFromOpened(@NotNull Project project) {
    synchronized (lock) {
      myOpenProjects = ArrayUtil.remove(myOpenProjects, project);
      ProjectCoreUtil.theProject = myOpenProjects.length == 1 ? myOpenProjects[0] : null;
      myOpenProjectByHash.values().remove(project); // remove by value and not by key!
    }
  }

  @Nullable
  public Project findOpenProjectByHash(@Nullable String locationHash) {
    return myOpenProjectByHash.get(locationHash);
  }

  private static boolean canCancelProjectLoading() {
    return !ProgressManager.getInstance().isInNonCancelableSection();
  }

  @Override
  public Project loadAndOpenProject(@NotNull final String originalFilePath) throws IOException {
    final String filePath = toCanonicalName(originalFilePath);
    final ConversionResult conversionResult = ConversionService.getInstance().convert(filePath);
    ProjectImpl project;
    if (conversionResult.openingIsCanceled()) {
      project = null;
    }
    else {
      project = createProject(null, toCanonicalName(filePath), false);
      myProgressManager.run(new Task.WithResult<Project, IOException>(project, ProjectBundle.message("project.load.progress"), true) {
        @Override
        protected Project compute(@NotNull ProgressIndicator indicator) throws IOException {
          if (!loadProjectWithProgress(project)) {
            return null;
          }
          if (!conversionResult.conversionNotNeeded()) {
            StartupManager.getInstance(project).registerPostStartupActivity(() -> conversionResult.postStartupActivity(project));
          }
          openProject(project);
          return project;
        }
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

  /**
   * Converts and loads the project at the specified path.
   *
   * @param filePath the path to open the project.
   * @return the project, or null if the user has cancelled opening the project.
   */
  @Override
  @Nullable
  public Project convertAndLoadProject(@NotNull String filePath) throws IOException {
    final String canonicalFilePath = toCanonicalName(filePath);
    final ConversionResult conversionResult = ConversionService.getInstance().convert(canonicalFilePath);
    if (conversionResult.openingIsCanceled()) {
      return null;
    }

    ProjectImpl project = createProject(null, canonicalFilePath, false);
    if (!loadProjectWithProgress(project)) return null;
    if (!conversionResult.conversionNotNeeded()) {
      StartupManager.getInstance(project).registerPostStartupActivity(() -> conversionResult.postStartupActivity(project));
    }
    return project;
  }

  private boolean loadProjectWithProgress(ProjectImpl project) throws IOException {
    try {
      if (!ApplicationManager.getApplication().isDispatchThread() &&
          myProgressManager.getProgressIndicator() != null) {
        initProject(project, null);
        return true;
      }
      myProgressManager.runProcessWithProgressSynchronously((ThrowableComputable<Object, RuntimeException>)() -> {
        initProject(project, null);
        return project;
      }, ProjectBundle.message("project.load.progress"), canCancelProjectLoading(), project);
      return true;
    }
    catch (ProcessCanceledException e) {
      return false;
    }
    catch (Throwable t) {
      LOG.info(t);
      throw new IOException(t);
    }
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
    doReloadProject(project);
  }

  public static void doReloadProject(@NotNull Project project) {
    final Ref<Project> projectRef = Ref.create(project);
    ProjectReloadState.getInstance(project).onBeforeAutomaticProjectReload();
    ApplicationManager.getApplication().invokeLater(() -> {
      LOG.debug("Reloading project.");
      Project project1 = projectRef.get();
      // Let it go
      projectRef.set(null);

      if (project1.isDisposed()) {
        return;
      }

      // must compute here, before project dispose
      String presentableUrl = project1.getPresentableUrl();
      if (!ProjectUtil.closeAndDispose(project1)) {
        return;
      }

      ProjectUtil.openProject(presentableUrl, null, true);
    }, ModalityState.NON_MODAL);
  }

  @Override
  public boolean closeProject(@NotNull final Project project) {
    return closeProject(project, true, true, false, true);
  }

  @TestOnly
  public boolean forceCloseProject(@NotNull Project project, boolean dispose) {
    return closeProject(project, false, false, dispose, false);
  }

  // return true if successful
  public boolean closeAndDisposeAllProjects(boolean checkCanClose) {
    ApplicationManager.getApplication().saveSettings();
    for (Project project : getOpenProjects()) {
      if (!closeProject(project, true, false, true, checkCanClose)) {
        return false;
      }
    }
    return true;
  }

  // saveApp is ignored if saveProject is false
  @SuppressWarnings("TestOnlyProblems")
  private boolean closeProject(@NotNull final Project project,
                               final boolean saveProject,
                               final boolean saveApp,
                               final boolean dispose,
                               boolean checkCanClose) {
    Application app = ApplicationManager.getApplication();
    if (app.isWriteAccessAllowed()) {
      throw new IllegalStateException("Must not call closeProject() from under write action because fireProjectClosing() listeners must have a chance to do something useful");
    }
    app.assertIsDispatchThread();

    if (isLight(project)) {
      // if we close project at the end of the test, just mark it closed; if we are shutting down the entire test framework, proceed to full dispose
      if (!((ProjectImpl)project).isTemporarilyDisposed()) {
        ((ProjectImpl)project).setTemporarilyDisposed(true);
        removeFromOpened(project);
        return true;
      }
      ((ProjectImpl)project).setTemporarilyDisposed(false);
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
      myBusPublisher.projectClosingBeforeSave(project);

      if (saveProject) {
        FileDocumentManager.getInstance().saveAllDocuments();
        project.save();
        if (saveApp) {
          app.saveSettings();
        }
      }

      if (checkCanClose && !ensureCouldCloseIfUnableToSave(project)) {
        return false;
      }

      fireProjectClosing(project); // somebody can start progress here, do not wrap in write action

      app.runWriteAction(() -> {
        removeFromOpened(project);

        fireProjectClosed(project);

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
  public boolean closeAndDispose(@NotNull final Project project) {
    return closeProject(project, true, true, true, true);
  }

  private void fireProjectClosing(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: fireProjectClosing()");
    }

    myBusPublisher.projectClosing(project);
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
    List<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (listeners == null) {
      listeners = ((UserDataHolderEx)project)
        .putUserDataIfAbsent(LISTENERS_IN_PROJECT_KEY, ContainerUtil.createLockFreeCopyOnWriteList());
    }
    listeners.add(listener);
  }

  @Override
  public void removeProjectManagerListener(@NotNull Project project, @NotNull ProjectManagerListener listener) {
    List<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    LOG.assertTrue(listeners != null);
    boolean removed = listeners.remove(listener);
    LOG.assertTrue(removed);
  }

  private void fireProjectOpened(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("projectOpened");
    }

    myBusPublisher.projectOpened(project);
    // https://jetbrains.slack.com/archives/C5E8K7FL4/p1495015043685628
    // projectOpened in the project components is called _after_ message bus event projectOpened for ages
    // old behavior is preserved for now (smooth transition, to not break all), but this order is not logical,
    // because ProjectComponent.projectOpened it is part of project initialization contract, but message bus projectOpened it is just an event
    // (and, so, should be called after project initialization)
    if (project instanceof ProjectImpl) {
      for (ProjectComponent component : ((ProjectImpl)project).getComponentInstancesOfType(ProjectComponent.class)) {
        try {
          component.projectOpened();
        }
        catch (Throwable e) {
          LOG.error(component.toString(), e);
        }
      }
    }
  }

  private void fireProjectClosed(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("projectClosed");
    }

    myBusPublisher.projectClosed(project);
    // see "why is called after message bus" in the fireProjectOpened
    if (project instanceof ProjectImpl) {
      List<ProjectComponent> components = ((ProjectImpl)project).getComponentInstancesOfType(ProjectComponent.class);
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
  }

  @Override
  public boolean canClose(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: canClose()");
    }

    for (ProjectManagerListener listener : getAllListeners(project)) {
      try {
        //noinspection deprecation
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

  // both lists are thread-safe (LockFreeCopyOnWriteArrayList), but ContainerUtil.concat cannot handle situation when list size is changed during iteration
  // so, we have to create list.
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
    UnableToSaveProjectNotification[] notifications =
      NotificationsManager.getNotificationsManager().getNotificationsOfType(UnableToSaveProjectNotification.class, project);
    if (notifications.length == 0) {
      return true;
    }

    StringBuilder message = new StringBuilder();
    message.append(String.format("%s was unable to save some project files,\nare you sure you want to close this project anyway?",
                                 ApplicationNamesInfo.getInstance().getProductName()));

    message.append("\n\nRead-only files:\n");
    int count = 0;
    VirtualFile[] files = notifications[0].myFiles;
    for (VirtualFile file : files) {
      if (count == 10) {
        message.append('\n').append("and ").append(files.length - count).append(" more").append('\n');
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
    public VirtualFile[] myFiles;

    public UnableToSaveProjectNotification(@NotNull final Project project, @NotNull VirtualFile[] readOnlyFiles) {
      super("Project Settings", "Could not save project", "Unable to save project files. Please ensure project files are writable and you have permissions to modify them." +
                                                           " <a href=\"\">Try to save project again</a>.", NotificationType.ERROR,
            (notification, event) -> {
              final UnableToSaveProjectNotification unableToSaveProjectNotification = (UnableToSaveProjectNotification)notification;
              final Project _project = unableToSaveProjectNotification.myProject;
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
  public void saveChangedProjectFile(@NotNull VirtualFile file, @NotNull Project project) {
  }

  @Override
  public void blockReloadingProjectOnExternalChanges() {
  }

  @Override
  public void unblockReloadingProjectOnExternalChanges() {
  }
}
