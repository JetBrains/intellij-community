// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
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
import com.intellij.util.Alarm;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FileStatusManagerImpl extends FileStatusManager implements Disposable {
  private final Project myProject;
  private final MergingUpdateQueue myQueue;

  private final List<FileStatusListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final Object myDirtyLock = new Object();
  private final Set<VirtualFile> myDirtyStatuses = new HashSet<>();
  private final Object2BooleanMap<VirtualFile> myDirtyDocuments = new Object2BooleanOpenHashMap<>();

  private final Map<VirtualFile, FileStatus> myCachedStatuses = Collections.synchronizedMap(new HashMap<>());
  private final Map<VirtualFile, Boolean> myWhetherExactlyParentToChanged = Collections.synchronizedMap(new HashMap<>());

  public FileStatusManagerImpl(@NotNull Project project) {
    myProject = project;
    myQueue = new MergingUpdateQueue("FileStatusManagerImpl", 100, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD);

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
      if (file == null || !file.isInLocalFileSystem()) {
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
  private Boolean calcDirectoryStatus(@NotNull VirtualFile virtualFile, @NotNull FileStatus status) {
    if (!FileStatus.NOT_CHANGED.equals(status)) return null;
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
    synchronized (myDirtyLock) {
      myCachedStatuses.clear();
      myWhetherExactlyParentToChanged.clear();
      myDirtyStatuses.clear();
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      for (FileStatusListener listener : myListeners) {
        listener.fileStatusesChanged();
      }
    }, ModalityState.any());
  }

  @Override
  public void fileStatusChanged(final VirtualFile file) {
    if (file == null) return;

    if (!file.isValid()) {
      synchronized (myDirtyLock) {
        myCachedStatuses.remove(file);
        myWhetherExactlyParentToChanged.remove(file);
      }
      return;
    }

    FileStatus cachedStatus = myCachedStatuses.get(file);
    if (cachedStatus == null) {
      return;
    }

    synchronized (myDirtyLock) {
      myDirtyStatuses.add(file);
    }

    myQueue.queue(DisposableUpdate.createDisposable(this, "file status update", () -> {
      updateCachedFileStatuses();
    }));
  }

  @RequiresBackgroundThread
  private void updateCachedFileStatuses() {
    List<VirtualFile> toRefresh;
    synchronized (myDirtyLock) {
      toRefresh = new ArrayList<>(myDirtyStatuses);
      myDirtyStatuses.clear();
    }

    List<VirtualFile> updatedFiles = new ArrayList<>();
    for (VirtualFile file : toRefresh) {
      boolean wasUpdated = updateFileStatusFor(file);
      if (wasUpdated) updatedFiles.add(file);
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      for (VirtualFile file : updatedFiles) {
        for (FileStatusListener listener : myListeners) {
          listener.fileStatusChanged(file);
        }
      }
    }, ModalityState.any(), myProject.getDisposed());
  }

  private boolean updateFileStatusFor(@NotNull VirtualFile file) {
    FileStatus newStatus = calcStatus(file);
    FileStatus oldStatus = myCachedStatuses.put(file, newStatus);
    if (oldStatus == newStatus) return false;

    myWhetherExactlyParentToChanged.put(file, calcDirectoryStatus(file, newStatus));
    return true;
  }

  private @NotNull FileStatus initFileStatusFor(@NotNull VirtualFile file) {
    FileStatus newStatus = calcStatus(file);
    myCachedStatuses.put(file, newStatus);

    myWhetherExactlyParentToChanged.put(file, calcDirectoryStatus(file, newStatus));
    return newStatus;
  }

  @Override
  public @NotNull FileStatus getStatus(@NotNull VirtualFile file) {
    if (file.getFileSystem() instanceof NonPhysicalFileSystem) {
      return FileStatus.SUPPRESSED;  // do not leak light files via cache
    }

    FileStatus status = myCachedStatuses.get(file);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Cached status for file [" + file + "] is " + status);
    }
    if (status != null) {
      return status;
    }

    return initFileStatusFor(file);
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
      LOG.debug(String.format("[refreshFileStatusFromDocument] file modificationStamp: %s, document modificationStamp: %s",
                              file.getModificationStamp(),
                              (document != null ? document.getModificationStamp() : null)));
    }

    boolean isDocumentModified = isDocumentModified(file);
    synchronized (myDirtyLock) {
      myDirtyDocuments.put(file, isDocumentModified);
    }

    myQueue.queue(DisposableUpdate.createDisposable(this, "refresh from document", () -> {
      processModifiedDocuments();
    }));
  }

  @RequiresBackgroundThread
  private void processModifiedDocuments() {
    Object2BooleanMap<VirtualFile> toRefresh;
    synchronized (myDirtyLock) {
      toRefresh = new Object2BooleanOpenHashMap<>(myDirtyDocuments);
      myDirtyDocuments.clear();
    }

    toRefresh.forEach((file, isDocumentModified) -> {
      processModifiedDocument(file, isDocumentModified);
    });
  }

  @RequiresBackgroundThread
  private void processModifiedDocument(@NotNull VirtualFile file, boolean isDocumentModified) {
    if (LOG.isDebugEnabled()) {
      Document document = ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(file));
      LOG.debug(String.format("[processModifiedDocument] isModified: %s, file modificationStamp: %s, document modificationStamp: %s",
                              isDocumentModified, file.getModificationStamp(),
                              (document != null ? document.getModificationStamp() : null)));
    }

    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
    if (vcs == null) return;

    FileStatus cachedStatus = myCachedStatuses.get(file);

    if (cachedStatus == FileStatus.MODIFIED && !isDocumentModified) {
      boolean unlockWithPrompt = ((ReadonlyStatusHandlerImpl)ReadonlyStatusHandler.getInstance(myProject)).getState().SHOW_DIALOG;
      if (!unlockWithPrompt) {
        RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
        if (rollbackEnvironment != null) {
          rollbackEnvironment.rollbackIfUnchanged(file);
        }
      }
    }

    if (cachedStatus != null) {
      boolean isStatusChanged = cachedStatus != FileStatus.NOT_CHANGED;
      if (isStatusChanged != isDocumentModified) {
        fileStatusChanged(file);
      }
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

  @TestOnly
  public void waitFor() {
    try {
      myQueue.waitForAllExecuted(10, TimeUnit.SECONDS);

      if (myQueue.isFlushing()) {
        // MUQ.queue() inside Update.run cancels underlying future, and 'waitForAllExecuted' exits prematurely.
        // Workaround this issue by waiting twice
        // This fixes 'processModifiedDocument -> fileStatusChanged' interraction.
        myQueue.waitForAllExecuted(10, TimeUnit.SECONDS);
      }
    }
    catch (ExecutionException | InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
