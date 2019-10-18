// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsIgnoreManager;
import com.intellij.openapi.vcs.changes.ignore.IgnoreFilesProcessorImpl;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.vcsUtil.VcsUtil;
import kotlin.Unit;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.util.Collections.emptyMap;

public abstract class VcsVFSListener implements Disposable {
  protected static final Logger LOG = Logger.getInstance(VcsVFSListener.class);

  private final ProjectLevelVcsManager myVcsManager;
  private final VcsIgnoreManager myVcsIgnoreManager;
  private final VcsFileListenerContextHelper myVcsFileListenerContextHelper;

  protected static class MovedFileInfo {
    @NotNull
    public final String myOldPath;
    @NotNull
    public String myNewPath;
    @NotNull
    private final VirtualFile myFile;

    MovedFileInfo(@NotNull VirtualFile file, @NotNull String newPath) {
      myOldPath = file.getPath();
      myNewPath = newPath;
      myFile = file;
    }

    @Override
    public String toString() {
      return String.format("MovedFileInfo{[%s] -> [%s]}", myOldPath, myNewPath);
    }
  }

  protected static class AllDeletedFiles {
    public final List<FilePath> deletedFiles;
    public final List<FilePath> deletedWithoutConfirmFiles;

    public AllDeletedFiles(@NotNull List<FilePath> deletedFiles, @NotNull List<FilePath> deletedWithoutConfirmFiles) {
      this.deletedFiles = deletedFiles;
      this.deletedWithoutConfirmFiles = deletedWithoutConfirmFiles;
    }
  }

  protected final Project myProject;
  protected final AbstractVcs myVcs;
  protected final ChangeListManager myChangeListManager;
  private final VcsShowConfirmationOption myAddOption;
  protected final VcsShowConfirmationOption myRemoveOption;
  protected final StateProcessor myProcessor = new StateProcessor();
  private final ProjectConfigurationFilesProcessorImpl myProjectConfigurationFilesProcessor;
  protected final ExternallyAddedFilesProcessorImpl myExternalFilesProcessor;
  private final List<VFileEvent> myEventsToProcess = new SmartList<>();

  protected enum VcsDeleteType {SILENT, CONFIRM, IGNORE}

  protected final class StateProcessor {
    private final Set<VirtualFile> myAddedFiles = new SmartHashSet<>();
    private final Map<VirtualFile, VirtualFile> myCopyFromMap = new HashMap<>(); // copy -> original
    private final Set<FilePath> myDeletedFiles = new SmartHashSet<>();
    private final Set<FilePath> myDeletedWithoutConfirmFiles = new SmartHashSet<>();
    private final Set<MovedFileInfo> myMovedFiles = new SmartHashSet<>();
    private final List<VcsException> myExceptions = new SmartList<>();

    private final ReentrantReadWriteLock PROCESSING_LOCK = new ReentrantReadWriteLock();

    public boolean addException(@NotNull VcsException exception) {
      return computeUnderLock(PROCESSING_LOCK.writeLock(), () -> myExceptions.add(exception));
    }

    @NotNull
    public List<VcsException> acquireExceptions() {
      return acquireListUnderLock(myExceptions);
    }

    @NotNull
    public List<VirtualFile> acquireAddedFiles() {
      return acquireListUnderLock(myAddedFiles);
    }

    @NotNull
    public List<MovedFileInfo> acquireMovedFiles() {
      return acquireListUnderLock(myMovedFiles);
    }

    @NotNull
    public AllDeletedFiles acquireAllDeletedFiles() {
      return computeUnderLock(PROCESSING_LOCK.writeLock(), () -> {
        List<FilePath> deletedWithoutConfirmFiles = new ArrayList<>(myDeletedWithoutConfirmFiles);
        List<FilePath> deletedFiles = new ArrayList<>(myDeletedFiles);
        myDeletedWithoutConfirmFiles.clear();
        myDeletedFiles.clear();
        return new AllDeletedFiles(deletedFiles, deletedWithoutConfirmFiles);
      });
    }

    /**
     * @return get a list of files under lock and clear the given collection of files
     */
    @NotNull
    private <T> List<T> acquireListUnderLock(@NotNull Collection<T> files) {
      return computeUnderLock(PROCESSING_LOCK.writeLock(), () -> {
        List<T> copiedFiles = new ArrayList<>(files);
        files.clear();
        return copiedFiles;
      });
    }

    /**
     * @return get a map of copied files under lock and clear the given map
     */
    @NotNull
    public Map<VirtualFile, VirtualFile> acquireCopiedFiles() {
      return computeUnderLock(PROCESSING_LOCK.writeLock(), () -> {
        Map<VirtualFile, VirtualFile> copyFromMap = new HashMap<>(myCopyFromMap);
        myCopyFromMap.clear();
        return copyFromMap;
      });
    }

    private void checkMovedAddedSourceBack() {
      if (myAddedFiles.isEmpty() || myMovedFiles.isEmpty()) return;

      Map<String, VirtualFile> addedPaths = new HashMap<>(myAddedFiles.size());
      for (VirtualFile file : myAddedFiles) {
        addedPaths.put(file.getPath(), file);
      }

      for (Iterator<MovedFileInfo> iterator = myMovedFiles.iterator(); iterator.hasNext(); ) {
        MovedFileInfo movedFile = iterator.next();
        VirtualFile oldAdded = addedPaths.get(movedFile.myOldPath);
        if (oldAdded != null) {
          iterator.remove();
          myAddedFiles.remove(oldAdded);
          myAddedFiles.add(movedFile.myFile);
          if (isFileCopyingFromTrackingSupported()) {
            myCopyFromMap.put(oldAdded, movedFile.myFile);
          }
        }
      }
    }

    // If a file is scheduled for deletion, and at the same time for copying or addition, don't delete it.
    // It happens during Overwrite command or undo of overwrite.
    private void doNotDeleteAddedCopiedOrMovedFiles() {
      if (myDeletedFiles.isEmpty() && myDeletedWithoutConfirmFiles.isEmpty()) return;

      Set<String> copiedAddedMoved = new HashSet<>();
      for (VirtualFile file : myCopyFromMap.keySet()) {
        copiedAddedMoved.add(file.getPath());
      }
      for (VirtualFile file : myAddedFiles) {
        copiedAddedMoved.add(file.getPath());
      }
      for (MovedFileInfo movedFileInfo : myMovedFiles) {
        copiedAddedMoved.add(movedFileInfo.myNewPath);
      }

      myDeletedFiles.removeIf(path -> copiedAddedMoved.contains(path.getPath()));
      myDeletedWithoutConfirmFiles.removeIf(path -> copiedAddedMoved.contains(path.getPath()));
    }

    private boolean isAnythingToProcess() {
      return computeUnderLock(PROCESSING_LOCK.readLock(), () -> !myAddedFiles.isEmpty() ||
                                                                !myDeletedFiles.isEmpty() ||
                                                                !myDeletedWithoutConfirmFiles.isEmpty() ||
                                                                !myMovedFiles.isEmpty());
    }

    @CalledInBackground
    private void process(@NotNull List<VFileEvent> events) {
      processEvents(events);
      runUnderLock(PROCESSING_LOCK.writeLock(), () -> {
        doNotDeleteAddedCopiedOrMovedFiles();
        checkMovedAddedSourceBack();
      });

      executeAdd();
      executeDelete();
      executeMoveRename();

      List<VcsException> exceptions = acquireExceptions();
      if (!exceptions.isEmpty()) {
        AbstractVcsHelper.getInstance(myProject).showErrors(exceptions, myVcs.getDisplayName() + " operations errors");
      }
    }

    private void processFileCreated(@NotNull VFileCreateEvent event) {
      if (LOG.isDebugEnabled()) LOG.debug("fileCreated: ", event.getFile());
      if (isDirectoryVersioningSupported() || !event.isDirectory()) {
        VirtualFile file = event.getFile();
        if (file == null) return;

        LOG.debug("Adding [", file, "] to added files");
        runUnderLock(PROCESSING_LOCK.writeLock(), () -> {
          myAddedFiles.add(file);
        });
      }
    }

    private void processFileMoved(@NotNull VFileMoveEvent event) {
      VirtualFile file = event.getFile();
      VirtualFile oldParent = event.getOldParent();
      if (!isUnderMyVcs(oldParent)) {
        runUnderLock(PROCESSING_LOCK.writeLock(), () -> {
          myAddedFiles.add(file);
        });
      }
    }

    private void processFileCopied(@NotNull VFileCopyEvent event) {
      VirtualFile newFile = event.getNewParent().findChild(event.getNewChildName());
      if (newFile == null || myChangeListManager.isIgnoredFile(newFile)) return;
      VirtualFile originalFile = event.getFile();
      runUnderLock(PROCESSING_LOCK.writeLock(), () -> {
        if (isFileCopyingFromTrackingSupported() && isUnderMyVcs(originalFile)) {
          myAddedFiles.add(newFile);
          myCopyFromMap.put(newFile, originalFile);
        }
        else {
          myAddedFiles.add(newFile);
        }
      });
    }

    private void processDeletedFile(@NotNull VirtualFile file) {
      if (file.isDirectory() && file instanceof NewVirtualFile && !isDirectoryVersioningSupported() && !isRecursiveDeleteSupported()) {
        for (VirtualFile child : ((NewVirtualFile)file).getCachedChildren()) {
          ProgressManager.checkCanceled();
          if (!myChangeListManager.isIgnoredFile(child)) {
            processDeletedFile(child);
          }
        }
      }
      else {
        VcsDeleteType type = needConfirmDeletion(file);
        if (type == VcsDeleteType.IGNORE) return;

        FilePath filePath = VcsUtil.getFilePath(file);
        runUnderLock(PROCESSING_LOCK.writeLock(), () -> {
          if (type == VcsDeleteType.CONFIRM) {
            myDeletedFiles.add(filePath);
          }
          else if (type == VcsDeleteType.SILENT) {
            myDeletedWithoutConfirmFiles.add(filePath);
          }
        });
      }
    }

    private void processMovedFile(@NotNull VirtualFile file, @NotNull String newParentPath, @NotNull String newName) {
      FileStatus status = myChangeListManager.getStatus(file);
      LOG.debug("Checking moved file ", file, "; status=", status);

      String newPath = newParentPath + "/" + newName;
      runUnderLock(PROCESSING_LOCK.writeLock(), () -> {
        if (!(filterOutUnknownFiles() && status == FileStatus.UNKNOWN) && status != FileStatus.IGNORED) {
          MovedFileInfo existingMovedFile = ContainerUtil.find(myMovedFiles, info -> Comparing.equal(info.myFile, file));
          if (existingMovedFile != null) {
            LOG.debug("Reusing existing moved file [" + file + "] with new path [" + newPath + "]");
            existingMovedFile.myNewPath = newPath;
          }
          else {
            LOG.debug("Registered moved file ", file);
            myMovedFiles.add(new MovedFileInfo(file, newPath));
          }
        }
        else {
          // If a file is moved on top of another file (overwrite), the VFS at first removes the original file,
          // and then performs the "clean" move.
          // But we don't need to handle this deletion by the VCS: it is not a real deletion, but just a trick to implement the overwrite.
          // This situation is already handled in doNotDeleteAddedCopiedOrMovedFiles(), but that method is called at the end of the command,
          // so it is not suitable for moving unversioned files: if an unversioned file is moved, it won't be recorded,
          // won't affect doNotDeleteAddedCopiedOrMovedFiles(), and therefore won't save the file from deletion.
          // Thus here goes a special handle for unversioned files overwrite-move.
          myDeletedFiles.remove(VcsUtil.getFilePath(newPath));
        }
      });
    }

    private void processBeforeFileMovement(@NotNull VFileMoveEvent event) {
      VirtualFile file = event.getFile();
      if (isUnderMyVcs(event.getNewParent())) {
        LOG.debug("beforeFileMovement ", event, " into same vcs");
        addFileToMove(file, event.getNewParent().getPath(), file.getName());
      }
      else {
        LOG.debug("beforeFileMovement ", event, " into different vcs");
        myProcessor.processDeletedFile(file);
      }
    }

    private void processBeforePropertyChange(@NotNull VFilePropertyChangeEvent event) {
      if (event.isRename()) {
        LOG.debug("before file rename ", event);
        String newName = (String)event.getNewValue();
        VirtualFile file = event.getFile();
        VirtualFile parent = file.getParent();
        if (parent != null) {
          addFileToMove(file, parent.getPath(), newName);
        }
      }
    }

    private void processEvents(@NotNull List<VFileEvent> events) {
      for (VFileEvent event : events) {
        ProgressManager.checkCanceled();
        if (isEventIgnored(event)) continue;

        if (event instanceof VFileCreateEvent) {
          processFileCreated((VFileCreateEvent)event);
        }
        else if (event instanceof VFileCopyEvent) {
          processFileCopied((VFileCopyEvent)event);
        }
        else if (event instanceof VFileMoveEvent) {
          processFileMoved((VFileMoveEvent)event);
        }
      }
    }
  }

  /**
   * @see #installListeners()
   */
  protected VcsVFSListener(@NotNull AbstractVcs vcs) {
    myProject = vcs.getProject();
    myVcs = vcs;
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myVcsIgnoreManager = VcsIgnoreManager.getInstance(myProject);

    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myAddOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);
    myRemoveOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, vcs);

    myVcsFileListenerContextHelper = VcsFileListenerContextHelper.getInstance(myProject);

    myProjectConfigurationFilesProcessor = createProjectConfigurationFilesProcessor();
    myExternalFilesProcessor = createExternalFilesProcessor();
  }

  /**
   * @deprecated Use {@link #VcsVFSListener(AbstractVcs)} followed by {@link #installListeners()}
   */
  @Deprecated
  protected VcsVFSListener(@NotNull Project project, @NotNull AbstractVcs vcs) {
    this(vcs);
    installListeners();
  }

  protected void installListeners() {
    VirtualFileManager.getInstance().addAsyncFileListener(new MyAsyncVfsListener(), this);
    myProject.getMessageBus().connect(this).subscribe(CommandListener.TOPIC, new MyCommandAdapter());

    myProjectConfigurationFilesProcessor.install();
    myExternalFilesProcessor.install();
    new IgnoreFilesProcessorImpl(myProject, myVcs, this).install();
  }

  @Override
  public void dispose() {
  }

  protected boolean isEventAccepted(@NotNull VFileEvent event) {
    return !event.isFromRefresh() && (event.getFileSystem() instanceof LocalFileSystem);
  }

  protected boolean isEventIgnored(@NotNull VFileEvent event) {
    FilePath filePath = VcsUtil.getFilePath(event.getPath());
    return !isUnderMyVcs(filePath) || myChangeListManager.isIgnoredFile(filePath);
  }

  protected boolean isUnderMyVcs(@Nullable VirtualFile file) {
    return file != null && ReadAction.compute(() -> !myProject.isDisposed() && myVcsManager.getVcsFor(file) == myVcs);
  }

  protected boolean isUnderMyVcs(@Nullable FilePath filePath) {
    return filePath != null && ReadAction.compute(() -> !myProject.isDisposed() && myVcsManager.getVcsFor(filePath) == myVcs);
  }

  @CalledInBackground
  protected void executeAdd() {
    List<VirtualFile> addedFiles = myProcessor.acquireAddedFiles();
    LOG.debug("executeAdd. addedFiles: ", addedFiles);
    addedFiles.removeIf(myVcsFileListenerContextHelper::isAdditionIgnored);
    addedFiles.removeIf(myVcsIgnoreManager::isPotentiallyIgnoredFile);
    Map<VirtualFile, VirtualFile> copyFromMap = isFileCopyingFromTrackingSupported() ? myProcessor.acquireCopiedFiles() : emptyMap();
    if (!addedFiles.isEmpty()) {
      executeAdd(addedFiles, copyFromMap);
    }
  }

  /**
   * Execute add that performs adding from specific collections
   *
   * @param addedFiles  the added files
   * @param copyFromMap the copied files
   */
  protected void executeAdd(@NotNull List<VirtualFile> addedFiles, @NotNull Map<VirtualFile, VirtualFile> copyFromMap) {
    VcsShowConfirmationOption.Value addOption = myAddOption.getValue();
    LOG.debug("executeAdd. add-option: ", addOption, ", files to add: ", addedFiles);
    if (addOption == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return;

    addedFiles = myProjectConfigurationFilesProcessor.filterNotProjectConfigurationFiles(addedFiles);

    if (addOption == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      performAdding(addedFiles, copyFromMap);
    }
    else {
      final AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
      // TODO[yole]: nice and clean description label
      Collection<VirtualFile> filesToProcess = helper.selectFilesToProcess(addedFiles, getAddTitle(), null,
                                                                           getSingleFileAddTitle(), getSingleFileAddPromptTemplate(),
                                                                           myAddOption);
      if (filesToProcess != null) {
        performAdding(new ArrayList<>(filesToProcess), copyFromMap);
      }
    }
  }

  protected void executeAddWithoutIgnores(@NotNull List<VirtualFile> addedFiles,
                                          @NotNull Map<VirtualFile, VirtualFile> copyFromMap,
                                          @NotNull ExecuteAddCallback executeAddCallback) {
    executeAddCallback.executeAdd(addedFiles, copyFromMap);
  }

  @CalledInBackground
  private void executeMoveRename() {
    List<MovedFileInfo> movedFiles = myProcessor.acquireMovedFiles();
    LOG.debug("executeMoveRename ", movedFiles);
    if (!movedFiles.isEmpty()) {
      performMoveRename(movedFiles);
    }
  }

  @CalledInBackground
  protected void executeDelete() {
    AllDeletedFiles allFiles = myProcessor.acquireAllDeletedFiles();
    List<FilePath> filesToDelete = allFiles.deletedWithoutConfirmFiles;
    List<FilePath> deletedFiles = allFiles.deletedFiles;

    filesToDelete.removeIf(myVcsFileListenerContextHelper::isDeletionIgnored);
    deletedFiles.removeIf(myVcsFileListenerContextHelper::isDeletionIgnored);

    VcsShowConfirmationOption.Value removeOption = myRemoveOption.getValue();
    if (removeOption == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      filesToDelete.addAll(deletedFiles);
    }
    else if (removeOption == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION) {
      if (!deletedFiles.isEmpty()) {
        Collection<FilePath> filePaths = selectFilePathsToDelete(deletedFiles);
        if (filePaths != null) {
          filesToDelete.addAll(filePaths);
        }
      }
    }
    if (!filesToDelete.isEmpty()) {
      performDeletion(filesToDelete);
    }
  }

  protected void processMovedFile(@NotNull VirtualFile file, @NotNull String newParentPath, @NotNull String newName) {
    myProcessor.processMovedFile(file, newParentPath, newName);
  }

  @FunctionalInterface
  protected interface ExecuteAddCallback {
    void executeAdd(@NotNull List<VirtualFile> addedFiles, @NotNull Map<VirtualFile, VirtualFile> copyFromMap);
  }

  protected void saveUnsavedVcsIgnoreFiles() {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Set<String> ignoreFileNames = VcsUtil.getVcsIgnoreFileNames(myProject);

    for (Document document : fileDocumentManager.getUnsavedDocuments()) {
      VirtualFile file = fileDocumentManager.getFile(document);
      if (file != null && ignoreFileNames.contains(file.getName())) {
        fileDocumentManager.saveDocument(document);
      }
    }
  }

  /**
   * Select file paths to delete
   *
   * @param deletedFiles deleted files set
   * @return selected files or null (that is considered as empty file set)
   */
  @Nullable
  protected Collection<FilePath> selectFilePathsToDelete(@NotNull List<FilePath> deletedFiles) {
    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
    Ref<Collection<FilePath>> ref = Ref.create();
    ApplicationManager.getApplication()
      .invokeAndWait(() -> ref.set(helper.selectFilePathsToProcess(deletedFiles, getDeleteTitle(), null, getSingleFileDeleteTitle(),
                                                                   getSingleFileDeletePromptTemplate(), myRemoveOption)));
    return ref.get();
  }

  protected void beforeContentsChange(@NotNull VFileContentChangeEvent event) {
  }

  private void addFileToMove(@NotNull VirtualFile file, @NotNull String newParentPath, @NotNull String newName) {
    if (file.isDirectory() && !file.is(VFileProperty.SYMLINK) && !isDirectoryVersioningSupported()) {
      @SuppressWarnings("UnsafeVfsRecursion") VirtualFile[] children = file.getChildren();
      if (children != null) {
        for (VirtualFile child : children) {
          ProgressManager.checkCanceled();
          addFileToMove(child, newParentPath + "/" + newName, child.getName());
        }
      }
    }
    else {
      processMovedFile(file, newParentPath, newName);
    }
  }

  protected boolean filterOutUnknownFiles() {
    return true;
  }

  @NotNull
  protected VcsDeleteType needConfirmDeletion(@NotNull VirtualFile file) {
    return VcsDeleteType.CONFIRM;
  }

  @NotNull
  protected abstract String getAddTitle();

  @NotNull
  protected abstract String getSingleFileAddTitle();

  @NotNull
  protected abstract String getSingleFileAddPromptTemplate();

  @NotNull
  protected abstract String getDeleteTitle();

  protected abstract String getSingleFileDeleteTitle();

  protected abstract String getSingleFileDeletePromptTemplate();

  protected abstract void performAdding(@NotNull Collection<VirtualFile> addedFiles, @NotNull Map<VirtualFile, VirtualFile> copyFromMap);

  protected abstract void performDeletion(@NotNull List<FilePath> filesToDelete);

  protected abstract void performMoveRename(@NotNull List<MovedFileInfo> movedFiles);

  protected abstract boolean isDirectoryVersioningSupported();

  protected boolean isRecursiveDeleteSupported() {
    return false;
  }

  protected boolean isFileCopyingFromTrackingSupported() {
    return true;
  }

  @SuppressWarnings("unchecked")
  private ExternallyAddedFilesProcessorImpl createExternalFilesProcessor() {
    return new ExternallyAddedFilesProcessorImpl(myProject,
                                                 this,
                                                 myVcs,
                                                 (files) -> {
                                                   performAdding((Collection<VirtualFile>)files, emptyMap());
                                                   return Unit.INSTANCE;
                                                 });
  }

  @SuppressWarnings("unchecked")
  private ProjectConfigurationFilesProcessorImpl createProjectConfigurationFilesProcessor() {
    return new ProjectConfigurationFilesProcessorImpl(myProject,
                                                      this,
                                                      myVcs.getDisplayName(),
                                                      (files) -> {
                                                        performAdding((Collection<VirtualFile>)files, emptyMap());
                                                        return Unit.INSTANCE;
                                                      });
  }

  private class MyAsyncVfsListener implements AsyncFileListener {

    private boolean isBeforeEvent(@NotNull VFileEvent event) {
      return event instanceof VFileContentChangeEvent
             || event instanceof VFileDeleteEvent
             || event instanceof VFileMoveEvent
             || event instanceof VFilePropertyChangeEvent;
    }

    private boolean isAfterEvent(@NotNull VFileEvent event) {
      return event instanceof VFileCreateEvent
             || event instanceof VFileCopyEvent
             || event instanceof VFileMoveEvent;
    }

    @Nullable
    @Override
    public ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events) {
      List<VFileEvent> beforeEvents = new ArrayList<>();
      List<VFileEvent> afterEvents = new ArrayList<>();
      for (VFileEvent event : events) {
        ProgressManager.checkCanceled();
        if (event instanceof VFileContentChangeEvent) {
          VirtualFile file = Objects.requireNonNull(event.getFile());
          if (isUnderMyVcs(file)) {
            beforeEvents.add(event);
          }
        }
        else if (isEventAccepted(event)) {
          if (isBeforeEvent(event)) {
            beforeEvents.add(event);
          }
          if (isAfterEvent(event)) {
            afterEvents.add(event);
          }
        }
      }
      return beforeEvents.isEmpty() && afterEvents.isEmpty() ? null : new ChangeApplier() {
        @Override
        public void beforeVfsChange() {
          for (VFileEvent event : beforeEvents) {
            if (event instanceof VFileContentChangeEvent) {
              beforeContentsChange((VFileContentChangeEvent)event);
            }

            if (isEventIgnored(event)) {
              continue;
            }

            if (event instanceof VFileDeleteEvent) {
              myProcessor.processDeletedFile(((VFileDeleteEvent)event).getFile());
            }
            else if (event instanceof VFileMoveEvent) {
              myProcessor.processBeforeFileMovement((VFileMoveEvent)event);
            }
            else if (event instanceof VFilePropertyChangeEvent) {
              myProcessor.processBeforePropertyChange((VFilePropertyChangeEvent)event);
            }
          }
        }

        @Override
        public void afterVfsChange() {
          myEventsToProcess.addAll(afterEvents);
        }
      };
    }
  }
    private class MyCommandAdapter implements CommandListener {

      @Override
      public void commandFinished(@NotNull CommandEvent event) {
        if (myProject != event.getProject()) return;

        List<VFileEvent> events = ContainerUtil.copyList(myEventsToProcess);
        myEventsToProcess.clear();

        if (events.isEmpty() && !myProcessor.isAnythingToProcess()) return;

        ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(() -> myProcessor.process(events),
                                               "Version Control: Processing Changed Files", true, myProject);
      }
    }

    private static void runUnderLock(@NotNull Lock lock, @NotNull Runnable runnable) {
      computeUnderLock(lock, (Computable<Void>)() -> {
        runnable.run();
        return null;
      });
    }

    private static <V> V computeUnderLock(@NotNull Lock lock, @NotNull Computable<V> computable) {
      lock.lock();
      try {
        return computable.compute();
      }
      finally {
        lock.unlock();
      }
    }
}

