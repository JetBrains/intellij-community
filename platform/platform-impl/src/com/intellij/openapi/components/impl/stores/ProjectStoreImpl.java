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
package com.intellij.openapi.components.impl.stores;

import com.intellij.CommonBundle;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.io.fs.FileSystem;
import com.intellij.util.io.fs.IFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class ProjectStoreImpl extends BaseFileConfigurableStoreImpl implements IProjectStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ProjectStoreImpl");
  @NonNls private static final String OLD_PROJECT_SUFFIX = "_old.";
  @NonNls static final String OPTION_WORKSPACE = "workspace";

  protected ProjectEx myProject;

  @NonNls static final String PROJECT_FILE_MACRO = "PROJECT_FILE";
  @NonNls static final String WS_FILE_MACRO = "WORKSPACE_FILE";
  @NonNls private static final String PROJECT_CONFIG_DIR = "PROJECT_CONFIG_DIR";

  static final String PROJECT_FILE_STORAGE = "$" + PROJECT_FILE_MACRO + "$";
  static final String WS_FILE_STORAGE = "$" + WS_FILE_MACRO + "$";
  static final String DEFAULT_STATE_STORAGE = PROJECT_FILE_STORAGE;

  static final Storage DEFAULT_STORAGE_ANNOTATION = new MyStorage();
  private static int originalVersion = -1;

  private StorageScheme myScheme = StorageScheme.DEFAULT;
  private String myCachedLocation;

  ProjectStoreImpl(final ProjectEx project) {
    super(project);
    myProject = project;
  }

  public boolean checkVersion() {
    final ApplicationNamesInfo appNamesInfo = ApplicationNamesInfo.getInstance();
    if (originalVersion >= 0 && originalVersion < ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
      final VirtualFile projectFile = getProjectFile();
      LOG.assertTrue(projectFile != null);
      String name = projectFile.getNameWithoutExtension();

      String message = ProjectBundle.message("project.convert.old.prompt", projectFile.getName(),
                                             appNamesInfo.getProductName(),
                                             name + OLD_PROJECT_SUFFIX + projectFile.getExtension());
      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != 0) return false;

      final ArrayList<String> conversionProblems = getConversionProblemsStorage();
      if (conversionProblems != null && !conversionProblems.isEmpty()) {
        StringBuilder buffer = new StringBuilder();
        buffer.append(ProjectBundle.message("project.convert.problems.detected"));
        for (String s : conversionProblems) {
          buffer.append('\n');
          buffer.append(s);
        }
        buffer.append(ProjectBundle.message("project.convert.problems.help"));
        final int result = Messages.showDialog(myProject, buffer.toString(), ProjectBundle.message("project.convert.problems.title"),
                                               new String[]{ProjectBundle.message("project.convert.problems.help.button"),
                                                 CommonBundle.getCloseButtonText()}, 0, Messages.getWarningIcon());
        if (result == 0) {
          HelpManager.getInstance().invokeHelp("project.migrationProblems");
        }
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            VirtualFile projectDir = projectFile.getParent();
            assert projectDir != null;

            backup(projectDir, projectFile);

            VirtualFile workspaceFile = getWorkspaceFile();
            if (workspaceFile != null) {
              backup(projectDir, workspaceFile);
            }
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }

        private void backup(final VirtualFile projectDir, final VirtualFile vile) throws IOException {
          final String oldName = vile.getNameWithoutExtension() + OLD_PROJECT_SUFFIX + vile.getExtension();
          VirtualFile oldFile = projectDir.findOrCreateChildData(this, oldName);
          VfsUtil.saveText(oldFile, VfsUtil.loadText(vile));
        }

      });
    }

    if (originalVersion > ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
      String message =
        ProjectBundle.message("project.load.new.version.warning", myProject.getName(), appNamesInfo.getProductName());

      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != 0) return false;
    }

    return true;
  }

  public TrackingPathMacroSubstitutor[] getSubstitutors() {
    return new TrackingPathMacroSubstitutor[] {getStateStorageManager().getMacroSubstitutor()};
  }

  @Override
  protected boolean optimizeTestLoading() {
    return myProject.isOptimiseTestLoadSpeed();
  }

  @Override
  protected Project getProject() {
    return myProject;
  }

  public void setProjectFilePath(final String filePath) {
    if (filePath == null) {
      return;
    }
    final IFile iFile = FileSystem.FILE_SYSTEM.createFile(filePath);
    final IFile dir_store =
      iFile.isDirectory() ? iFile.getChild(Project.DIRECTORY_STORE_FOLDER) : iFile.getParentFile().getChild(Project.DIRECTORY_STORE_FOLDER);

    final StateStorageManager stateStorageManager = getStateStorageManager();
    if (dir_store.exists() && iFile.isDirectory()) {
      FileBasedStorage.syncRefreshPathRecursively(dir_store.getPath(), null);

      myScheme = StorageScheme.DIRECTORY_BASED;

      stateStorageManager.addMacro(PROJECT_FILE_MACRO, dir_store.getChild("misc.xml").getPath());
      final IFile ws = dir_store.getChild("workspace.xml");
      stateStorageManager.addMacro(WS_FILE_MACRO, ws.getPath());

      if (!ws.exists() && !iFile.isDirectory()) {
        useOldWsContent(filePath, ws);
      }

      stateStorageManager.addMacro(PROJECT_CONFIG_DIR, dir_store.getPath());
    }
    else {
      myScheme = StorageScheme.DEFAULT;
      stateStorageManager.addMacro(PROJECT_FILE_MACRO, filePath);

      LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);

      int lastDot = filePath.lastIndexOf(".");
      final String filePathWithoutExt = lastDot > 0 ? filePath.substring(0, lastDot) : filePath;
      String workspacePath = filePathWithoutExt + WorkspaceFileType.DOT_DEFAULT_EXTENSION;

      LocalFileSystem.getInstance().refreshAndFindFileByPath(workspacePath);
      stateStorageManager.addMacro(WS_FILE_MACRO, workspacePath);
    }

    myCachedLocation = null;
  }

  private static void useOldWsContent(final String filePath, final IFile ws) {
    int lastDot = filePath.lastIndexOf(".");
    final String filePathWithoutExt = lastDot > 0 ? filePath.substring(0, lastDot) : filePath;
    String workspacePath = filePathWithoutExt + WorkspaceFileType.DOT_DEFAULT_EXTENSION;
    IFile oldWs = FileSystem.FILE_SYSTEM.createFile(workspacePath);
    if (oldWs.exists()) {
      try {
        final InputStream is = oldWs.openInputStream();
        final byte[] bytes;

        try {
          bytes = FileUtil.loadBytes(is, (int)oldWs.length());
        }
        finally {
          is.close();
        }

        final OutputStream os = ws.openOutputStream();
        try {
          os.write(bytes);
        }
        finally {
          os.close();
        }

      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Nullable
  public VirtualFile getProjectBaseDir() {
    final VirtualFile projectFile = getProjectFile();
    if (projectFile != null) return myScheme == StorageScheme.DEFAULT ? projectFile.getParent() : projectFile.getParent().getParent();

    //we are not yet initialized completely
    final StateStorage s = getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    if (!(s instanceof FileBasedStorage)) return null;
    final FileBasedStorage storage = (FileBasedStorage)s;

    final IFile file = storage.getFile();
    if (file == null) return null;

    return LocalFileSystem.getInstance()
      .findFileByIoFile(myScheme == StorageScheme.DEFAULT ? file.getParentFile() : file.getParentFile().getParentFile());
  }

  @Nullable
  public String getLocation() {
    if (myCachedLocation == null) {
      if (myScheme == StorageScheme.DEFAULT) {
        myCachedLocation = getProjectFilePath();
      }
      else {
        final VirtualFile baseDir = getProjectBaseDir();
        myCachedLocation = baseDir == null ? null : baseDir.getPath();
      }
    }

    return myCachedLocation;
  }

  @NotNull
  public String getProjectName() {
    if (myScheme == StorageScheme.DIRECTORY_BASED) {
      final VirtualFile baseDir = getProjectBaseDir();
      assert baseDir != null : "project file: " + (getProjectFile() == null ? "[NULL]" : getProjectFile().getPath());
      return baseDir.getName().replace(":", "");
    }

    String temp = getProjectFileName();
    if (temp.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      temp = temp.substring(0, temp.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length());
    }
    final int i = temp.lastIndexOf(File.separatorChar);
    if (i >= 0) {
      temp = temp.substring(i + 1, temp.length() - i + 1);
    }
    return temp;
  }

  @NotNull
  public StorageScheme getStorageScheme() {
    return myScheme;
  }

  @Nullable
  public String getPresentableUrl() {
    if (myProject.isDefault()) return null;
    if (myScheme == StorageScheme.DIRECTORY_BASED) {
      final VirtualFile baseDir = getProjectBaseDir();
      return baseDir != null ? baseDir.getPresentableUrl() : null;
    }
    else {
      if (myProject.isDefault()) return null;
      final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
      assert storage != null;
      return storage.getFilePath().replace('/', File.separatorChar);
    }
  }

  public void loadProject() throws IOException, JDOMException, InvalidDataException, StateStorage.StateStorageException {
    //load();
    myProject.init();
  }

  @Nullable
  public VirtualFile getProjectFile() {
    if (myProject.isDefault()) return null;
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    assert storage != null;
    return storage.getVirtualFile();
  }

  @Nullable
  public VirtualFile getWorkspaceFile() {
    if (myProject.isDefault()) return null;
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(WS_FILE_STORAGE);
    assert storage != null;
    return storage.getVirtualFile();
  }

  public void loadProjectFromTemplate(final ProjectImpl defaultProject) {
    final StateStorage stateStorage = getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);

    assert stateStorage instanceof XmlElementStorage;
    XmlElementStorage xmlElementStorage = (XmlElementStorage)stateStorage;

    defaultProject.save();
    final IProjectStore projectStore = defaultProject.getStateStore();
    assert projectStore instanceof DefaultProjectStoreImpl;
    DefaultProjectStoreImpl defaultProjectStore = (DefaultProjectStoreImpl)projectStore;
    final Element element = defaultProjectStore.getStateCopy();
    if (element != null) {
      xmlElementStorage.setDefaultState(element);
    }
  }

  @NotNull
  public String getProjectFileName() {
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    assert storage != null;
    return storage.getFileName();
  }

  @NotNull
  public String getProjectFilePath() {
    if (myProject.isDefault()) return "";

    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    assert storage != null;
    return storage.getFilePath();
  }

  protected XmlElementStorage getMainStorage() {
    final XmlElementStorage storage = (XmlElementStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
    assert storage != null;
    return storage;
  }

  protected StateStorageManager createStateStorageManager() {
    return new ProjectStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(), myProject);
  }


  static class  ProjectStorageData extends BaseStorageData {
    protected final Project myProject;

    ProjectStorageData(final String rootElementName, Project project) {
      super(rootElementName);
      myProject = project;
    }

    protected ProjectStorageData(ProjectStorageData storageData) {
      super(storageData);
      myProject = storageData.myProject;
    }

    public XmlElementStorage.StorageData clone() {
      return new ProjectStorageData(this);
    }
  }

  static class WsStorageData extends ProjectStorageData {

    WsStorageData(final String rootElementName, final Project project) {
      super(rootElementName, project);
    }

    WsStorageData(final WsStorageData storageData) {
      super(storageData);
    }

    public XmlElementStorage.StorageData clone() {
      return new WsStorageData(this);
    }
  }

  static class IprStorageData extends ProjectStorageData {
    IprStorageData(final String rootElementName, Project project) {
      super(rootElementName, project);
    }

    IprStorageData(final IprStorageData storageData) {
      super(storageData);
    }

    protected void load(@NotNull final Element root) throws IOException {
      final String v = root.getAttributeValue(VERSION_OPTION);
      originalVersion = v != null ? Integer.parseInt(v) : 0;

      if (originalVersion != ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
        convert(root, originalVersion);
      }

      super.load(root);
    }

    protected void convert(final Element root, final int originalVersion) {
    }

    public XmlElementStorage.StorageData clone() {
      return new IprStorageData(this);
    }
  }

  protected SaveSessionImpl createSaveSession() throws StateStorage.StateStorageException {
    return new ProjectSaveSession();
  }

  protected class ProjectSaveSession extends SaveSessionImpl {

    ProjectSaveSession() throws StateStorage.StateStorageException {
    }

    public List<IFile> getAllStorageFilesToSave(final boolean includingSubStructures) throws IOException {
      List<IFile> result = new ArrayList<IFile>();

      if (includingSubStructures) {
        collectSubfilesToSave(result);
      }

      result.addAll(super.getAllStorageFilesToSave(false));

      return result;
    }

    protected void collectSubfilesToSave(final List<IFile> result) throws IOException { }

    public SaveSession save() throws IOException {
      final ReadonlyStatusHandler.OperationStatus operationStatus = ensureConfigFilesWritable();
      if (operationStatus == null) {
        throw new IOException();
      }
      else if (operationStatus.hasReadonlyFiles()) {
        MessagesEx.error(myProject, ProjectBundle.message("project.save.error", operationStatus.getReadonlyFilesMessage())).showLater();
        throw new SaveCancelledException();
      }

      beforeSave();

      super.save();

      return this;
    }

    protected void beforeSave() throws IOException {
    }

    private ReadonlyStatusHandler.OperationStatus ensureConfigFilesWritable() {
      return ApplicationManager.getApplication().runReadAction(new Computable<ReadonlyStatusHandler.OperationStatus>() {
        public ReadonlyStatusHandler.OperationStatus compute() {
          final List<IFile> filesToSave;
          try {
            filesToSave = getAllStorageFilesToSave(true);
          }
          catch (IOException e) {
            LOG.error(e);
            return null;
          }

          List<VirtualFile> readonlyFiles = new ArrayList<VirtualFile>();

          for (IFile file : filesToSave) {
            final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);

            if (virtualFile != null) {
              virtualFile.refresh(false, false);
              if (virtualFile.isValid() && !virtualFile.isWritable()) readonlyFiles.add(virtualFile);
            }
          }

          return ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(readonlyFiles);
        }
      });
    }
  }


  private final StateStorageChooser myStateStorageChooser = new StateStorageChooser() {
    public Storage[] selectStorages(final Storage[] storages, final Object component, final StateStorageOperation operation) {
      if (operation == StateStorageOperation.READ) {
        OrderedSet<Storage> result = new OrderedSet<Storage>();

        for (Storage storage : storages) {
          if (storage.scheme() == myScheme) {
            result.add(0, storage);
          }
        }

        for (Storage storage : storages) {
          if (storage.scheme() == StorageScheme.DEFAULT) {
            result.add(storage);
          }
        }

        return result.toArray(new Storage[result.size()]);
      }
      else if (operation == StateStorageOperation.WRITE) {
        List<Storage> result = new ArrayList<Storage>();
        for (Storage storage : storages) {
          if (storage.scheme() == myScheme) {
            result.add(storage);
          }
        }

        if (!result.isEmpty()) return result.toArray(new Storage[result.size()]);

        for (Storage storage : storages) {
          if (storage.scheme() == StorageScheme.DEFAULT) {
            result.add(storage);
          }
        }

        return result.toArray(new Storage[result.size()]);
      }

      return new Storage[]{};
    }
  };

  @Nullable
  protected StateStorageChooser getDefaultStateStorageChooser() {
    return myStateStorageChooser;
  }

  @NotNull
  protected <T> Storage[] getComponentStorageSpecs(@NotNull final PersistentStateComponent<T> persistentStateComponent, final StateStorageOperation operation) throws StateStorage.StateStorageException {
    Storage[] result = super.getComponentStorageSpecs(persistentStateComponent, operation);

    if (operation == StateStorageOperation.READ) {
      Storage[] upd = new Storage[result.length + 1];
      System.arraycopy(result, 0, upd, 0, result.length);
      upd[result.length] = DEFAULT_STORAGE_ANNOTATION;
      result = upd;
    }

    return result;
  }

  private static class MyStorage implements Storage {
    public String id() {
      return "___Default___";
    }

    public boolean isDefault() {
      return true;
    }

    public String file() {
      return DEFAULT_STATE_STORAGE;
    }

    public StorageScheme scheme() {
      return  StorageScheme.DEFAULT;
    }

    public Class<? extends StateStorage> storageClass() {
      return StorageAnnotationsDefaultValues.NullStateStorage.class;
    }

    public Class<? extends StateSplitter> stateSplitter() {
      return StorageAnnotationsDefaultValues.NullStateSplitter.class;
    }

    public Class<? extends Annotation> annotationType() {
      throw new UnsupportedOperationException("Method annotationType not implemented in " + getClass());
    }
  }

  public boolean reload(final Set<Pair<VirtualFile, StateStorage>> changedFiles) throws IOException, StateStorage.StateStorageException {
    final SaveSession saveSession = startSave();

    final Set<String> componentNames;
    try {
      componentNames = saveSession.analyzeExternalChanges(changedFiles);
      if (componentNames == null) return false;

      // TODO[mike]: This is a hack to prevent NPE (assert != null) in StateStorageManagerImpl.reload, storage is null for...
      for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
        if (pair.second == null) {
          return false;
        }
      }

      if (!componentNames.isEmpty()) {
        StorageUtil.logStateDiffInfo(changedFiles, componentNames);        
      }

      if (!isReloadPossible(componentNames)) {
        return false;
      }
    }
    finally {
      finishSave(saveSession);
    }

    if (!componentNames.isEmpty()) {
      myProject.getMessageBus().syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateStarted();

      try {
        doReload(changedFiles, componentNames);
        reinitComponents(componentNames, false);
      }
      finally {
        myProject.getMessageBus().syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateFinished();
      }
    }


    return true;
  }


}

