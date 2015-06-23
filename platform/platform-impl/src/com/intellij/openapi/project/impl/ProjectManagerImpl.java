/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.conversion.ConversionResult;
import com.intellij.conversion.ConversionService;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.*;
import com.intellij.openapi.components.impl.stores.ComponentStoreImpl.ReloadComponentStoreStatus;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.*;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerAdapter;
import com.intellij.openapi.vfs.impl.local.FileWatcher;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SingleAlarm;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@State(name = "ProjectManager", storages = @Storage(
  file = StoragePathMacros.APP_CONFIG + "/project.default.xml"
))
public class ProjectManagerImpl extends ProjectManagerEx implements PersistentStateComponent<Element>, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance(ProjectManagerImpl.class);

  public static final int CURRENT_FORMAT_VERSION = 4;

  private static final Key<List<ProjectManagerListener>> LISTENERS_IN_PROJECT_KEY = Key.create("LISTENERS_IN_PROJECT_KEY");
  private static final Key<MultiMap<StateStorage, VirtualFile>> CHANGED_FILES_KEY = Key.create("CHANGED_FILES_KEY");

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private ProjectImpl myDefaultProject; // Only used asynchronously in save and dispose, which itself are synchronized.
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private Element myDefaultProjectRootElement; // Only used asynchronously in save and dispose, which itself are synchronized.
  private boolean myDefaultProjectConfigurationChanged;

  private Project[] myOpenProjects = {}; // guarded by lock
  private final Object lock = new Object();
  private final List<ProjectManagerListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final SingleAlarm myChangedFilesAlarm;
  private final MultiMap<StateStorage, VirtualFile> myChangedApplicationFiles = MultiMap.createLinkedSet();
  private final AtomicInteger myReloadBlockCount = new AtomicInteger(0);

  private final ProgressManager myProgressManager;
  private volatile boolean myDefaultProjectWasDisposed;

  private final Runnable restartApplicationOrReloadProjectTask = new Runnable() {
    @Override
    public void run() {
      if (isReloadUnblocked() && tryToReloadApplication()) {
        askToReloadProjectIfConfigFilesChangedExternally();
      }
    }
  };

  @NotNull
  private static List<ProjectManagerListener> getListeners(@NotNull Project project) {
    List<ProjectManagerListener> array = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (array == null) return Collections.emptyList();
    return array;
  }

  /** @noinspection UnusedParameters*/
  public ProjectManagerImpl(@NotNull VirtualFileManager virtualFileManager,
                            RecentProjectsManager recentProjectsManager,
                            ProgressManager progressManager) {
    myProgressManager = progressManager;
    Application app = ApplicationManager.getApplication();
    MessageBus messageBus = app.getMessageBus();

    messageBus.connect(app).subscribe(StateStorage.STORAGE_TOPIC, new StateStorage.Listener() {
      @Override
      public void storageFileChanged(@NotNull VirtualFileEvent event, @NotNull StateStorage storage) {
        projectStorageFileChanged(event, storage, null);
      }
    });

    final ProjectManagerListener busPublisher = messageBus.syncPublisher(TOPIC);
    addProjectManagerListener(
      new ProjectManagerListener() {
        @Override
        public void projectOpened(final Project project) {
          project.getMessageBus().connect(project).subscribe(StateStorage.PROJECT_STORAGE_TOPIC, new StateStorage.Listener() {
            @Override
            public void storageFileChanged(@NotNull VirtualFileEvent event, @NotNull StateStorage storage) {
              projectStorageFileChanged(event, storage, project);
            }
          });

          busPublisher.projectOpened(project);
          for (ProjectManagerListener listener : getListeners(project)) {
            listener.projectOpened(project);
          }
        }

        @Override
        public void projectClosed(Project project) {
          busPublisher.projectClosed(project);
          for (ProjectManagerListener listener : getListeners(project)) {
            listener.projectClosed(project);
          }
        }

        @Override
        public boolean canCloseProject(Project project) {
          for (ProjectManagerListener listener : getListeners(project)) {
            if (!listener.canCloseProject(project)) {
              return false;
            }
          }
          return true;
        }

        @Override
        public void projectClosing(Project project) {
          busPublisher.projectClosing(project);
          for (ProjectManagerListener listener : getListeners(project)) {
            listener.projectClosing(project);
          }
        }
      }
    );

    virtualFileManager.addVirtualFileManagerListener(new VirtualFileManagerAdapter() {
      @Override
      public void beforeRefreshStart(boolean asynchronous) {
        blockReloadingProjectOnExternalChanges();
      }

      @Override
      public void afterRefreshFinish(boolean asynchronous) {
        unblockReloadingProjectOnExternalChanges();
      }
    });
    myChangedFilesAlarm = new SingleAlarm(restartApplicationOrReloadProjectTask, 300);
  }

  private void projectStorageFileChanged(@NotNull VirtualFileEvent event, @NotNull StateStorage storage, @Nullable Project project) {
    VirtualFile file = event.getFile();
    if (!StorageUtil.isChangedByStorageOrSaveSession(event) && !(event.getRequestor() instanceof ProjectManagerImpl)) {
      registerProjectToReload(project, file, storage);
    }
  }

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    Disposer.dispose(myChangedFilesAlarm);
    if (myDefaultProject != null) {
      Disposer.dispose(myDefaultProject);

      myDefaultProject = null;
      myDefaultProjectWasDisposed = true;
    }
  }

  public static int TEST_PROJECTS_CREATED;
  private static final boolean LOG_PROJECT_LEAKAGE_IN_TESTS = false;
  private static final int MAX_LEAKY_PROJECTS = 42;
  @SuppressWarnings("FieldCanBeLocal") private final Map<Project, String> myProjects = new WeakHashMap<Project, String>();

  @Override
  @Nullable
  public Project newProject(final String projectName, @NotNull String filePath, boolean useDefaultProjectSettings, boolean isDummy) {
    return newProject(projectName, filePath, useDefaultProjectSettings, isDummy, ApplicationManager.getApplication().isUnitTestMode());
  }

  @Nullable
  public Project newProject(@Nullable String projectName, @NotNull String filePath, boolean useDefaultProjectSettings, boolean isDummy,
                            boolean optimiseTestLoadSpeed) {
    filePath = toCanonicalName(filePath);

    //noinspection ConstantConditions
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      TEST_PROJECTS_CREATED++;
      if (LOG_PROJECT_LEAKAGE_IN_TESTS) {
        for (int i = 0; i < 42; i++) {
          if (myProjects.size() < MAX_LEAKY_PROJECTS) break;
          System.gc();
          TimeoutUtil.sleep(100);
          System.gc();
        }

        if (myProjects.size() >= MAX_LEAKY_PROJECTS) {
          List<Project> copy = new ArrayList<Project>(myProjects.keySet());
          myProjects.clear();
          throw new TooManyProjectLeakedException(copy);
        }
      }
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
    ProjectImpl project = createProject(projectName, filePath, false, optimiseTestLoadSpeed);
    try {
      initProject(project, useDefaultProjectSettings ? (ProjectImpl)getDefaultProject() : null);
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

  private void initProject(@NotNull ProjectImpl project, @Nullable ProjectImpl template) throws IOException {
    ProgressIndicator indicator = myProgressManager.getProgressIndicator();
    if (indicator != null && !project.isDefault()) {
      indicator.setText(ProjectBundle.message("loading.components.for", project.getName()));
      indicator.setIndeterminate(true);
    }

    ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).beforeProjectLoaded(project);

    boolean succeed = false;
    try {
      if (template == null) {
        project.getStateStore().load();
      }
      else {
        project.getStateStore().loadProjectFromTemplate(template);
      }
      project.init();
      succeed = true;
    }
    finally {
      if (!succeed) {
        scheduleDispose(project);
      }
    }
  }

  private ProjectImpl createProject(@Nullable String projectName,
                                    @NotNull String filePath,
                                    boolean isDefault,
                                    boolean isOptimiseTestLoadSpeed) {
    return isDefault ? new DefaultProject(this, "", isOptimiseTestLoadSpeed)
                     : new ProjectImpl(this, new File(filePath).getAbsolutePath(), isOptimiseTestLoadSpeed, projectName);
  }

  private static void scheduleDispose(@NotNull final ProjectImpl project) {
    if (project.isDefault()) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            if (!project.isDisposed()) {
              Disposer.dispose(project);
            }
          }
        });
      }
    });
  }

  @Override
  @Nullable
  public Project loadProject(@NotNull String filePath) throws IOException, JDOMException, InvalidDataException {
    try {
      ProjectImpl project = createProject(null, filePath, false, false);
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
      ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
        @Override
        public void run() {
          try {
            myDefaultProject = createProject(null, "", true, ApplicationManager.getApplication().isUnitTestMode());
            initProject(myDefaultProject, null);
          }
          catch (Throwable t) {
            PluginManager.processException(t);
          }
        }
      });
    }
    return myDefaultProject;
  }

  @Nullable
  public Element getDefaultProjectRootElement() {
    return myDefaultProjectRootElement;
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

    final Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode() && !((ProjectEx)project).getStateStore().checkVersion()) {
      return false;
    }

    if (!addToOpened(project)) return false;

    fireProjectOpened(project);
    DumbService.getInstance(project).queueTask(new DumbModeTask() {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        waitForFileWatcher(indicator);
      }

      @Override
      public String toString() {
        return "wait for file watcher";
      }
    });

    final StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(project);
    boolean ok = myProgressManager.runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        startupManager.runStartupActivities();

        // dumb mode should start before post-startup activities
        // only when startCacheUpdate is called from UI thread, we can guarantee that
        // when the method returns, the application has entered dumb mode
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            startupManager.startCacheUpdate();
          }
        });

        startupManager.runPostStartupActivitiesFromExtensions();

        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!project.isDisposed()) {
              startupManager.runPostStartupActivities();
            }
          }
        });
      }
    }, ProjectBundle.message("project.load.progress"), canCancelProjectLoading(), project);

    if (!ok) {
      closeProject(project, false, false, true);
      notifyProjectOpenFailed();
      return false;
    }

    if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
      // should be invoked last
      startupManager.runWhenProjectIsInitialized(new Runnable() {
        @Override
        public void run() {
          TrackingPathMacroSubstitutor substitutor = ((ProjectEx)project).getStateStore().getStateStorageManager().getMacroSubstitutor();
          if (substitutor != null) {
            StorageUtil.notifyUnknownMacros(substitutor, project, null);
          }
        }
      });
    }

    return true;
  }

  private boolean addToOpened(@NotNull Project project) {
    assert !project.isDisposed() : "Must not open already disposed project";
    synchronized (lock) {
      if (isProjectOpened(project)) {
        return false;
      }
      myOpenProjects = ArrayUtil.append(myOpenProjects, project);
    }
    return true;
  }

  @NotNull
  private Collection<Project> removeFromOpened(@NotNull Project project) {
    synchronized (lock) {
      myOpenProjects = ArrayUtil.remove(myOpenProjects, project);
      return Arrays.asList(myOpenProjects);
    }
  }

  private static boolean canCancelProjectLoading() {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    return !(indicator instanceof NonCancelableSection);
  }

  private static void waitForFileWatcher(@NotNull ProgressIndicator indicator) {
    LocalFileSystem fs = LocalFileSystem.getInstance();
    if (!(fs instanceof LocalFileSystemImpl)) return;

    final FileWatcher watcher = ((LocalFileSystemImpl)fs).getFileWatcher();
    if (!watcher.isOperational() || !watcher.isSettingRoots()) return;

    LOG.info("FW/roots waiting started");
    indicator.setIndeterminate(true);
    indicator.setText(ProjectBundle.message("project.load.waiting.watcher"));
    if (indicator instanceof ProgressWindow) {
      ((ProgressWindow)indicator).setCancelButtonText(CommonBundle.message("button.skip"));
    }
    while (watcher.isSettingRoots() && !indicator.isCanceled()) {
      TimeoutUtil.sleep(10);
    }
    LOG.info("FW/roots waiting finished");
  }

  @Override
  public Project loadAndOpenProject(@NotNull final String filePath) throws IOException {
    final Project project = convertAndLoadProject(filePath);
    if (project == null) {
      WelcomeFrame.showIfNoProjectOpened();
      return null;
    }

    // todo unify this logic with PlatformProjectOpenProcessor
    if (!openProject(project)) {
      WelcomeFrame.showIfNoProjectOpened();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
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
    final String fp = toCanonicalName(filePath);
    final ConversionResult conversionResult = ConversionService.getInstance().convert(fp);
    if (conversionResult.openingIsCanceled()) {
      return null;
    }

    final Project project;
    try {
      project = loadProjectWithProgress(filePath);
      if (project == null) return null;
    }
    catch (IOException e) {
      LOG.info(e);
      throw e;
    }
    catch (Throwable t) {
      LOG.info(t);
      throw new IOException(t);
    }

    if (!conversionResult.conversionNotNeeded()) {
      StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
        @Override
        public void run() {
          conversionResult.postStartupActivity(project);
        }
      });
    }
    return project;
  }

  /**
   * Opens the project at the specified path.
   *
   * @param filePath the path to open the project.
   * @return the project, or null if the user has cancelled opening the project.
   */
  @Nullable
  private Project loadProjectWithProgress(@NotNull final String filePath) throws IOException {
    final ProjectImpl project = createProject(null, toCanonicalName(filePath), false, false);
    try {
      myProgressManager.runProcessWithProgressSynchronously(new ThrowableComputable<Project, IOException>() {
        @Override
        @Nullable
        public Project compute() throws IOException {
          initProject(project, null);
          return project;
        }
      }, ProjectBundle.message("project.load.progress"), canCancelProjectLoading(), project);
    }
    catch (StateStorageException e) {
      throw new IOException(e);
    }
    catch (ProcessCanceledException ignore) {
      return null;
    }

    return project;
  }

  private static void notifyProjectOpenFailed() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed();
    WelcomeFrame.showIfNoProjectOpened();
  }

  private void askToReloadProjectIfConfigFilesChangedExternally() {
    List<Project> projectsToReload = new SmartList<Project>();
    for (Project project : getOpenProjects()) {
      if (project.isDisposed()) {
        continue;
      }

      MultiMap<StateStorage, VirtualFile> changes = CHANGED_FILES_KEY.get(project);
      if (changes == null) {
        continue;
      }

      CHANGED_FILES_KEY.set(project, null);
      if (!changes.isEmpty() && ComponentStoreImpl.reloadStore(changes, ((ProjectEx)project).getStateStore()) == ReloadComponentStoreStatus.RESTART_AGREED) {
        projectsToReload.add(project);
      }
    }

    for (Project project : projectsToReload) {
      doReloadProject(project);
    }
  }

  private boolean tryToReloadApplication() {
    if (ApplicationManager.getApplication().isDisposed()) {
      return false;
    }
    if (myChangedApplicationFiles.isEmpty()) {
      return true;
    }

    MultiMap<StateStorage, VirtualFile> changes = MultiMap.createLinkedSet();
    changes.putAllValues(myChangedApplicationFiles);
    myChangedApplicationFiles.clear();

    ReloadComponentStoreStatus status = ComponentStoreImpl.reloadStore(changes, ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore());
    if (status == ReloadComponentStoreStatus.RESTART_AGREED) {
      ApplicationManagerEx.getApplicationEx().restart(true);
      return false;
    }
    else {
      return status == ReloadComponentStoreStatus.SUCCESS || status == ReloadComponentStoreStatus.RESTART_CANCELLED;
    }
  }

  @Override
  public void blockReloadingProjectOnExternalChanges() {
    myReloadBlockCount.incrementAndGet();
  }

  @Override
  public void unblockReloadingProjectOnExternalChanges() {
    if (myReloadBlockCount.decrementAndGet() == 0 && myChangedFilesAlarm.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(restartApplicationOrReloadProjectTask, ModalityState.NON_MODAL, ApplicationManager.getApplication().getDisposed());
    }
  }

  private boolean isReloadUnblocked() {
    int count = myReloadBlockCount.get();
    if (LOG.isDebugEnabled()) {
      LOG.debug("[RELOAD] myReloadBlockCount = " + count);
    }
    return count == 0;
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
    closeProject(project);
    return Arrays.asList(getOpenProjects());
  }

  @Override
  public void saveChangedProjectFile(@NotNull VirtualFile file, @NotNull Project project) {
    StateStorageManager storageManager = ((ProjectEx)project).getStateStore().getStateStorageManager();
    String fileSpec = storageManager.collapseMacros(file.getPath());
    Couple<Collection<FileBasedStorage>> storages = storageManager.getCachedFileStateStorages(Collections.singletonList(fileSpec), Collections.<String>emptyList());
    FileBasedStorage storage = ContainerUtil.getFirstItem(storages.first);
    // if empty, so, storage is not yet loaded, so, we don't have to reload
    if (storage != null) {
      registerProjectToReload(project, file, storage);
    }
  }

  private void registerProjectToReload(@Nullable Project project, @NotNull VirtualFile file, @NotNull StateStorage storage) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("[RELOAD] Registering project to reload: " + file, new Exception());
    }

    MultiMap<StateStorage, VirtualFile> changes;
    if (project == null) {
      changes = myChangedApplicationFiles;
    }
    else {
      changes = CHANGED_FILES_KEY.get(project);
      if (changes == null) {
        changes = MultiMap.createLinkedSet();
        CHANGED_FILES_KEY.set(project, changes);
      }
    }

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (changes) {
      changes.putValue(storage, file);
    }

    if (storage instanceof StateStorageBase) {
      ((StateStorageBase)storage).disableSaving();
    }

    if (isReloadUnblocked()) {
      myChangedFilesAlarm.cancelAndRequest();
    }
  }

  @Override
  public void reloadProject(@NotNull Project project) {
    CHANGED_FILES_KEY.set(project, null);
    doReloadProject(project);
  }

  private static void doReloadProject(@NotNull Project project) {
    final Ref<Project> projectRef = Ref.create(project);
    ProjectReloadState.getInstance(project).onBeforeAutomaticProjectReload();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        LOG.debug("Reloading project.");
        Project project = projectRef.get();
        // Let it go
        projectRef.set(null);

        if (project.isDisposed()) {
          return;
        }

        // must compute here, before project dispose
        String presentableUrl = project.getPresentableUrl();
        if (!ProjectUtil.closeAndDispose(project)) {
          return;
        }

        ProjectUtil.openProject(presentableUrl, null, true);
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public boolean closeProject(@NotNull final Project project) {
    return closeProject(project, true, false, true);
  }

  public boolean closeProject(@NotNull final Project project, final boolean save, final boolean dispose, boolean checkCanClose) {
    if (isLight(project)) {
      // if we close project at the end of the test, just mark it closed; if we are shutting down the entire test framework, proceed to full dispose
      if (!((ProjectImpl)project).isTemporarilyDisposed()) {
        ((ProjectImpl)project).setTemporarilyDisposed(true);
        removeFromOpened(project);
        return true;
      }
      ((ProjectImpl)project).setTemporarilyDisposed(false);
    }
    else {
      if (!isProjectOpened(project)) return true;
    }
    if (checkCanClose && !canClose(project)) return false;
    final ShutDownTracker shutDownTracker = ShutDownTracker.getInstance();
    shutDownTracker.registerStopperThread(Thread.currentThread());
    try {
      if (save) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            FileDocumentManager.getInstance().saveAllDocuments();
            project.save();
          }
        });
      }

      if (checkCanClose && !ensureCouldCloseIfUnableToSave(project)) {
        return false;
      }

      fireProjectClosing(project); // somebody can start progress here, do not wrap in write action

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          removeFromOpened(project);

          fireProjectClosed(project);

          if (dispose) {
            Disposer.dispose(project);
          }
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
    return ApplicationManager.getApplication().isUnitTestMode() && project.toString().contains("light_temp_");
  }

  @Override
  public boolean closeAndDispose(@NotNull final Project project) {
    return closeProject(project, true, true, true);
  }

  private void fireProjectClosing(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: fireProjectClosing()");
    }

    for (ProjectManagerListener listener : myListeners) {
      try {
        listener.projectClosing(project);
      }
      catch (Exception e) {
        LOG.error("From listener "+listener+" ("+listener.getClass()+")", e);
      }
    }
  }

  @Override
  public void addProjectManagerListener(@NotNull ProjectManagerListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addProjectManagerListener(@NotNull final ProjectManagerListener listener, @NotNull Disposable parentDisposable) {
    addProjectManagerListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeProjectManagerListener(listener);
      }
    });
  }

  @Override
  public void removeProjectManagerListener(@NotNull ProjectManagerListener listener) {
    boolean removed = myListeners.remove(listener);
    LOG.assertTrue(removed);
  }

  @Override
  public void addProjectManagerListener(@NotNull Project project, @NotNull ProjectManagerListener listener) {
    List<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (listeners == null) {
      listeners = ((UserDataHolderEx)project)
        .putUserDataIfAbsent(LISTENERS_IN_PROJECT_KEY, ContainerUtil.<ProjectManagerListener>createLockFreeCopyOnWriteList());
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

    for (ProjectManagerListener listener : myListeners) {
      try {
        listener.projectOpened(project);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private void fireProjectClosed(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("projectClosed");
    }

    for (ProjectManagerListener listener : myListeners) {
      try {
        listener.projectClosed(project);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public boolean canClose(@NotNull Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: canClose()");
    }

    for (ProjectManagerListener listener : myListeners) {
      try {
        if (!listener.canCloseProject(project)) return false;
      }
      catch (Throwable e) {
        LOG.warn(e); // DO NOT LET ANY PLUGIN to prevent closing due to exception
      }
    }

    return true;
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


  @Nullable
  @Override
  public Element getState() {
    if (myDefaultProject != null) {
      myDefaultProject.save();
    }

    if (!myDefaultProjectConfigurationChanged) {
      // we are not ready to save
      return null;
    }

    Element element = new Element("state");
    myDefaultProjectRootElement.detach();
    element.addContent(myDefaultProjectRootElement);
    return element;
  }

  @Override
  public void loadState(Element state) {
    myDefaultProjectRootElement = state.getChild("defaultProject");
    if (myDefaultProjectRootElement != null) {
      myDefaultProjectRootElement.detach();
    }
    myDefaultProjectConfigurationChanged = false;
  }

  public void setDefaultProjectRootElement(@NotNull Element defaultProjectRootElement) {
    myDefaultProjectRootElement = defaultProjectRootElement;
    myDefaultProjectConfigurationChanged = true;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "ProjectManager";
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("project.default")};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("project.default.settings");
  }

  public static class UnableToSaveProjectNotification extends Notification {
    private Project myProject;
    public VirtualFile[] myFiles;

    public UnableToSaveProjectNotification(@NotNull final Project project, @NotNull VirtualFile[] readOnlyFiles) {
      super("Project Settings", "Could not save project", "Unable to save project files. Please ensure project files are writable and you have permissions to modify them." +
                                                           " <a href=\"\">Try to save project again</a>.", NotificationType.ERROR, new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          final UnableToSaveProjectNotification unableToSaveProjectNotification = (UnableToSaveProjectNotification)notification;
          final Project _project = unableToSaveProjectNotification.myProject;
          notification.expire();

          if (_project != null && !_project.isDisposed()) {
            _project.save();
          }
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
}
