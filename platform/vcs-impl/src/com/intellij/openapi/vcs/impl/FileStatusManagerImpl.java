// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FileStatusManagerImpl extends FileStatusManager implements Disposable {
  private final Project myProject;

  private final List<FileStatusListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final Map<VirtualFile, FileStatus> myCachedStatuses = Collections.synchronizedMap(new HashMap<>());
  private final Map<VirtualFile, Boolean> myWhetherExactlyParentToChanged = Collections.synchronizedMap(new HashMap<>());

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

    MessageBusConnection projectBus = project.getMessageBus().connect();
    projectBus.subscribe(EditorColorsManager.TOPIC, __ -> fileStatusesChanged());
    projectBus.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this::fileStatusesChanged);
    projectBus.subscribe(ChangeListListener.TOPIC, new MyChangeListListener());

    FileStatusProvider.EP_NAME.addChangeListener(myProject, this::fileStatusesChanged, project);

    StartupManager.getInstance(project).runAfterOpened(this::fileStatusesChanged);
  }

  private class MyChangeListListener implements ChangeListListener {
    @Override
    public void changeListAdded(ChangeList list) {
      fileStatusesChanged();
    }

    @Override
    public void changeListRemoved(ChangeList list) {
      fileStatusesChanged();
    }

    @Override
    public void changeListUpdateDone() {
      fileStatusesChanged();
    }
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
        FileStatusManagerImpl manager = (FileStatusManagerImpl)project.getServiceIfCreated(FileStatusManager.class);
        if (manager != null) manager.refreshFileStatusFromDocument(file);
      }
    }
  }

  private @NotNull FileStatus calcStatus(@NotNull VirtualFile virtualFile) {
    for (FileStatusProvider extension : FileStatusProvider.EP_NAME.getExtensions(myProject)) {
      FileStatus status = extension.getFileStatus(virtualFile);
      if (status != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("File status for file [%s] from provider %s: %s", virtualFile, extension.getClass().getName(), status));
        }
        return status;
      }
    }

    if (virtualFile.isInLocalFileSystem()) {
      FileStatus status = getVcsFileStatus(virtualFile);
      if (status != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(String.format("File status for file [%s] from vcs provider: %s", virtualFile, status));
        }
        return status;
      }
    }

    if (virtualFile.isValid() && virtualFile.is(VFileProperty.SPECIAL)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Default ignored status for special file [%s]", virtualFile));
      }
      return FileStatus.IGNORED;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Default not_changed status for file [%s]", virtualFile));
    }
    return FileStatus.NOT_CHANGED;
  }

  private @Nullable FileStatus getVcsFileStatus(@NotNull VirtualFile virtualFile) {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
    if (vcs == null) {
      if (ScratchUtil.isScratch(virtualFile)) {
        return FileStatus.SUPPRESSED; // do not use for vcs-tracked scratched files
      }
      return null;
    }

    FileStatus status = ChangeListManager.getInstance(myProject).getStatus(virtualFile);
    if (status != FileStatus.NOT_CHANGED) return status;

    if (isDocumentModified(virtualFile)) {
      return FileStatus.MODIFIED;
    }

    return null;
  }

  @Nullable
  private Boolean calcDirectoryStatus(@NotNull VirtualFile virtualFile) {
    if (!VcsConfiguration.getInstance(myProject).SHOW_DIRTY_RECURSIVELY) {
      return null;
    }

    ThreeState state = ChangeListManager.getInstance(myProject).haveChangesUnder(virtualFile);
    if (ThreeState.YES.equals(state)) {
      return Boolean.TRUE; // an immediate child is modified
    }
    else if (ThreeState.UNSURE.equals(state)) {
      return Boolean.FALSE; // some child is modified
    }
    else {
      return null; // no modified files inside
    }
  }

  private static boolean isDocumentModified(VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) return false;
    return FileDocumentManager.getInstance().isFileModified(virtualFile);
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

  @Override
  public void fileStatusChanged(final VirtualFile file) {
    final Application application = ApplicationManager.getApplication();
    if (!application.isDispatchThread() && !application.isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(() -> fileStatusChanged(file));
      return;
    }

    if (file == null || !file.isValid()) return;
    FileStatus cachedStatus = myCachedStatuses.get(file);
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

  private void cacheChangedFileStatus(final VirtualFile virtualFile, final FileStatus fs) {
    myCachedStatuses.put(virtualFile, fs);

    Boolean isParentToChanged = FileStatus.NOT_CHANGED.equals(fs)
                                ? calcDirectoryStatus(virtualFile)
                                : null;
    myWhetherExactlyParentToChanged.put(virtualFile, isParentToChanged);
  }

  @Override
  public @NotNull FileStatus getStatus(@NotNull final VirtualFile file) {
    if (file.getFileSystem() instanceof NonPhysicalFileSystem) {
      return FileStatus.SUPPRESSED;  // do not leak light files via cache
    }

    FileStatus status = myCachedStatuses.get(file);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Cached status for file [" + file + "] is " + status);
    }
    if (status == null || status == FileStatusNull.INSTANCE) {
      status = calcStatus(file);
      cacheChangedFileStatus(file, status);
    }

    return status;
  }

  @NotNull
  @Override
  public FileStatus getRecursiveStatus(@NotNull VirtualFile file) {
    FileStatus status = getStatus(file);
    if (status != FileStatus.NOT_CHANGED) {
      return status;
    }

    if (file.isValid() && file.isDirectory()) {
      Boolean immediate = myWhetherExactlyParentToChanged.get(file);
      if (immediate == null) return FileStatus.NOT_CHANGED;
      return immediate ? FileStatus.NOT_CHANGED_IMMEDIATE
                       : FileStatus.NOT_CHANGED_RECURSIVE;
    }

    return FileStatus.NOT_CHANGED;
  }

  @RequiresEdt
  private void refreshFileStatusFromDocument(@NotNull VirtualFile file) {
    if (LOG.isDebugEnabled()) {
      Document document = FileDocumentManager.getInstance().getDocument(file);
      LOG.debug("refreshFileStatusFromDocument: file.getModificationStamp()=" + file.getModificationStamp() +
                ", document.getModificationStamp()=" + (document != null ? document.getModificationStamp() : null));
    }

    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
    if (vcs == null) return;

    FileStatus cachedStatus = myCachedStatuses.get(file);
    boolean isDocumentModified = isDocumentModified(file);

    if (cachedStatus == FileStatus.MODIFIED && !isDocumentModified) {
      if (!((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject)).getState().SHOW_DIALOG) {
        RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
        if (rollbackEnvironment != null) {
          rollbackEnvironment.rollbackIfUnchanged(file);
        }
      }
    }

    boolean isStatusChanged = cachedStatus != null && cachedStatus != FileStatus.NOT_CHANGED;
    if (isStatusChanged != isDocumentModified) {
      fileStatusChanged(file);
    }

    ChangeProvider cp = vcs.getChangeProvider();
    if (cp != null && cp.isModifiedDocumentTrackingRequired()) {
      FileStatus status = ChangeListManager.getInstance(myProject).getStatus(file);
      boolean isClmStatusChanged = status != FileStatus.NOT_CHANGED;
      if (isClmStatusChanged != isDocumentModified) {
        VcsDirtyScopeManager.getInstance(myProject).fileDirty(file);
      }
    }
  }

  @Override
  public void removeFileStatusListener(@NotNull FileStatusListener listener) {
    myListeners.remove(listener);
  }
}
