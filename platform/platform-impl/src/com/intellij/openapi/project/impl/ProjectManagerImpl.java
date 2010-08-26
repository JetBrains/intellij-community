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
package com.intellij.openapi.project.impl;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.StateStorage;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManagerListener;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
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
  private static final boolean LOG_PROJECT_LEAKAGE_IN_TESTS = false;
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
  private final List<ProjectManagerListener> myListeners = ContainerUtil.createEmptyCOWList();

  private Project myCurrentTestProject = null;

  private final Map<VirtualFile, byte[]> mySavedCopies = new HashMap<VirtualFile, byte[]>();
  private final TObjectLongHashMap<VirtualFile> mySavedTimestamps = new TObjectLongHashMap<VirtualFile>();
  private final Map<Project, List<Pair<VirtualFile, StateStorage>>> myChangedProjectFiles = new HashMap<Project, List<Pair<VirtualFile, StateStorage>>>();
  private final Alarm myChangedFilesAlarm = new Alarm();
  private final List<Pair<VirtualFile, StateStorage>> myChangedApplicationFiles = new ArrayList<Pair<VirtualFile, StateStorage>>();
  private final AtomicInteger myReloadBlockCount = new AtomicInteger(0);
  private final Map<Project, String> myProjects = new WeakHashMap<Project, String>();
  private static final int MAX_LEAKY_PROJECTS = 42;

  private static ProjectManagerListener[] getListeners(Project project) {
    List<ProjectManagerListener> array = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (array == null) return ProjectManagerListener.EMPTY_ARRAY;
    return ContainerUtil.toArray(array, new ProjectManagerListener[array.size()]);
  }

  public ProjectManagerImpl(VirtualFileManagerEx virtualFileManagerEx) {
    Application app = ApplicationManager.getApplication();
    MessageBus messageBus = app.getMessageBus();
    MessageBusConnection connection = messageBus.connect(app);
    connection.subscribe(StateStorage.STORAGE_TOPIC, new StateStorage.Listener() {
      public void storageFileChanged(final VirtualFileEvent event, @NotNull final StateStorage storage) {
        VirtualFile file = event.getFile();
        LOG.debug("[RELOAD] Storage file changed: " + file.getPath());
        if (!file.isDirectory() && !(event.getRequestor() instanceof StateStorage.SaveSession)) {
          saveChangedProjectFile(file, null, storage);
        }
      }
    });

    addProjectManagerListener(
      new ProjectManagerListener() {

        public void projectOpened(final Project project) {
          MessageBus messageBus = project.getMessageBus();
          MessageBusConnection connection = messageBus.connect(project);
          connection.subscribe(StateStorage.STORAGE_TOPIC, new StateStorage.Listener() {
            public void storageFileChanged(final VirtualFileEvent event, @NotNull final StateStorage storage) {
              VirtualFile file = event.getFile();
              if (!file.isDirectory() && !(event.getRequestor() instanceof StateStorage.SaveSession)) {
                saveChangedProjectFile(file, project, storage);
              }
            }
          });


          ProjectManagerListener[] listeners = getListeners(project);
          for (ProjectManagerListener listener : listeners) {
            listener.projectOpened(project);
          }
        }

        public void projectClosed(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (ProjectManagerListener listener : listeners) {
            listener.projectClosed(project);
          }
        }

        public boolean canCloseProject(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (ProjectManagerListener listener : listeners) {
            if (!listener.canCloseProject(project)) {
              return false;
            }
          }
          return true;
        }

        public void projectClosing(Project project) {
          ProjectManagerListener[] listeners = getListeners(project);
          for (ProjectManagerListener listener : listeners) {
            listener.projectClosing(project);
          }
        }
      }
    );

    registerExternalProjectFileListener(virtualFileManagerEx);
  }

  public void disposeComponent() {
    Disposer.dispose(myChangedFilesAlarm);
    if (myDefaultProject != null) {
      Disposer.dispose(myDefaultProject);
      myDefaultProject = null;
    }
  }

  public void initComponent() {
  }

  @Nullable
  public Project newProject(final String projectName, String filePath, boolean useDefaultProjectSettings, boolean isDummy) {
    filePath = canonicalize(filePath);

    if (LOG_PROJECT_LEAKAGE_IN_TESTS && ApplicationManager.getApplication().isUnitTestMode()) {
      for (int i = 0; i < 42; i++) {
        if (myProjects.size() < MAX_LEAKY_PROJECTS) break;
        System.gc();
        try {
          Thread.sleep(100);
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }

        System.gc();
      }

      if (myProjects.size() >= MAX_LEAKY_PROJECTS) {
        List<Project> copy = new ArrayList<Project>(myProjects.keySet());
        myProjects.clear();
        throw new TooManyProjectLeakedException(copy);
      }
    }

    try {
      ProjectImpl project =
        createAndInitProject(projectName, filePath, false, isDummy, ApplicationManager.getApplication().isUnitTestMode(),
                             useDefaultProjectSettings ? getDefaultProject() : null);
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

  private ProjectImpl createAndInitProject(String projectName, String filePath, boolean isDefault, boolean isDummy, boolean isOptimiseTestLoadSpeed,
                                           @Nullable Project template) throws IOException {
    if (isDummy) {
      throw new UnsupportedOperationException("Dummy project is deprecated and shall not be used anymore.");
    }
    final ProjectImpl project = isDefault ? new DefaultProject(this, filePath, isOptimiseTestLoadSpeed, projectName) :
                                new ProjectImpl(this, filePath, isOptimiseTestLoadSpeed, projectName);

    ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).beforeProjectLoaded(project);

    try {
      if (template != null) {
        project.getStateStore().loadProjectFromTemplate((ProjectImpl)template);
      } else {
        project.getStateStore().load();
      }
    }
    catch (IOException e) {
      scheduleDispose(project);
      throw e;
    }
    catch (final StateStorage.StateStorageException e) {
      scheduleDispose(project);
      throw e;
    }

    project.loadProjectComponents();
    project.init();

    return project;
  }

  private static void scheduleDispose(final ProjectImpl project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Disposer.dispose(project);
      }
    });
  }

  @Nullable
  public Project loadProject(String filePath) throws IOException, JDOMException, InvalidDataException {
    try {
      return doLoadProject(filePath);
    }
    catch (StateStorage.StateStorageException e) {
      throw new IOException(e.getMessage());
    }
  }

  @Nullable
  private Project doLoadProject(String filePath) throws IOException, StateStorage.StateStorageException {
    filePath = canonicalize(filePath);
    ProjectImpl project = null;
    try {
      project = createAndInitProject(null, filePath, false, false, false, null);
    }
    catch (ProcessCanceledException e) {
      if (project != null) {
        scheduleDispose(project);
      }
      throw e;
    }

    return project;
  }

  @Nullable
  protected static String canonicalize(final String filePath) {
    if (filePath == null) return null;
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
  @NotNull
  public synchronized Project getDefaultProject() {
    if (myDefaultProject == null) {
      try {
        myDefaultProject = createAndInitProject(null, null, true, false, ApplicationManager.getApplication().isUnitTestMode(), null);
        myDefaultProjectRootElement = null;
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (StateStorage.StateStorageException e) {
        LOG.error(e);
      }
    }
    return myDefaultProject;
  }


  public Element getDefaultProjectRootElement() {
    return myDefaultProjectRootElement;
  }

  @NotNull
  public Project[] getOpenProjects() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final Project currentTestProject = myCurrentTestProject;
      if (myOpenProjects.isEmpty() && currentTestProject != null && !currentTestProject.isDisposed()) {
        return new Project[] {currentTestProject};
      }
    }
    if (myOpenProjectsArrayCache.length != myOpenProjects.size()) {
      LOG.error("Open projects: "+myOpenProjects+"; cache: "+Arrays.asList(myOpenProjectsArrayCache));
    }
    if (myOpenProjectsArrayCache.length > 0 && myOpenProjectsArrayCache[0] != myOpenProjects.get(0)) {
      LOG.error("Open projects cache corrupted. Open projects: "+myOpenProjects+"; cache: "+Arrays.asList(myOpenProjectsArrayCache));
    }
    return myOpenProjectsArrayCache;
  }

  public boolean isProjectOpened(Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode() && myOpenProjects.isEmpty() && myCurrentTestProject != null) {
      return project == myCurrentTestProject;
    }
    return myOpenProjects.contains(project);
  }

  public boolean openProject(final Project project) {
    if (myOpenProjects.contains(project)) return false;
    if (!ApplicationManager.getApplication().isUnitTestMode() && !((ProjectEx)project).getStateStore().checkVersion()) return false;

    myOpenProjects.add(project);
    cacheOpenProjects();

    fireProjectOpened(project);

    final StartupManagerImpl startupManager = (StartupManagerImpl)StartupManager.getInstance(project);

    boolean ok = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        startupManager.runStartupActivities();
      }
    }, ProjectBundle.message("project.load.progress"), true, project);

    if (!ok) {
      closeProject(project, false);
      notifyProjectOpenFailed();
      return false;
    }

    startupManager.runPostStartupActivities();

    if (!ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode()) {
      // should be invoked last
      StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
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

  public Project loadAndOpenProject(String filePath) throws IOException, JDOMException, InvalidDataException {
    return loadAndOpenProject(filePath, true);
  }

  @Nullable
  protected Project convertAndLoadProject(String filePath, boolean convert) throws IOException {
    return loadProjectWithProgress(filePath);
  }

  @Nullable
  public Project loadAndOpenProject(final String filePath, final boolean convert) throws IOException, JDOMException, InvalidDataException {
    try {

      Project project = convertAndLoadProject(filePath, convert);
      if (project == null) {
        return null;
      }

      if (!openProject(project)) {
        Disposer.dispose(project);
        return null;
      }

      return project;
    }
    catch (StateStorage.StateStorageException e) {
      throw new IOException(e.getMessage());
    }
  }

  @Nullable
  public Project loadProjectWithProgress(final String filePath) throws IOException {
    return loadProjectWithProgress(filePath, null);
  }

  @Nullable
  public Project loadProjectWithProgress(final String filePath, Ref<Boolean> canceled) throws IOException {
    final IOException[] io = {null};
    final StateStorage.StateStorageException[] stateStorage = {null};

    if (filePath != null) {
      refreshProjectFiles(filePath);
    }

    final Project[] project = new Project[1];
    boolean ok = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        try {
          if (indicator != null) {
            indicator.setText(ProjectBundle.message("loading.components.for", filePath));
            indicator.setIndeterminate(true);
          }
          project[0] = doLoadProject(filePath);
        }
        catch (IOException e) {
          io[0] = e;
          return;
        }
        catch (StateStorage.StateStorageException e) {
          stateStorage[0] = e;
          return;
        }

        if (indicator != null) {
          indicator.setText(ProjectBundle.message("initializing.components"));
        }
      }
    }, ProjectBundle.message("project.load.progress"), true, null);

    if (!ok) {
      if (project[0] != null) {
        Disposer.dispose(project[0]);
        project[0] = null;
      }
      if (canceled != null) {
        canceled.set(true);
      }
      notifyProjectOpenFailed();
    }

    if (io[0] != null) throw io[0];
    if (stateStorage[0] != null) throw stateStorage[0];

    if (project[0] == null || !ok) {
      return null;
    }
    return project [0];
  }

  private static void refreshProjectFiles(final String filePath) {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isDispatchThread()) {
      final File file = new File(filePath);
      if (file.isFile()) {
        VirtualFile projectFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
        if (projectFile != null) {
          projectFile.refresh(false, false);
        }

        File iwsFile = new File(file.getParentFile(), FileUtil.getNameWithoutExtension(file) + WorkspaceFileType.DOT_DEFAULT_EXTENSION);
        VirtualFile wsFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(iwsFile);
        if (wsFile != null) {
          wsFile.refresh(false, false);
        }

      }
      else {
        VirtualFile projectConfigDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(filePath, Project.DIRECTORY_STORE_FOLDER));
        if (projectConfigDir != null && projectConfigDir.isDirectory()) {
          projectConfigDir.getChildren();
          if (projectConfigDir instanceof NewVirtualFile) {
            ((NewVirtualFile)projectConfigDir).markDirtyRecursively();
          }
          projectConfigDir.refresh(false, true);

        }
      }
    }
  }

  private static void notifyProjectOpenFailed() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(AppLifecycleListener.TOPIC).projectOpenFailed();
  }

  private void registerExternalProjectFileListener(VirtualFileManagerEx virtualFileManager) {
    virtualFileManager.addVirtualFileManagerListener(new VirtualFileManagerListener() {
      public void beforeRefreshStart(boolean asynchonous) {
      }

      public void afterRefreshFinish(boolean asynchonous) {
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
        reloadProjectImpl(projectToReload, false, false);
      }
    }

  }

  private boolean tryToReloadApplication(){
    try {
      final Application app = ApplicationManager.getApplication();

      if (app.isDisposed()) return false;
      final HashSet<Pair<VirtualFile, StateStorage>> causes = new HashSet<Pair<VirtualFile, StateStorage>>(myChangedApplicationFiles);
      if (causes.isEmpty()) return true;

      final boolean[] reloadOk = {false};
      final LinkedHashSet<String> components = new LinkedHashSet<String>();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            reloadOk[0] = ((ApplicationImpl)app).getStateStore().reload(causes, components);
          }
          catch (StateStorage.StateStorageException e) {
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
                                     "Application Configuration Reload", Messages.getQuestionIcon()) == 0) {
          for (Pair<VirtualFile, StateStorage> cause : causes) {
            StateStorage stateStorage = cause.getSecond();
            if (stateStorage instanceof XmlElementStorage) {
              ((XmlElementStorage)stateStorage).disableSaving();
            }
          }
          if (canRestart) {
            ApplicationManagerEx.getApplicationEx().restart();
          }
          else {
            ApplicationManagerEx.getApplicationEx().exit(true);
          }
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
      public void run() {
        try {
          LOG.debug("[RELOAD] Reloading project/components...");
          reloadOk[0] = ((ProjectEx)project).getStateStore().reload(causes);
        }
        catch (StateStorage.StateStorageException e) {
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
          if (!first) filesBuilder.append("\n");
          first = false;
          filesBuilder.append(url);
          alreadyShown.add(url);
        }
      }
      message = ProjectBundle.message("project.reload.external.change.multiple", filesBuilder.toString());
    }

    return Messages.showYesNoDialog(project, message, ProjectBundle.message("project.reload.external.change.title"), Messages.getQuestionIcon()) == 0;
  }

  public boolean isFileSavedToBeReloaded(VirtualFile candidate) {
    return mySavedCopies.containsKey(candidate);
  }

  public void blockReloadingProjectOnExternalChanges() {
    myReloadBlockCount.incrementAndGet();
  }

  public void unblockReloadingProjectOnExternalChanges() {
    if (myReloadBlockCount.decrementAndGet() == 0) scheduleReloadApplicationAndProject();
  }

  private void scheduleReloadApplicationAndProject() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (!tryToReloadApplication()) return;
        askToReloadProjectIfConfigFilesChangedExternally();
      }

    }, ModalityState.NON_MODAL);

  }

  public void setCurrentTestProject(@Nullable final Project project) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myCurrentTestProject = project;
  }

  @Nullable
  public Project getCurrentTestProject() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return myCurrentTestProject;
  }

  public void saveChangedProjectFile(final VirtualFile file, final Project project) {
    if (file.exists()) {
      copyToTemp(file);
    }
    registerProjectToReload(project, file, null);
  }

  private void saveChangedProjectFile(final VirtualFile file, final Project project, final StateStorage storage) {
    if (file.exists()) {
      copyToTemp(file);
    }
    registerProjectToReload(project, file, storage);
  }

  private void registerProjectToReload(final Project project, final VirtualFile cause, final StateStorage storage) {
    LOG.debug("[RELOAD] Registering project to reload.");

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

  public void reloadProject(final Project p) {
    reloadProjectImpl(p, true, false);
  }

  public void reloadProjectImpl(final Project p, final boolean clearCopyToRestore, boolean takeMemorySnapshot) {
    if (clearCopyToRestore) {
      mySavedCopies.clear();
      mySavedTimestamps.clear();
    }
    reloadProject(p, takeMemorySnapshot);
  }

  public void reloadProject(@NotNull Project p, final boolean takeMemorySnapshot) {
    final Project[] project = {p};

    ProjectReloadState.getInstance(project[0]).onBeforeAutomaticProjectReload();
    final Application application = ApplicationManager.getApplication();

    application.invokeLater(new Runnable() {
      public void run() {
        LOG.debug("Reloading project.");
        ProjectImpl projectImpl = (ProjectImpl)project[0];
        if (projectImpl.isDisposed()) return;
        IProjectStore projectStore = projectImpl.getStateStore();
        final String location = projectImpl.getLocation();

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

        if (project[0].isDisposed() || ProjectUtil.closeProject(project[0])) {
          application.runWriteAction(new Runnable() {
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

  /*
  public boolean isOpeningProject() {
    return myCountOfProjectsBeingOpen > 0;
  }
  */

  public boolean closeProject(final Project project) {
    return closeProject(project, true);
  }

  private boolean closeProject(final Project project, final boolean save) {
    if (!isProjectOpened(project)) return true;
    if (!canClose(project)) return false;

    final ShutDownTracker shutDownTracker = ShutDownTracker.getInstance();
    shutDownTracker.registerStopperThread(Thread.currentThread());
    try {
      if (save) {
        FileDocumentManager.getInstance().saveAllDocuments();
        project.save();
      }
      fireProjectClosing(project);

      myOpenProjects.remove(project);
      cacheOpenProjects();

      myChangedProjectFiles.remove(project);
      fireProjectClosed(project);
    }
    finally {
      shutDownTracker.unregisterStopperThread(Thread.currentThread());
    }

    return true;
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

  public void addProjectManagerListener(ProjectManagerListener listener) {
    myListeners.add(listener);
  }

  public void removeProjectManagerListener(ProjectManagerListener listener) {
    boolean removed = myListeners.remove(listener);
    LOG.assertTrue(removed);
  }

  public void addProjectManagerListener(Project project, ProjectManagerListener listener) {
    List<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (listeners == null) {
      listeners = new ArrayList<ProjectManagerListener>();
      project.putUserData(LISTENERS_IN_PROJECT_KEY, listeners);
    }
    listeners.add(listener);
  }

  public void removeProjectManagerListener(Project project, ProjectManagerListener listener) {
    List<ProjectManagerListener> listeners = project.getUserData(LISTENERS_IN_PROJECT_KEY);
    if (listeners != null) {
      boolean removed = listeners.remove(listener);
      LOG.assertTrue(removed);
    }
    else {
      LOG.assertTrue(false);
    }
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

  public boolean canClose(Project project) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: canClose()");
    }

    for (ProjectManagerListener listener : myListeners) {
      try {
        if (!listener.canCloseProject(project)) return false;
      } catch (Throwable e) {
        LOG.warn(e); // DO NOT LET ANY PLUGIN to prevent closing due to exception
      }
    }

    return true;
  }

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

  public void readExternal(Element parentNode) throws InvalidDataException {
    myDefaultProjectRootElement = parentNode.getChild(ELEMENT_DEFAULT_PROJECT);

    if (myDefaultProjectRootElement == null) {
      myDefaultProjectRootElement = new Element(ELEMENT_DEFAULT_PROJECT);
    }

    myDefaultProjectRootElement.detach();
  }

  public String getExternalFileName() {
    return "project.default";
  }

  @NotNull
  public String getComponentName() {
    return "ProjectManager";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("project.default.settings");
  }
}
