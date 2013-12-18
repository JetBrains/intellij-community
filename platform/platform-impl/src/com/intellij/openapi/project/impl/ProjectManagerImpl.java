/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.components.impl.stores.XmlElementStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.project.ProjectReloadState;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.local.FileWatcher;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectLongHashMap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectManagerImpl extends ProjectManagerEx implements NamedJDOMExternalizable, ExportableApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectManagerImpl");

  public static final int CURRENT_FORMAT_VERSION = 4;

  private static final Key<List<ProjectManagerListener>> LISTENERS_IN_PROJECT_KEY = Key.create("LISTENERS_IN_PROJECT_KEY");
  @NonNls private static final String ELEMENT_DEFAULT_PROJECT = "defaultProject";

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private ProjectImpl myDefaultProject; // Only used asynchronously in save and dispose, which itself are synchronized.

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private Element myDefaultProjectRootElement; // Only used asynchronously in save and dispose, which itself are synchronized.

  private final List<Project> myOpenProjects = new ArrayList<Project>();
  private Project[] myOpenProjectsArrayCache = {};
  private final List<ProjectManagerListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final Set<Project> myTestProjects = new THashSet<Project>();

  private final Map<VirtualFile, byte[]> mySavedCopies = new HashMap<VirtualFile, byte[]>();
  private final TObjectLongHashMap<VirtualFile> mySavedTimestamps = new TObjectLongHashMap<VirtualFile>();
  private final Map<Project, List<Pair<VirtualFile, StateStorage>>> myChangedProjectFiles =
    new HashMap<Project, List<Pair<VirtualFile, StateStorage>>>();
  private final Alarm myChangedFilesAlarm = new Alarm();
  private final List<Pair<VirtualFile, StateStorage>> myChangedApplicationFiles = new ArrayList<Pair<VirtualFile, StateStorage>>();
  private final AtomicInteger myReloadBlockCount = new AtomicInteger(0);
  @SuppressWarnings("FieldCanBeLocal") private final Map<Project, String> myProjects = new WeakHashMap<Project, String>();
  private static final int MAX_LEAKY_PROJECTS = 42;
  private final ProgressManager myProgressManager;
  private volatile boolean myDefaultProjectWasDisposed = false;

  @NotNull
  private static List<ProjectManagerListener> getListeners(Project project) {
    List<ProjectManagerListener> array = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (array == null) return Collections.emptyList();
    return array;
  }

  public ProjectManagerImpl(VirtualFileManager virtualFileManager, ProgressManager progressManager) {
    myProgressManager = progressManager;
    Application app = ApplicationManager.getApplication();
    MessageBus messageBus = app.getMessageBus();
    MessageBusConnection connection = messageBus.connect(app);
    connection.subscribe(StateStorage.STORAGE_TOPIC, new StateStorage.Listener() {
      @Override
      public void storageFileChanged(@NotNull final VirtualFileEvent event, @NotNull final StateStorage storage) {
        VirtualFile file = event.getFile();
        if (!file.isDirectory() && !(event.getRequestor() instanceof StateStorage.SaveSession)) {
          saveChangedProjectFile(file, null, storage);
        }
      }
    });
    final ProjectManagerListener busPublisher = messageBus.syncPublisher(TOPIC);

    addProjectManagerListener(
      new ProjectManagerListener() {
        @Override
        public void projectOpened(final Project project) {
          MessageBus messageBus = project.getMessageBus();
          MessageBusConnection connection = messageBus.connect(project);
          connection.subscribe(StateStorage.STORAGE_TOPIC, new StateStorage.Listener() {
            @Override
            public void storageFileChanged(@NotNull final VirtualFileEvent event, @NotNull final StateStorage storage) {
              VirtualFile file = event.getFile();
              if (!file.isDirectory() && !(event.getRequestor() instanceof StateStorage.SaveSession)) {
                saveChangedProjectFile(file, project, storage);
              }
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

    registerExternalProjectFileListener(virtualFileManager);
  }

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

  @Override
  public void initComponent() {
  }

  private static final boolean LOG_PROJECT_LEAKAGE_IN_TESTS = false;

  @Override
  @Nullable
  public Project newProject(final String projectName, @NotNull String filePath, boolean useDefaultProjectSettings, boolean isDummy) {
    filePath = toCanonicalName(filePath);

    //noinspection ConstantConditions
    if (LOG_PROJECT_LEAKAGE_IN_TESTS && ApplicationManager.getApplication().isUnitTestMode()) {
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

    ProjectImpl project = createProject(projectName, filePath, false, ApplicationManager.getApplication().isUnitTestMode());
    try {
      initProject(project, useDefaultProjectSettings ? (ProjectImpl)getDefaultProject() : null);
      if (LOG_PROJECT_LEAKAGE_IN_TESTS) {
        myProjects.put(project, null);
      }
      return project;
    }
    catch (final Exception e) {
      LOG.info(e);
      Messages.showErrorDialog(message(e), ProjectBundle.message("project.load.default.error"));
    }
    return null;
  }

  @NonNls
  private static String message(Throwable e) {
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

  private void initProject(@NotNull ProjectImpl project, @Nullable ProjectImpl template) throws IOException {

    final ProgressIndicator indicator = myProgressManager.getProgressIndicator();
    if (indicator != null) {
      indicator.setText(ProjectBundle.message("loading.components.for", project.isDefault() ? "Default" : project.getName()));
      indicator.setIndeterminate(true);
    }

    ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).beforeProjectLoaded(project);

    try {
      if (template != null) {
        project.getStateStore().loadProjectFromTemplate(template);
      }
      else {
        project.getStateStore().load();
      }
      project.loadProjectComponents();
      project.init();
    }
    catch (IOException e) {
      scheduleDispose(project);
      throw e;
    }
    catch (ProcessCanceledException e) {
      scheduleDispose(project);
      throw e;
    }
  }

  private ProjectImpl createProject(@Nullable String projectName,
                                    @NotNull String filePath,
                                    boolean isDefault,
                                    boolean isOptimiseTestLoadSpeed) {
    return isDefault ? new DefaultProject(this, "", isOptimiseTestLoadSpeed, projectName)
                     : new ProjectImpl(this, new File(filePath).getAbsolutePath(), isOptimiseTestLoadSpeed, projectName);
  }

  private static void scheduleDispose(final ProjectImpl project) {
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
    catch (StateStorageException e) {
      throw new IOException(e.getMessage());
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
      try {
        myDefaultProject = createProject("Default project", "Default project", true, ApplicationManager.getApplication().isUnitTestMode());
        initProject(myDefaultProject, null);
        myDefaultProjectRootElement = null;
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (StateStorageException e) {
        LOG.error(e);
      }
    }
    return myDefaultProject;
  }


  public Element getDefaultProjectRootElement() {
    return myDefaultProjectRootElement;
  }

  @Override
  @NotNull
  public Project[] getOpenProjects() {
    synchronized (myOpenProjects) {
      if (myOpenProjectsArrayCache.length != myOpenProjects.size()) {
        LOG.error("Open projects: " + myOpenProjects + "; cache: " + Arrays.asList(myOpenProjectsArrayCache));
      }
      if (myOpenProjectsArrayCache.length > 0 && myOpenProjectsArrayCache[0] != myOpenProjects.get(0)) {
        LOG.error("Open projects cache corrupted. Open projects: " + myOpenProjects + "; cache: " + Arrays.asList(myOpenProjectsArrayCache));
      }
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        Project[] testProjects = myTestProjects.toArray(new Project[myTestProjects.size()]);
        for (Project testProject : testProjects) {
          assert !testProject.isDisposed() : testProject;
        }
        return ArrayUtil.mergeArrays(myOpenProjectsArrayCache, testProjects);
      }
      return myOpenProjectsArrayCache;
    }
  }

  @Override
  public boolean isProjectOpened(Project project) {
    synchronized (myOpenProjects) {
      return ApplicationManager.getApplication().isUnitTestMode() && myTestProjects.contains(project) || myOpenProjects.contains(project);
    }
  }

  @Override
  public boolean openProject(final Project project) {
    if (isLight(project)) {
      throw new AssertionError("must not open light project");
    }
    final Application application = ApplicationManager.getApplication();

    if (!application.isUnitTestMode() && !((ProjectEx)project).getStateStore().checkVersion()) {
      return false;
    }

    synchronized (myOpenProjects) {
      if (myOpenProjects.contains(project)) {
        return false;
      }
      myOpenProjects.add(project);
      cacheOpenProjects();
    }
    fireProjectOpened(project);

    final StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(project);
    waitForFileWatcher(project);
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
            startupManager.runPostStartupActivities();
          }
        });
      }
    }, ProjectBundle.message("project.load.progress"), true, project);

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
          final TrackingPathMacroSubstitutor macroSubstitutor =
            ((ProjectEx)project).getStateStore().getStateStorageManager().getMacroSubstitutor();
          if (macroSubstitutor != null) {
            StorageUtil.notifyUnknownMacros(macroSubstitutor, project, null);
          }
        }
      });
    }

    return true;
  }

  private void cacheOpenProjects() {
    myOpenProjectsArrayCache = myOpenProjects.toArray(new Project[myOpenProjects.size()]);
  }

  private void waitForFileWatcher(@NotNull Project project) {
    LocalFileSystem fs = LocalFileSystem.getInstance();
    if (!(fs instanceof LocalFileSystemImpl)) return;

    final FileWatcher watcher = ((LocalFileSystemImpl)fs).getFileWatcher();
    if (!watcher.isOperational() || !watcher.isSettingRoots()) return;

    LOG.info("FW/roots waiting started");
    Task.Modal task = new Task.Modal(project, ProjectBundle.message("project.load.progress"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
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
    };
    myProgressManager.run(task);
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
  public Project convertAndLoadProject(String filePath) throws IOException {
    final String fp = toCanonicalName(filePath);
    final ConversionResult conversionResult = ConversionService.getInstance().convert(fp);
    if (conversionResult.openingIsCanceled()) {
      return null;
    }

    final Project project = loadProjectWithProgress(filePath);
    if (project == null) return null;

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
      }, ProjectBundle.message("project.load.progress"), true, project);
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

  private void registerExternalProjectFileListener(VirtualFileManager virtualFileManager) {
    virtualFileManager.addVirtualFileManagerListener(new VirtualFileManagerListener() {
      @Override
      public void beforeRefreshStart(boolean asynchronous) {
      }

      @Override
      public void afterRefreshFinish(boolean asynchronous) {
        scheduleReloadApplicationAndProject();
      }
    });
  }

  private void askToReloadProjectIfConfigFilesChangedExternally() {
    LOG.debug("[RELOAD] myReloadBlockCount = " + myReloadBlockCount.get());
    if (myReloadBlockCount.get() == 0) {
      Set<Project> projects;

      synchronized (myChangedProjectFiles) {
        if (myChangedProjectFiles.isEmpty()) return;
        projects = new HashSet<Project>(myChangedProjectFiles.keySet());
      }

      List<Project> projectsToReload = new ArrayList<Project>();

      for (Project project : projects) {
        if (shouldReloadProject(project)) {
          projectsToReload.add(project);
        }
      }

      for (final Project projectToReload : projectsToReload) {
        reloadProjectImpl(projectToReload, false);
      }
    }
  }

  private boolean tryToReloadApplication() {
    try {
      final Application app = ApplicationManager.getApplication();

      if (app.isDisposed()) return false;
      final HashSet<Pair<VirtualFile, StateStorage>> causes = new HashSet<Pair<VirtualFile, StateStorage>>(myChangedApplicationFiles);
      if (causes.isEmpty()) return true;

      final boolean[] reloadOk = {false};
      final LinkedHashSet<String> components = new LinkedHashSet<String>();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            reloadOk[0] = ((ApplicationImpl)app).getStateStore().reload(causes, components);
          }
          catch (StateStorageException e) {
            Messages.showWarningDialog(ProjectBundle.message("project.reload.failed", e.getMessage()),
                                       ProjectBundle.message("project.reload.failed.title"));
          }
          catch (IOException e) {
            Messages.showWarningDialog(ProjectBundle.message("project.reload.failed", e.getMessage()),
                                       ProjectBundle.message("project.reload.failed.title"));
          }
        }
      });

      if (!reloadOk[0] && !components.isEmpty()) {
        String message = "Application components were changed externally and cannot be reloaded:\n";
        for (String component : components) {
          message += component + "\n";
        }

        final boolean canRestart = ApplicationManager.getApplication().isRestartCapable();
        message += "Would you like to " + (canRestart ? "restart " : "shutdown ");
        message += ApplicationNamesInfo.getInstance().getProductName() + "?";

        if (Messages.showYesNoDialog(message,
                                     "Application Configuration Reload", Messages.getQuestionIcon()) == Messages.YES) {
          for (Pair<VirtualFile, StateStorage> cause : causes) {
            StateStorage stateStorage = cause.getSecond();
            if (stateStorage instanceof XmlElementStorage) {
              ((XmlElementStorage)stateStorage).disableSaving();
            }
          }
          ApplicationManagerEx.getApplicationEx().restart(true);
        }
      }

      return reloadOk[0];
    }
    finally {
      myChangedApplicationFiles.clear();
    }
  }

  private boolean shouldReloadProject(final Project project) {
    if (project.isDisposed()) return false;
    final HashSet<Pair<VirtualFile, StateStorage>> causes = new HashSet<Pair<VirtualFile, StateStorage>>();

    synchronized (myChangedProjectFiles) {
      final List<Pair<VirtualFile, StateStorage>> changes = myChangedProjectFiles.remove(project);
      if (changes != null) {
        causes.addAll(changes);
      }

      if (causes.isEmpty()) return false;
    }

    final boolean[] reloadOk = {false};

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          LOG.debug("[RELOAD] Reloading project/components...");
          reloadOk[0] = ((ProjectEx)project).getStateStore().reload(causes);
        }
        catch (StateStorageException e) {
          Messages.showWarningDialog(ProjectBundle.message("project.reload.failed", e.getMessage()),
                                     ProjectBundle.message("project.reload.failed.title"));
        }
        catch (IOException e) {
          Messages.showWarningDialog(ProjectBundle.message("project.reload.failed", e.getMessage()),
                                     ProjectBundle.message("project.reload.failed.title"));
        }
      }
    });
    if (reloadOk[0]) return false;

    String message;
    if (causes.size() == 1) {
      message = ProjectBundle.message("project.reload.external.change.single", causes.iterator().next().first.getPresentableUrl());
    }
    else {
      StringBuilder filesBuilder = new StringBuilder();
      boolean first = true;
      Set<String> alreadyShown = new HashSet<String>();
      for (Pair<VirtualFile, StateStorage> cause : causes) {
        String url = cause.first.getPresentableUrl();
        if (!alreadyShown.contains(url)) {
          if (alreadyShown.size() > 10) {
            filesBuilder.append("\n" + "and ").append(causes.size() - alreadyShown.size()).append(" more");
            break;
          }
          if (!first) filesBuilder.append("\n");
          first = false;
          filesBuilder.append(url);
          alreadyShown.add(url);
        }
      }
      message = ProjectBundle.message("project.reload.external.change.multiple", filesBuilder.toString());
    }

    return Messages.showTwoStepConfirmationDialog(message, ProjectBundle.message("project.reload.external.change.title"), "Reload project",
                                                  Messages.getQuestionIcon()) == 0;
  }

  @Override
  public boolean isFileSavedToBeReloaded(VirtualFile candidate) {
    return mySavedCopies.containsKey(candidate);
  }

  @Override
  public void blockReloadingProjectOnExternalChanges() {
    myReloadBlockCount.incrementAndGet();
  }

  @Override
  public void unblockReloadingProjectOnExternalChanges() {
    if (myReloadBlockCount.decrementAndGet() == 0) scheduleReloadApplicationAndProject();
  }

  private void scheduleReloadApplicationAndProject() {
    // todo: commented due to "IDEA-61938 Libraries configuration is kept if switching branches"
    // because of save which may happen _before_ project reload ;(

    //ApplicationManager.getApplication().invokeLater(new Runnable() {
    //  public void run() {
    //IdeEventQueue.getInstance().addIdleListener(new Runnable() {
    //  @Override
    //  public void run() {
    //    IdeEventQueue.getInstance().removeIdleListener(this);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!tryToReloadApplication()) return;
        askToReloadProjectIfConfigFilesChangedExternally();
      }
    }, ModalityState.NON_MODAL);
    //}
    //}, 2000);
    //}
    //}, ModalityState.NON_MODAL);
  }

  @Override
  public void openTestProject(@NotNull final Project project) {
    synchronized (myOpenProjects) {
      assert ApplicationManager.getApplication().isUnitTestMode();
      assert !project.isDisposed() : "Must not open already disposed project";
      myTestProjects.add(project);
    }
  }

  @Override
  public Collection<Project> closeTestProject(@NotNull Project project) {
    synchronized (myOpenProjects) {
      assert ApplicationManager.getApplication().isUnitTestMode();
      myTestProjects.remove(project);
      return myTestProjects;
    }
  }

  @Override
  public void saveChangedProjectFile(final VirtualFile file, final Project project) {
    if (file.exists()) {
      copyToTemp(file);
    }
    registerProjectToReload(project, file, null);
  }

  private void saveChangedProjectFile(final VirtualFile file, @Nullable final Project project, final StateStorage storage) {
    if (file.exists()) {
      copyToTemp(file);
    }
    registerProjectToReload(project, file, storage);
  }

  private void registerProjectToReload(@Nullable final Project project, final VirtualFile cause, @Nullable final StateStorage storage) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("[RELOAD] Registering project to reload: " + cause, new Exception());
    }

    if (project != null) {
      synchronized (myChangedProjectFiles) {
        List<Pair<VirtualFile, StateStorage>> changedProjectFiles = myChangedProjectFiles.get(project);
        if (changedProjectFiles == null) {
          changedProjectFiles = new ArrayList<Pair<VirtualFile, StateStorage>>();
          myChangedProjectFiles.put(project, changedProjectFiles);
        }

        changedProjectFiles.add(new Pair<VirtualFile, StateStorage>(cause, storage));
      }
    }
    else {
      myChangedApplicationFiles.add(new Pair<VirtualFile, StateStorage>(cause, storage));
    }

    myChangedFilesAlarm.cancelAllRequests();
    myChangedFilesAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        LOG.debug("[RELOAD] Scheduling reload application & project, myReloadBlockCount = " + myReloadBlockCount);
        if (myReloadBlockCount.get() == 0) {
          scheduleReloadApplicationAndProject();
        }
      }
    }, 444);
  }

  private void copyToTemp(VirtualFile file) {
    try {
      final byte[] bytes = file.contentsToByteArray();
      mySavedCopies.put(file, bytes);
      mySavedTimestamps.put(file, file.getTimeStamp());
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void restoreCopy(VirtualFile file) {
    try {
      if (file == null) return; // Externally deleted actually.
      if (!file.isWritable()) return; // IDEA was unable to save it as well. So no need to restore.

      final byte[] bytes = mySavedCopies.get(file);
      if (bytes != null) {
        try {
          file.setBinaryContent(bytes, -1, mySavedTimestamps.get(file));
        }
        catch (IOException e) {
          Messages.showWarningDialog(ProjectBundle.message("project.reload.write.failed", file.getPresentableUrl()),
                                     ProjectBundle.message("project.reload.write.failed.title"));
        }
      }
    }
    finally {
      mySavedCopies.remove(file);
      mySavedTimestamps.remove(file);
    }
  }

  @Override
  public void reloadProject(@NotNull final Project p) {
    reloadProjectImpl(p, true);
  }

  public void reloadProjectImpl(@NotNull final Project p, final boolean clearCopyToRestore) {
    if (clearCopyToRestore) {
      mySavedCopies.clear();
      mySavedTimestamps.clear();
    }

    final Project[] project = {p};

    ProjectReloadState.getInstance(project[0]).onBeforeAutomaticProjectReload();
    final Application application = ApplicationManager.getApplication();

    application.invokeLater(new Runnable() {
      @Override
      public void run() {
        LOG.debug("Reloading project.");
        ProjectImpl projectImpl = (ProjectImpl)project[0];
        if (projectImpl.isDisposed()) return;
        IProjectStore projectStore = projectImpl.getStateStore();
        final String location = projectImpl.getPresentableUrl();

        final List<IFile> original;
        try {
          final IComponentStore.SaveSession saveSession = projectStore.startSave();
          original = saveSession.getAllStorageFiles(true);
          saveSession.finishSave();
        }
        catch (IOException e) {
          LOG.error(e);
          return;
        }

        if (project[0].isDisposed() || ProjectUtil.closeAndDispose(project[0])) {
          application.runWriteAction(new Runnable() {
            @Override
            public void run() {
              for (final IFile originalFile : original) {
                restoreCopy(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(originalFile));
              }
            }
          });

          project[0] = null; // Let it go.

          ProjectUtil.openProject(location, null, true);
        }
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public boolean closeProject(@NotNull final Project project) {
    return closeProject(project, true, false, true);
  }

  public boolean closeProject(@NotNull final Project project, final boolean save, final boolean dispose, boolean checkCanClose) {
    if (isLight(project)) {
      throw new AssertionError("must not close light project");
    }
    if (!isProjectOpened(project)) return true;
    if (checkCanClose && !canClose(project)) return false;
    final ShutDownTracker shutDownTracker = ShutDownTracker.getInstance();
    shutDownTracker.registerStopperThread(Thread.currentThread());
    try {
      if (save) {
        FileDocumentManager.getInstance().saveAllDocuments();
        project.save();
      }

      if (checkCanClose && !ensureCouldCloseIfUnableToSave(project)) {
        return false;
      }

      fireProjectClosing(project); // somebody can start progress here, do not wrap in write action

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          synchronized (myOpenProjects) {
            myOpenProjects.remove(project);
            cacheOpenProjects();
            myTestProjects.remove(project);
          }

          myChangedProjectFiles.remove(project);

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

  public static boolean isLight(@NotNull Project project) {
    return ApplicationManager.getApplication().isUnitTestMode() && project.toString().contains("light_temp_");
  }

  @Override
  public boolean closeAndDispose(@NotNull final Project project) {
    return closeProject(project, true, true, true);
  }

  private void fireProjectClosing(Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: fireProjectClosing()");
    }

    for (ProjectManagerListener listener : myListeners) {
      try {
        listener.projectClosing(project);
      }
      catch (Exception e) {
        LOG.error(e);
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

  private void fireProjectOpened(Project project) {
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

  private void fireProjectClosed(Project project) {
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
  public boolean canClose(Project project) {
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

  private static boolean ensureCouldCloseIfUnableToSave(@NotNull final Project project) {
    final ProjectImpl.UnableToSaveProjectNotification[] notifications =
      NotificationsManager.getNotificationsManager().getNotificationsOfType(ProjectImpl.UnableToSaveProjectNotification.class, project);
    if (notifications.length == 0) return true;

    final String fileNames = StringUtil.join(notifications[0].getFileNames(), "\n");

    final String msg = String.format("%s was unable to save some project files,\nare you sure you want to close this project anyway?",
                                     ApplicationNamesInfo.getInstance().getProductName());
    return Messages.showDialog(project, msg, "Unsaved Project", "Read-only files:\n\n" + fileNames, new String[]{"Yes", "No"}, 0, 1,
                               Messages.getWarningIcon()) == 0;
  }

  @Override
  public void writeExternal(Element parentNode) throws WriteExternalException {
    if (myDefaultProject != null) {
      myDefaultProject.save();
    }

    if (myDefaultProjectRootElement == null) { //read external isn't called if config folder is absent
      myDefaultProjectRootElement = new Element(ELEMENT_DEFAULT_PROJECT);
    }

    myDefaultProjectRootElement.detach();
    parentNode.addContent(myDefaultProjectRootElement);
  }


  public void setDefaultProjectRootElement(final Element defaultProjectRootElement) {
    myDefaultProjectRootElement = defaultProjectRootElement;
  }

  @Override
  public void readExternal(Element parentNode) throws InvalidDataException {
    myDefaultProjectRootElement = parentNode.getChild(ELEMENT_DEFAULT_PROJECT);

    if (myDefaultProjectRootElement == null) {
      myDefaultProjectRootElement = new Element(ELEMENT_DEFAULT_PROJECT);
    }

    myDefaultProjectRootElement.detach();
  }

  @Override
  public String getExternalFileName() {
    return "project.default";
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "ProjectManager";
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("project.default.settings");
  }
}
