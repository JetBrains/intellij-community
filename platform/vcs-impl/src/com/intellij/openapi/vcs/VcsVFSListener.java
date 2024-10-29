// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.CommonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.components.ComponentManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsIgnoreManager;
import com.intellij.openapi.vcs.changes.ignore.IgnoreFilesProcessorImpl;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.vcsUtil.VcsUtil;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.util.ConcurrencyUtil.withLock;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public abstract class VcsVFSListener implements Disposable {
  protected static final Logger LOG = Logger.getInstance(VcsVFSListener.class);

  private final ProjectLevelVcsManager myVcsManager;
  private final VcsIgnoreManager myVcsIgnoreManager;
  private final VcsFileListenerContextHelper myVcsFileListenerContextHelper;

  protected static final class MovedFileInfo {
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
      return String.format("MovedFileInfo{[%s] -> [%s]}", myOldPath, myNewPath);  //NON-NLS
    }

    public boolean isCaseSensitive() {
      return myFile.isCaseSensitive();
    }

    public @NotNull FilePath getOldPath() {
      return VcsUtil.getFilePath(myOldPath, myFile.isDirectory());
    }

    public @NotNull FilePath getNewPath() {
      return VcsUtil.getFilePath(myNewPath, myFile.isDirectory());
    }
  }

  protected final Project myProject;
  protected final AbstractVcs myVcs;
  @NotNull protected final CoroutineScope coroutineScope;
  protected final ChangeListManager myChangeListManager;
  protected final VcsShowConfirmationOption myAddOption;
  protected final VcsShowConfirmationOption myRemoveOption;
  protected final StateProcessor myProcessor = new StateProcessor();
  private final ProjectConfigurationFilesProcessorImpl myProjectConfigurationFilesProcessor;
  private final ExternallyAddedFilesProcessorImpl myExternalFilesProcessor;
  private final IgnoreFilesProcessorImpl myIgnoreFilesProcessor;
  private final List<VFileEvent> myEventsToProcess = new SmartList<>();

  protected final class StateProcessor {
    private final Set<VirtualFile> myAddedFiles = new SmartHashSet<>();
    private final Map<VirtualFile, VirtualFile> myCopyFromMap = new HashMap<>(); // copy -> original
    private final Set<FilePath> myDeletedFiles = new SmartHashSet<>();
    private final Set<MovedFileInfo> myMovedFiles = new SmartHashSet<>();

    private final ReentrantReadWriteLock PROCESSING_LOCK = new ReentrantReadWriteLock();

    @NotNull
    public List<VirtualFile> acquireAddedFiles() {
      return acquireListUnderLock(myAddedFiles);
    }

    @NotNull
    public List<MovedFileInfo> acquireMovedFiles() {
      return acquireListUnderLock(myMovedFiles);
    }

    @NotNull
    public List<FilePath> acquireDeletedFiles() {
      return withLock(PROCESSING_LOCK.writeLock(), () -> {
        List<FilePath> deletedFiles = new ArrayList<>(myDeletedFiles);
        myDeletedFiles.clear();
        return deletedFiles;
      });
    }

    /**
     * @return get a list of files under lock and clear the given collection of files
     */
    @NotNull
    private <T> List<T> acquireListUnderLock(@NotNull Collection<? extends T> files) {
      return withLock(PROCESSING_LOCK.writeLock(), () -> {
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
      return withLock(PROCESSING_LOCK.writeLock(), () -> {
        Map<VirtualFile, VirtualFile> copyFromMap = new HashMap<>(myCopyFromMap);
        myCopyFromMap.clear();
        return copyFromMap;
      });
    }

    private void clearAllPendingTasks() {
      withLock(PROCESSING_LOCK.writeLock(), () -> {
        myAddedFiles.clear();
        myCopyFromMap.clear();
        myDeletedFiles.clear();
        myMovedFiles.clear();
      });
    }

    /**
     * Called under {@link #PROCESSING_LOCK} - avoid slow operations.
     */
    @RequiresBackgroundThread
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

    /**
     * If a file is scheduled for deletion, and at the same time for copying or addition, don't delete it.
     * It happens during Overwrite command or undo of overwrite.
     * <p>
     * Called under {@link #PROCESSING_LOCK} - avoid slow operations.
     */
    @RequiresBackgroundThread
    private void doNotDeleteAddedCopiedOrMovedFiles() {
      if (myDeletedFiles.isEmpty()) return;

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
    }

    private boolean isAnythingToProcess() {
      return withLock(PROCESSING_LOCK.readLock(), () -> !myAddedFiles.isEmpty() ||
                                                        !myDeletedFiles.isEmpty() ||
                                                        !myMovedFiles.isEmpty());
    }

    @RequiresBackgroundThread
    private void executePendingTasks() {
      withLock(PROCESSING_LOCK.writeLock(), () -> {
        doNotDeleteAddedCopiedOrMovedFiles();
        checkMovedAddedSourceBack();
      });

      executeAdd();
      executeDelete();
      executeMoveRename();
    }

    @RequiresBackgroundThread
    private void processFileCreated(@NotNull VFileCreateEvent event) {
      if (LOG.isDebugEnabled()) LOG.debug("fileCreated: ", event.getFile());
      if (!event.isDirectory()) {
        VirtualFile file = event.getFile();
        if (file == null) return;

        LOG.debug("Adding [", file, "] to added files");
        withLock(PROCESSING_LOCK.writeLock(), () -> {
          myAddedFiles.add(file);
        });
      }
    }

    @RequiresBackgroundThread
    private void processFileMoved(@NotNull VFileMoveEvent event) {
      VirtualFile file = event.getFile();
      VirtualFile oldParent = event.getOldParent();
      if (!isUnderMyVcs(oldParent)) {
        withLock(PROCESSING_LOCK.writeLock(), () -> {
          myAddedFiles.add(file);
        });
      }
    }

    @RequiresBackgroundThread
    private void processFileCopied(@NotNull VFileCopyEvent event) {
      VirtualFile newFile = event.getNewParent().findChild(event.getNewChildName());
      if (newFile == null || myChangeListManager.isIgnoredFile(newFile)) return;
      VirtualFile originalFile = event.getFile();
      if (isFileCopyingFromTrackingSupported() && isUnderMyVcs(originalFile)) {
        withLock(PROCESSING_LOCK.writeLock(), () -> {
          myAddedFiles.add(newFile);
          myCopyFromMap.put(newFile, originalFile);
        });
      }
      else {
        withLock(PROCESSING_LOCK.writeLock(), () -> {
          myAddedFiles.add(newFile);
        });
      }
    }

    @RequiresEdt
    private void processBeforeDeletedFile(@NotNull VFileDeleteEvent event) {
      processBeforeDeletedFile(event.getFile());
    }

    private void processBeforeDeletedFile(@NotNull VirtualFile file) {
      if (file.isDirectory() && file instanceof NewVirtualFile && !isRecursiveDeleteSupported()) {
        for (VirtualFile child : ((NewVirtualFile)file).getCachedChildren()) {
          ProgressManager.checkCanceled();
          FileStatus status = myChangeListManager.getStatus(child);
          if (!filterOutByStatus(status)) {
            processBeforeDeletedFile(child);
          }
        }
      }
      else {
        FileStatus status = myChangeListManager.getStatus(file);
        if (filterOutByStatus(status) || shouldIgnoreDeletion(status)) return;

        FilePath filePath = VcsUtil.getFilePath(file);
        withLock(PROCESSING_LOCK.writeLock(), () -> {
          myDeletedFiles.add(filePath);
        });
      }
    }

    @RequiresEdt
    private void processMovedFile(@NotNull VirtualFile file, @NotNull String newParentPath, @NotNull String newName) {
      FileStatus status = myChangeListManager.getStatus(file);
      LOG.debug("Checking moved file ", file, "; status=", status);

      String newPath = newParentPath + "/" + newName;
      withLock(PROCESSING_LOCK.writeLock(), () -> {
        if (!filterOutByStatus(status)) {
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
          myDeletedFiles.remove(VcsUtil.getFilePath(newPath, file.isDirectory()));
        }
      });
    }

    @RequiresEdt
    private void processBeforeFileMovement(@NotNull VFileMoveEvent event) {
      VirtualFile file = event.getFile();
      if (isUnderMyVcs(event.getNewParent())) {
        LOG.debug("beforeFileMovement ", event, " into same vcs");
        addFileToMove(file, event.getNewParent().getPath(), file.getName());
      }
      else {
        LOG.debug("beforeFileMovement ", event, " into different vcs");
        myProcessor.processBeforeDeletedFile(file);
      }
    }

    @RequiresEdt
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

    @RequiresEdt
    private void addFileToMove(@NotNull VirtualFile file, @NotNull String newParentPath, @NotNull String newName) {
      if (file.isDirectory() && !file.is(VFileProperty.SYMLINK)) {
        @SuppressWarnings("UnsafeVfsRecursion") VirtualFile[] children = file.getChildren();
        if (children != null) {
          for (VirtualFile child : children) {
            ProgressManager.checkCanceled();
            addFileToMove(child, newParentPath + "/" + newName, child.getName());
          }
        }
      }
      else {
        VcsVFSListener.this.processMovedFile(file, newParentPath, newName);
        myProcessor.processMovedFile(file, newParentPath, newName);
      }
    }

    @RequiresEdt
    private void processBeforeEvents(@NotNull List<? extends VFileEvent> events) {
      for (VFileEvent event : events) {
        if (isEventIgnored(event)) continue;

        if (event instanceof VFileDeleteEvent && allowedDeletion(event)) {
          processBeforeDeletedFile((VFileDeleteEvent)event);
        }
        else if (event instanceof VFileMoveEvent) {
          processBeforeFileMovement((VFileMoveEvent)event);
        }
        else if (event instanceof VFilePropertyChangeEvent) {
          processBeforePropertyChange((VFilePropertyChangeEvent)event);
        }
      }
    }

    @RequiresBackgroundThread
    private void processAfterEvents(@NotNull List<? extends VFileEvent> events) {
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
  protected VcsVFSListener(@NotNull AbstractVcs vcs, @NotNull CoroutineScope coroutineScope) {
    myProject = vcs.getProject();
    myVcs = vcs;
    this.coroutineScope = coroutineScope;
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myVcsIgnoreManager = VcsIgnoreManager.getInstance(myProject);

    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myAddOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);
    myRemoveOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, vcs);

    myVcsFileListenerContextHelper = VcsFileListenerContextHelper.getInstance(myProject);

    myProjectConfigurationFilesProcessor = createProjectConfigurationFilesProcessor();
    myExternalFilesProcessor = createExternalFilesProcessor();
    myIgnoreFilesProcessor = createIgnoreFilesProcessor();
  }

  /**
   * @deprecated Use {@link #VcsVFSListener(AbstractVcs, CoroutineScope)} followed by {@link #installListeners()}
   */
  @Deprecated(forRemoval = true)
  protected VcsVFSListener(@NotNull Project project, @NotNull AbstractVcs vcs) {
    //noinspection UsagesOfObsoleteApi
    this(vcs, ((ComponentManagerEx)project).getCoroutineScope());
    installListeners();
  }

  protected void installListeners() {
    VirtualFileManager.getInstance().addAsyncFileListener(new MyAsyncVfsListener(), this);
    myProject.getMessageBus().connect(coroutineScope).subscribe(CommandListener.TOPIC, new MyCommandAdapter());

    myProjectConfigurationFilesProcessor.install();
    myExternalFilesProcessor.install(coroutineScope);
    myIgnoreFilesProcessor.install(coroutineScope);
  }

  @Override
  public void dispose() {
  }

  protected boolean isEventAccepted(@NotNull VFileEvent event) {
    return !event.isFromRefresh() && (event.getFileSystem() instanceof LocalFileSystem);
  }

  protected boolean isEventIgnored(@NotNull VFileEvent event) {
    FilePath filePath = getEventFilePath(event);
    return !isUnderMyVcs(filePath) || myChangeListManager.isIgnoredFile(filePath);
  }

  protected boolean isUnderMyVcs(@Nullable VirtualFile file) {
    return file != null && ReadAction.compute(() -> !myProject.isDisposed() && myVcsManager.getVcsFor(file) == myVcs);
  }

  protected boolean isUnderMyVcs(@Nullable FilePath filePath) {
    return filePath != null && ReadAction.compute(() -> !myProject.isDisposed() && myVcsManager.getVcsFor(filePath) == myVcs);
  }

  @NotNull
  private static FilePath getEventFilePath(@NotNull VFileEvent event) {
    if (event instanceof VFileCreateEvent createEvent) {
      return VcsUtil.getFilePath(event.getPath(), createEvent.isDirectory());
    }

    VirtualFile file = event.getFile();
    if (file != null) {
      // Do not use file.getPath(), as it is slower.
      return VcsUtil.getFilePath(event.getPath(), file.isDirectory());
    }
    else {
      LOG.error("VFileEvent should have VirtualFile: " + event);
      return VcsUtil.getFilePath(event.getPath());
    }
  }

  private boolean allowedDeletion(@NotNull VFileEvent event) {
    if (myVcsFileListenerContextHelper.isDeletionContextEmpty()) return true;

    return !myVcsFileListenerContextHelper.isDeletionIgnored(getEventFilePath(event));
  }

  private boolean allowedAddition(@NotNull VFileEvent event) {
    if (myVcsFileListenerContextHelper.isAdditionContextEmpty()) return true;

    return !myVcsFileListenerContextHelper.isAdditionIgnored(getEventFilePath(event));
  }

  @RequiresBackgroundThread
  protected void executeAdd() {
    List<VirtualFile> addedFiles = myProcessor.acquireAddedFiles();
    Map<VirtualFile, VirtualFile> copyFromMap = myProcessor.acquireCopiedFiles();
    LOG.debug("executeAdd. addedFiles: ", addedFiles);

    VcsShowConfirmationOption.Value addOption = myAddOption.getValue();
    if (addOption == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return;

    addedFiles.removeIf(myVcsIgnoreManager::isPotentiallyIgnoredFile);
    if (addedFiles.isEmpty()) return;

    executeAdd(addedFiles, copyFromMap);
  }

  protected void executeAdd(@NotNull List<VirtualFile> addedFiles, @NotNull Map<VirtualFile, VirtualFile> copyFromMap) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      // backward compatibility with plugins
      ApplicationManager.getApplication().executeOnPooledThread(() -> performAddingWithConfirmation(addedFiles, copyFromMap));
    }
    else {
      performAddingWithConfirmation(addedFiles, copyFromMap);
    }
  }

  /**
   * Execute add that performs adding from specific collections
   *
   * @param addedFiles  the added files
   * @param copyFromMap the copied files
   */
  @RequiresBackgroundThread
  protected void performAddingWithConfirmation(@NotNull List<VirtualFile> addedFiles, @NotNull Map<VirtualFile, VirtualFile> copyFromMap) {
    VcsShowConfirmationOption.Value addOption = myAddOption.getValue();
    LOG.debug("executeAdd. add-option: ", addOption, ", files to add: ", addedFiles);
    if (addOption == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return;

    addedFiles = myProjectConfigurationFilesProcessor.filterNotProjectConfigurationFiles(addedFiles);

    List<VirtualFile> filesToProcess = selectFilesToAdd(addedFiles);
    if (filesToProcess.isEmpty()) return;

    performAdding(filesToProcess, copyFromMap);
  }

  @RequiresBackgroundThread
  private void executeMoveRename() {
    List<MovedFileInfo> movedFiles = myProcessor.acquireMovedFiles();
    LOG.debug("executeMoveRename ", movedFiles);
    if (movedFiles.isEmpty()) return;

    performMoveRename(movedFiles);
  }

  @RequiresBackgroundThread
  protected void executeDelete() {
    List<FilePath> deletedFiles = myProcessor.acquireDeletedFiles();
    LOG.debug("executeDelete ", deletedFiles);

    VcsShowConfirmationOption.Value removeOption = myRemoveOption.getValue();
    if (removeOption == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return;

    deletedFiles.removeIf(myVcsIgnoreManager::isPotentiallyIgnoredFile);

    List<FilePath> filesToProcess = selectFilePathsToDelete(deletedFiles);
    if (filesToProcess.isEmpty()) return;

    performDeletion(filesToProcess);
  }

  protected void saveUnsavedVcsIgnoreFiles() {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Set<String> ignoreFileNames = VcsUtil.getVcsIgnoreFileNames(myProject);

    for (Document document : fileDocumentManager.getUnsavedDocuments()) {
      VirtualFile file = fileDocumentManager.getFile(document);
      if (file != null && ignoreFileNames.contains(file.getName())) {
        ApplicationManager.getApplication().invokeAndWait(() -> fileDocumentManager.saveDocument(document));
      }
    }
  }

  /**
   * Select file paths to delete
   *
   * @param deletedFiles deleted files set
   * @return selected files or empty if {@link VcsShowConfirmationOption.Value#DO_NOTHING_SILENTLY}
   */
  protected @NotNull List<FilePath> selectFilePathsToDelete(@NotNull List<FilePath> deletedFiles) {
    return selectFilePathsForOption(myRemoveOption, deletedFiles, getDeleteTitle(), getSingleFileDeleteTitle(),
                                    getSingleFileDeletePromptTemplate(),
                                    CommonBundle.message("button.delete"), CommonBundle.getCancelButtonText());
  }

  /**
   * Same as {@link #selectFilePathsToDelete} but for add operation
   *
   * @param addFiles added files set
   * @return selected files or empty if {@link VcsShowConfirmationOption.Value#DO_NOTHING_SILENTLY}
   */
  protected @NotNull List<FilePath> selectFilePathsToAdd(@NotNull List<FilePath> addFiles) {
    return selectFilePathsForOption(myAddOption, addFiles, getAddTitle(), getSingleFileAddTitle(), getSingleFileAddPromptTemplate(),
                                    CommonBundle.getAddButtonText(), CommonBundle.getCancelButtonText());
  }

  protected @NotNull List<VirtualFile> selectFilesToAdd(@NotNull List<VirtualFile> addFiles) {
    return selectFilesForOption(myAddOption, addFiles, getAddTitle(), getSingleFileAddTitle(), getSingleFileAddPromptTemplate());
  }

  private @NotNull List<FilePath> selectFilePathsForOption(@NotNull VcsShowConfirmationOption option,
                                                           @NotNull List<FilePath> files,
                                                           @NlsContexts.DialogTitle String title,
                                                           @NlsContexts.DialogTitle String singleFileTitle,
                                                           @NlsContexts.DialogMessage String singleFilePromptTemplate,
                                                           @NlsActions.ActionText @Nullable String okActionName,
                                                           @NlsActions.ActionText @Nullable String cancelActionName) {
    VcsShowConfirmationOption.Value optionValue = option.getValue();
    if (optionValue == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      return emptyList();
    }
    if (optionValue == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      return files;
    }
    if (files.isEmpty()) {
      return emptyList();
    }

    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
    Ref<Collection<FilePath>> ref = Ref.create();
    ApplicationManager.getApplication()
      .invokeAndWait(() -> ref.set(helper.selectFilePathsToProcess(files, title, null, singleFileTitle,
                                                                   singleFilePromptTemplate, option, okActionName, cancelActionName)));
    Collection<FilePath> selectedFilePaths = ref.get();
    return selectedFilePaths != null ? new ArrayList<>(selectedFilePaths) : emptyList();
  }

  private @NotNull List<VirtualFile> selectFilesForOption(@NotNull VcsShowConfirmationOption option,
                                                          @NotNull List<VirtualFile> files,
                                                          @NlsContexts.DialogTitle String title,
                                                          @NlsContexts.DialogTitle String singleFileTitle,
                                                          @NlsContexts.DialogMessage String singleFilePromptTemplate) {
    VcsShowConfirmationOption.Value optionValue = option.getValue();
    if (optionValue == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      return emptyList();
    }
    if (optionValue == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      return files;
    }
    if (files.isEmpty()) {
      return emptyList();
    }

    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
    Ref<Collection<VirtualFile>> ref = Ref.create();
    ApplicationManager.getApplication()
      .invokeAndWait(() -> ref.set(helper.selectFilesToProcess(files, title, null, singleFileTitle,
                                                               singleFilePromptTemplate, option)));
    Collection<VirtualFile> selectedFiles = ref.get();
    return selectedFiles != null ? new ArrayList<>(selectedFiles) : emptyList();
  }

  /**
   * @return whether {@link #beforeContentsChange} is overridden.
   */
  protected boolean processBeforeContentsChange() {
    return false;
  }

  /**
   * This is a very expensive operation and shall be avoided whenever possible.
   *
   * @see #processBeforeContentsChange()
   */
  @RequiresEdt
  protected void beforeContentsChange(@NotNull List<VFileContentChangeEvent> events) {
  }

  @RequiresEdt
  protected void processMovedFile(@NotNull VirtualFile file, @NotNull String newParentPath, @NotNull String newName) {
  }

  /**
   * Determine if the listener should not process files with the given status.
   */
  protected boolean filterOutByStatus(@NotNull FileStatus status) {
    return status == FileStatus.IGNORED || status == FileStatus.UNKNOWN;
  }

  protected boolean shouldIgnoreDeletion(@NotNull FileStatus status) {
    return false;
  }

  @NotNull
  @NlsContexts.DialogTitle
  protected abstract String getAddTitle();

  @NotNull
  @NlsContexts.DialogTitle
  protected abstract String getSingleFileAddTitle();

  @NotNull
  @NlsContexts.DialogMessage
  protected abstract String getSingleFileAddPromptTemplate();

  @NotNull
  @NlsContexts.DialogTitle
  protected abstract String getDeleteTitle();

  @NlsContexts.DialogTitle
  protected abstract String getSingleFileDeleteTitle();

  @NlsContexts.DialogMessage
  protected abstract String getSingleFileDeletePromptTemplate();

  protected abstract void performAdding(@NotNull Collection<VirtualFile> addedFiles, @NotNull Map<VirtualFile, VirtualFile> copyFromMap);

  protected abstract void performDeletion(@NotNull List<FilePath> filesToDelete);

  protected abstract void performMoveRename(@NotNull List<MovedFileInfo> movedFiles);

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
                                                      myVcs,
                                                      (files) -> {
                                                        performAdding((Collection<VirtualFile>)files, emptyMap());
                                                        return Unit.INSTANCE;
                                                      });
  }

  private IgnoreFilesProcessorImpl createIgnoreFilesProcessor() {
    return new IgnoreFilesProcessorImpl(myProject, this, myVcs);
  }

  private class MyAsyncVfsListener implements AsyncFileListener {

    private static boolean isBeforeEvent(@NotNull VFileEvent event) {
      return event instanceof VFileDeleteEvent
             || event instanceof VFileMoveEvent
             || event instanceof VFilePropertyChangeEvent;
    }

    private static boolean isAfterEvent(@NotNull VFileEvent event) {
      return event instanceof VFileCreateEvent
             || event instanceof VFileCopyEvent
             || event instanceof VFileMoveEvent;
    }

    @Nullable
    @Override
    public ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
      List<VFileContentChangeEvent> contentChangedEvents = new ArrayList<>();
      List<VFileEvent> beforeEvents = new ArrayList<>();
      List<VFileEvent> afterEvents = new ArrayList<>();
      for (VFileEvent event : events) {
        ProgressManager.checkCanceled();
        if (event instanceof VFileContentChangeEvent contentChangeEvent) {
          if (processBeforeContentsChange()) {
            VirtualFile file = contentChangeEvent.getFile();
            if (isUnderMyVcs(file)) {
              contentChangedEvents.add(contentChangeEvent);
            }
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
      if (contentChangedEvents.isEmpty() && beforeEvents.isEmpty() && afterEvents.isEmpty()) {
        return null;
      }
      return new ChangeApplier() {
        @Override
        public void beforeVfsChange() {
          beforeContentsChange(contentChangedEvents);

          myProcessor.processBeforeEvents(beforeEvents);
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

      /*
       * Create file events cannot be filtered in afterVfsChange since VcsFileListenerContextHelper populated after actual file creation in PathsVerifier.CheckAdded.check
       * So this commandFinished is the only way to get in sync with VcsFileListenerContextHelper to check if additions need to be filtered.
       */
      List<VFileEvent> afterEvents = ContainerUtil.filter(myEventsToProcess, e -> !(e instanceof VFileCreateEvent) || allowedAddition(e));
      myEventsToProcess.clear();

      if (afterEvents.isEmpty() && !myProcessor.isAnythingToProcess()) return;

      processEventsInBackground(afterEvents);
    }

    /**
     * Not using modal progress here, because it could lead to some focus related assertion (e.g. "showing dialogs from popup" in com.intellij.ui.popup.tree.TreePopupImpl)
     * Assume that it is a safe to do all processing in background even if "Add to VCS" dialog may appear during such processing.
     */
    private void processEventsInBackground(List<? extends VFileEvent> events) {
      new Task.Backgroundable(myProject, VcsBundle.message("progress.title.version.control.processing.changed.files"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            indicator.checkCanceled();
            myProcessor.processAfterEvents(events);
            myProcessor.executePendingTasks();
          }
          catch (ProcessCanceledException e) {
            myProcessor.clearAllPendingTasks();
          }
        }

        @Override
        public boolean isHeadless() {
          return false;
        }
      }.queue();
    }
  }

  @TestOnly
  protected final void waitForEventsProcessedInTestMode() {
    myExternalFilesProcessor.waitForEventsProcessedInTestMode();
    myProjectConfigurationFilesProcessor.waitForEventsProcessedInTestMode();
    myIgnoreFilesProcessor.waitForEventsProcessedInTestMode();
  }
}

