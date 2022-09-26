// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FileStatusManagerImpl extends FileStatusManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(FileStatusManagerImpl.class);
  private final Map<VirtualFile, FileStatus> myCachedStatuses = Collections.synchronizedMap(new HashMap<>());
  private final Map<VirtualFile, Boolean> myWhetherExactlyParentToChanged = Collections.synchronizedMap(new HashMap<>());
  private final Project myProject;
  private final List<FileStatusListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final VcsFileStatusProvider myFileStatusProvider;

  private static class FileStatusNull implements FileStatus {
    private static final FileStatus INSTANCE = new FileStatusNull();

    @Override
    public String getText() {
      throw new AssertionError("Should not be called");
    }

    @Override
    public Color getColor() {
      throw new AssertionError("Should not be called");
    }

    @NotNull
    @Override
    public ColorKey getColorKey() {
      throw new AssertionError("Should not be called");
    }

    @NotNull
    @Override
    public String getId() {
      throw new AssertionError("Should not be called");
    }
  }

  public FileStatusManagerImpl(@NotNull Project project) {
    myProject = project;
    myFileStatusProvider = VcsFileStatusProvider.getInstance(project);

    MessageBusConnection projectBus = project.getMessageBus().connect();
    projectBus.subscribe(EditorColorsManager.TOPIC, __ -> fileStatusesChanged());
    projectBus.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this::fileStatusesChanged);

    if (!project.isDefault()) {
      StartupManager.getInstance(project).runAfterOpened(this::fileStatusesChanged);
    }

    FileStatusProvider.EP_NAME.addChangeListener(myProject, this::fileStatusesChanged, project);
  }

  static final class FileStatusManagerDocumentListener implements FileDocumentManagerListener, DocumentListener {
    private final Key<Boolean> CHANGED = Key.create("FileStatusManagerDocumentListener.document.changed");

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      Document document = event.getDocument();
      if (document.isInBulkUpdate()) {
        document.putUserData(CHANGED, Boolean.TRUE);
      }
      else {
        refreshFileStatus(document);
      }
    }

    @Override
    public void bulkUpdateFinished(@NotNull Document document) {
      if (document.getUserData(CHANGED) != null) {
        document.putUserData(CHANGED, null);
        refreshFileStatus(document);
      }
    }

    @Override
    public void unsavedDocumentDropped(@NotNull Document document) {
      refreshFileStatus(document);
    }

    private static void refreshFileStatus(@NotNull Document document) {
      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file == null) {
        return;
      }

      ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
      if (projectManager == null) {
        return;
      }

      for (Project project : projectManager.getOpenProjects()) {
        VcsFileStatusProvider.getInstance(project).refreshFileStatusFromDocument(file, document);
      }
    }
  }

  private FileStatus calcStatus(@NotNull final VirtualFile virtualFile) {
    for (FileStatusProvider extension : FileStatusProvider.EP_NAME.getExtensions(myProject)) {
      final FileStatus status = extension.getFileStatus(virtualFile);
      if (status != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("File status for file [%s] from provider %s: %s", virtualFile, extension.getClass().getName(), status));
        }
        return status;
      }
    }

    if (virtualFile.isInLocalFileSystem()) {
      FileStatus status = myFileStatusProvider.getFileStatus(virtualFile);
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("File status for file [%s] from default provider %s: %s", virtualFile, myFileStatusProvider, status));
      }
      return status;
    }

    FileStatus defaultStatus = getDefaultStatus(virtualFile);
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Default status for file [%s]: %s", virtualFile, defaultStatus));
    }
    return defaultStatus;
  }

  @NotNull
  static FileStatus getDefaultStatus(@NotNull final VirtualFile file) {
    return file.isValid() && file.is(VFileProperty.SPECIAL) ? FileStatus.IGNORED : FileStatus.NOT_CHANGED;
  }

  @Override
  public void dispose() {
    myCachedStatuses.clear();
  }

  @Override
  public void addFileStatusListener(@NotNull FileStatusListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addFileStatusListener(@NotNull final FileStatusListener listener, @NotNull Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, () -> myListeners.remove(listener));
  }

  @Override
  public void fileStatusesChanged() {
    if (myProject.isDisposed()) {
      return;
    }
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeLater(() -> fileStatusesChanged(), ModalityState.any());
      return;
    }

    myCachedStatuses.clear();
    myWhetherExactlyParentToChanged.clear();

    for (FileStatusListener listener : myListeners) {
      listener.fileStatusesChanged();
    }
  }

  private void cacheChangedFileStatus(final VirtualFile virtualFile, final FileStatus fs) {
    myCachedStatuses.put(virtualFile, fs);
    if (FileStatus.NOT_CHANGED.equals(fs)) {
      final ThreeState parentingStatus = myFileStatusProvider.getNotChangedDirectoryParentingStatus(virtualFile);
      if (ThreeState.YES.equals(parentingStatus)) {
        myWhetherExactlyParentToChanged.put(virtualFile, true);
      }
      else if (ThreeState.UNSURE.equals(parentingStatus)) {
        myWhetherExactlyParentToChanged.put(virtualFile, false);
      }
    }
    else {
      myWhetherExactlyParentToChanged.remove(virtualFile);
    }
  }

  @Override
  public void fileStatusChanged(final VirtualFile file) {
    final Application application = ApplicationManager.getApplication();
    if (!application.isDispatchThread() && !application.isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(() -> fileStatusChanged(file));
      return;
    }

    if (file == null || !file.isValid()) return;
    FileStatus cachedStatus = getCachedStatus(file);
    if (cachedStatus == FileStatusNull.INSTANCE) {
      return;
    }
    if (cachedStatus == null) {
      cacheChangedFileStatus(file, FileStatusNull.INSTANCE);
      return;
    }
    FileStatus newStatus = calcStatus(file);
    if (cachedStatus == newStatus) return;
    cacheChangedFileStatus(file, newStatus);

    for (FileStatusListener listener : myListeners) {
      listener.fileStatusChanged(file);
    }
  }

  @Override
  public FileStatus getStatus(@NotNull final VirtualFile file) {
    if (file.getFileSystem() instanceof NonPhysicalFileSystem) {
      return FileStatus.SUPPRESSED;  // do not leak light files via cache
    }

    FileStatus status = getCachedStatus(file);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Cached status for file [" + file + "] is " + status);
    }
    if (status == null || status == FileStatusNull.INSTANCE) {
      status = calcStatus(file);
      cacheChangedFileStatus(file, status);
    }

    return status;
  }

  FileStatus getCachedStatus(final VirtualFile file) {
    return myCachedStatuses.get(file);
  }

  @Override
  public void removeFileStatusListener(@NotNull FileStatusListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public Color getNotChangedDirectoryColor(@NotNull VirtualFile file) {
    return getRecursiveStatus(file).getColor();
  }

  @NotNull
  @Override
  public FileStatus getRecursiveStatus(@NotNull VirtualFile file) {
    FileStatus status = super.getRecursiveStatus(file);
    if (status != FileStatus.NOT_CHANGED || !file.isValid() || !file.isDirectory()) return status;
    Boolean immediate = myWhetherExactlyParentToChanged.get(file);
    if (immediate == null) return status;
    return immediate ? FileStatus.NOT_CHANGED_IMMEDIATE : FileStatus.NOT_CHANGED_RECURSIVE;
  }
}
