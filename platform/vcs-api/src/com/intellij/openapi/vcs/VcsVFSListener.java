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

package com.intellij.openapi.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.SmartList;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public abstract class VcsVFSListener implements Disposable {
  private VcsDirtyScopeManager myDirtyScopeManager;
  private final ProjectLevelVcsManager myVcsManager;
  private final VcsFileListenerContextHelper myVcsFileListenerContextHelper;

  protected static class MovedFileInfo {
    public final String myOldPath;
    public String myNewPath;
    private final VirtualFile myFile;

    protected MovedFileInfo(VirtualFile file, final String newPath) {
      myOldPath = file.getPath();
      myNewPath = newPath;
      myFile = file;
    }
  }

  protected final Project myProject;
  protected final AbstractVcs myVcs;
  protected final ChangeListManager myChangeListManager;
  protected final VcsShowConfirmationOption myAddOption;
  protected final VcsShowConfirmationOption myRemoveOption;
  protected final List<VirtualFile> myAddedFiles = new ArrayList<VirtualFile>();
  protected final Map<VirtualFile, VirtualFile> myCopyFromMap = new HashMap<VirtualFile, VirtualFile>();
  protected final List<VcsException> myExceptions = new SmartList<VcsException>();
  protected final List<FilePath> myDeletedFiles = new ArrayList<FilePath>();
  protected final List<FilePath> myDeletedWithoutConfirmFiles = new ArrayList<FilePath>();
  protected final List<MovedFileInfo> myMovedFiles = new ArrayList<MovedFileInfo>();
  protected final List<VirtualFile> myDirtyFiles = new ArrayList<VirtualFile>();

  protected enum VcsDeleteType {SILENT, CONFIRM, IGNORE}

  protected VcsVFSListener(final Project project, final AbstractVcs vcs) {
    myProject = project;
    myVcs = vcs;
    myChangeListManager = ChangeListManager.getInstance(project);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);

    final MyVirtualFileAdapter myVFSListener = new MyVirtualFileAdapter();
    final MyCommandAdapter myCommandListener = new MyCommandAdapter();

    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myAddOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);
    myRemoveOption = myVcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, vcs);

    VirtualFileManager.getInstance().addVirtualFileListener(myVFSListener, this);
    CommandProcessor.getInstance().addCommandListener(myCommandListener, this);
    myVcsFileListenerContextHelper = VcsFileListenerContextHelper.getInstance(myProject);
  }

  public void dispose() {
  }

  protected boolean isEventIgnored(final VirtualFileEvent event, boolean putInDirty) {
    if (event.isFromRefresh()) return true;
    boolean vcsIgnored = !isUnderMyVcs(event.getFile());
    if (vcsIgnored) {
      myDirtyFiles.add(event.getFile());
    }
    return vcsIgnored;
  }

  private boolean isUnderMyVcs(VirtualFile file) {
    return myVcsManager.getVcsFor(file) == myVcs &&
           (myVcsManager.isFileInContent(file)) &&
           !myChangeListManager.isIgnoredFile(file);
  }

  protected void executeAdd() {
    final List<VirtualFile> addedFiles = acquireAddedFiles();
    for (Iterator<VirtualFile> iterator = addedFiles.iterator(); iterator.hasNext(); ) {
      VirtualFile file = iterator.next();
      if (myVcsFileListenerContextHelper.isAdditionIgnored(file)) {
        iterator.remove();
      }
    }
    final Map<VirtualFile, VirtualFile> copyFromMap = acquireCopiedFiles();
    if (! addedFiles.isEmpty()) {
      executeAdd(addedFiles, copyFromMap);
    }
  }

  /**
   * @return get map of copied files and clear the map
   */
  protected Map<VirtualFile, VirtualFile> acquireCopiedFiles() {
    final Map<VirtualFile, VirtualFile> copyFromMap = new HashMap<VirtualFile, VirtualFile>(myCopyFromMap);
    myCopyFromMap.clear();
    return copyFromMap;
  }

  /**
   * @return get list of added files and clear previous list
   */
  protected List<VirtualFile> acquireAddedFiles() {
    final List<VirtualFile> addedFiles = new ArrayList<VirtualFile>(myAddedFiles);
    myAddedFiles.clear();
    return addedFiles;
  }

  /**
   * Execute add that performs adding from specific collections
   *
   * @param addedFiles  the added files
   * @param copyFromMap the copied files
   */
  protected void executeAdd(List<VirtualFile> addedFiles, Map<VirtualFile, VirtualFile> copyFromMap) {
    if (myAddOption.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return;
    if (myAddOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
      performAdding(addedFiles, copyFromMap);
    }
    else {
      final AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
      // TODO[yole]: nice and clean description label
      Collection<VirtualFile> filesToProcess = helper.selectFilesToProcess(addedFiles, getAddTitle(), null,
                                                                           getSingleFileAddTitle(), getSingleFileAddPromptTemplate(),
                                                                           myAddOption);
      if (filesToProcess != null) {
        performAdding(new ArrayList<VirtualFile>(filesToProcess), copyFromMap);
      }
    }
  }

  private void addFileToDelete(VirtualFile file) {
    if (file.isDirectory() && file instanceof NewVirtualFile && !isDirectoryVersioningSupported()) {
      for (VirtualFile child : ((NewVirtualFile)file).getCachedChildren()) {
        addFileToDelete(child);
      }
    }
    else {
      final VcsDeleteType type = needConfirmDeletion(file);
      final FilePath filePath =
        VcsContextFactory.SERVICE.getInstance().createFilePathOnDeleted(new File(file.getPath()), file.isDirectory());
      if (type == VcsDeleteType.CONFIRM) {
        myDeletedFiles.add(filePath);
      }
      else if (type == VcsDeleteType.SILENT) {
        myDeletedWithoutConfirmFiles.add(filePath);
      }
    }
  }

  protected void executeDelete() {
    final List<FilePath> filesToDelete = new ArrayList<FilePath>(myDeletedWithoutConfirmFiles);
    final List<FilePath> deletedFiles = new ArrayList<FilePath>(myDeletedFiles);
    myDeletedWithoutConfirmFiles.clear();
    myDeletedFiles.clear();

    for (Iterator<FilePath> iterator = filesToDelete.iterator(); iterator.hasNext(); ) {
      FilePath file = iterator.next();
      if (myVcsFileListenerContextHelper.isDeletionIgnored(file)) {
        iterator.remove();
      }
    }
    for (Iterator<FilePath> iterator = deletedFiles.iterator(); iterator.hasNext(); ) {
      FilePath file = iterator.next();
      if (myVcsFileListenerContextHelper.isDeletionIgnored(file)) {
        iterator.remove();
      }
    }

    if (deletedFiles.isEmpty() &&filesToDelete.isEmpty()) return;

    if (myRemoveOption.getValue() != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
      if (myRemoveOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY || deletedFiles.isEmpty()) {
        filesToDelete.addAll(deletedFiles);
      }
      else {
        Collection<FilePath> filePaths = selectFilePathsToDelete(deletedFiles);
        if (filePaths != null) {
          filesToDelete.addAll(filePaths);
        }
      }
    }
    performDeletion(filesToDelete);
  }

  /**
   * Select file paths to delete
   *
   * @param deletedFiles deleted files set
   * @return selected files or null (that is considered as empty file set)
   */
  @Nullable
  protected Collection<FilePath> selectFilePathsToDelete(final List<FilePath> deletedFiles) {
    AbstractVcsHelper helper = AbstractVcsHelper.getInstance(myProject);
    return helper.selectFilePathsToProcess(deletedFiles, getDeleteTitle(), null, getSingleFileDeleteTitle(),
                                           getSingleFileDeletePromptTemplate(), myRemoveOption);
  }

  protected void beforeContentsChange(VirtualFileEvent event, VirtualFile file) {
  }

  protected void fileAdded(VirtualFileEvent event, VirtualFile file) {
    if (!isEventIgnored(event, true) && !myChangeListManager.isIgnoredFile(file) &&
        (isDirectoryVersioningSupported() || !file.isDirectory())) {
      myAddedFiles.add(event.getFile());
    }
  }

  private void addFileToMove(final VirtualFile file, final String newParentPath, final String newName) {
    if (file.isDirectory() && !isDirectoryVersioningSupported()) {
      VirtualFile[] children = file.getChildren();
      if (children != null) {
        for (VirtualFile child : children) {
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

  protected void processMovedFile(VirtualFile file, String newParentPath, String newName) {
    final FileStatus status = FileStatusManager.getInstance(myProject).getStatus(file);
    if (status == FileStatus.IGNORED) {
      if (file.getParent() != null) {
        myDirtyFiles.add(file.getParent());
        myDirtyFiles.add(file); // will be at new path
      }
    }
    if (!(filterOutUnknownFiles() && status == FileStatus.UNKNOWN) && status != FileStatus.IGNORED) {
      final String newPath = newParentPath + "/" + newName;
      boolean foundExistingInfo = false;
      for (MovedFileInfo info : myMovedFiles) {
        if (Comparing.equal(info.myFile, file)) {
          info.myNewPath = newPath;
          foundExistingInfo = true;
          break;
        }
      }
      if (!foundExistingInfo) {
        myMovedFiles.add(new MovedFileInfo(file, newPath));
      }
    }
  }

  private void executeMoveRename() {
    final List<MovedFileInfo> movedFiles = new ArrayList<MovedFileInfo>(myMovedFiles);
    myMovedFiles.clear();
    performMoveRename(movedFiles);
  }

  protected VcsDeleteType needConfirmDeletion(final VirtualFile file) {
    return VcsDeleteType.CONFIRM;
  }

  protected abstract String getAddTitle();

  protected abstract String getSingleFileAddTitle();

  protected abstract String getSingleFileAddPromptTemplate();

  protected abstract void performAdding(final Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap)
    ;

  protected abstract String getDeleteTitle();

  protected abstract String getSingleFileDeleteTitle();

  protected abstract String getSingleFileDeletePromptTemplate();

  protected abstract void performDeletion(List<FilePath> filesToDelete);

  protected abstract void performMoveRename(List<MovedFileInfo> movedFiles);

  protected abstract boolean isDirectoryVersioningSupported();

  private class MyVirtualFileAdapter extends VirtualFileAdapter {
    public void fileCreated(final VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      if (isUnderMyVcs(file)) {
        VcsVFSListener.this.fileAdded(event, file);
      }
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      if (isEventIgnored(event, true) || myChangeListManager.isIgnoredFile(event.getFile())) return;
      final AbstractVcs oldVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(event.getOriginalFile());
      if (oldVcs == myVcs) {
        final VirtualFile parent = event.getFile().getParent();
        if (parent != null) {
          myAddedFiles.add(event.getFile());
          myCopyFromMap.put(event.getFile(), event.getOriginalFile());
        }
      }
      else {
        myAddedFiles.add(event.getFile());
      }
    }

    public void beforeFileDeletion(final VirtualFileEvent event) {
      final VirtualFile file = event.getFile();
      if (isEventIgnored(event, true)) {
        return;
      }
      if (!myChangeListManager.isIgnoredFile(file)) {
        addFileToDelete(file);
        return;
      }
      // files are ignored, directories are handled recursively
      if (event.getFile().isDirectory()) {
        final List<VirtualFile> list = new LinkedList<VirtualFile>();
        VcsUtil.collectFiles(file, list, true, isDirectoryVersioningSupported());
        for (VirtualFile child : list) {
          if (!myChangeListManager.isIgnoredFile(child)) {
            addFileToDelete(child);
          }
        }
      }
    }

    public void beforeFileMovement(final VirtualFileMoveEvent event) {
      if (isEventIgnored(event, true)) return;
      final VirtualFile file = event.getFile();
      final AbstractVcs newVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(event.getNewParent());
      if (newVcs == myVcs) {
        addFileToMove(file, event.getNewParent().getPath(), file.getName());
      }
      else {
        addFileToDelete(event.getFile());
      }
    }

    public void fileMoved(final VirtualFileMoveEvent event) {
      if (isEventIgnored(event, true)) return;
      final AbstractVcs oldVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(event.getOldParent());
      if (oldVcs != myVcs) {
        myAddedFiles.add(event.getFile());
      }
    }

    public void beforePropertyChange(final VirtualFilePropertyEvent event) {
      if (!isEventIgnored(event, false) && event.getPropertyName().equalsIgnoreCase(VirtualFile.PROP_NAME)) {
        String oldName = (String)event.getOldValue();
        String newName = (String)event.getNewValue();
        // in order to force a reparse of a file, the rename event can be fired with old name equal to new name -
        // such events needn't be handled by the VCS
        if (!Comparing.equal(oldName, newName)) {
          final VirtualFile file = event.getFile();
          final VirtualFile parent = file.getParent();
          if (parent != null) {
            addFileToMove(file, parent.getPath(), newName);
          }
        }
      }
    }

    @Override
    public void beforeContentsChange(VirtualFileEvent event) {
      VirtualFile file = event.getFile();
      assert !file.isDirectory();
      if (isUnderMyVcs(file)) {
        VcsVFSListener.this.beforeContentsChange(event, file);
      }
    }
  }

  private class MyCommandAdapter extends CommandAdapter {
    private int myCommandLevel;

    public void commandStarted(final CommandEvent event) {
      if (myProject != event.getProject()) return;
      myCommandLevel++;
    }

    private void checkMovedAddedSourceBack() {
      if (myAddedFiles.isEmpty() || myMovedFiles.isEmpty()) return;

      final Map<String, VirtualFile> addedPaths = new HashMap<String, VirtualFile>(myAddedFiles.size());
      for (VirtualFile file : myAddedFiles) {
        addedPaths.put(file.getPath(), file);
      }

      for (Iterator<MovedFileInfo> iterator = myMovedFiles.iterator(); iterator.hasNext();) {
        final MovedFileInfo movedFile = iterator.next();
        if (addedPaths.containsKey(movedFile.myOldPath)) {
          iterator.remove();
          final VirtualFile oldAdded = addedPaths.get(movedFile.myOldPath);
          myAddedFiles.remove(oldAdded);
          myAddedFiles.add(movedFile.myFile);
          myCopyFromMap.put(oldAdded, movedFile.myFile);
        }
      }
    }

    // If a file is scheduled for deletion, and at the same time for copying or addition, don't delete it.
    // It happens during Overwrite command or undo of overwrite.
    private void dontDeleteAddedCopiedOrMovedFiles() {
      Collection<String> copiedAddedMoved = new ArrayList<String>();
      for (VirtualFile file : myCopyFromMap.keySet()) {
        copiedAddedMoved.add(file.getPath());
      }
      for (VirtualFile file : myAddedFiles) {
        copiedAddedMoved.add(file.getPath());
      }
      for (MovedFileInfo movedFileInfo : myMovedFiles) {
        copiedAddedMoved.add(movedFileInfo.myNewPath);
      }

      for (Iterator<FilePath> iter = myDeletedFiles.iterator(); iter.hasNext(); ) {
        if (copiedAddedMoved.contains(FileUtil.toSystemIndependentName(iter.next().getPath()))) {
          iter.remove();
        }
      }
      for (Iterator<FilePath> iter = myDeletedWithoutConfirmFiles.iterator(); iter.hasNext(); ) {
        if (copiedAddedMoved.contains(FileUtil.toSystemIndependentName(iter.next().getPath()))) {
          iter.remove();
        }
      }
    }

    public void commandFinished(final CommandEvent event) {
      if (myProject != event.getProject()) return;
      myCommandLevel--;
      if (myCommandLevel == 0) {
        if (!myAddedFiles.isEmpty() || !myDeletedFiles.isEmpty() || !myDeletedWithoutConfirmFiles.isEmpty() || !myMovedFiles.isEmpty() ||
            ! myDirtyFiles.isEmpty()) {
          // avoid reentering commandFinished handler - saving the documents may cause a "before file deletion" event firing,
          // which will cause closing the text editor, which will itself run a command that will be caught by this listener
          myCommandLevel++;
          try {
            FileDocumentManager.getInstance().saveAllDocuments();
          }
          finally {
            myCommandLevel--;
          }
          dontDeleteAddedCopiedOrMovedFiles();
          checkMovedAddedSourceBack();
          if (!myAddedFiles.isEmpty()) {
            executeAdd();
            myAddedFiles.clear();
          }
          if (!myDeletedFiles.isEmpty() || !myDeletedWithoutConfirmFiles.isEmpty()) {
            executeDelete();
            myDeletedFiles.clear();
            myDeletedWithoutConfirmFiles.clear();
          }
          if (!myMovedFiles.isEmpty()) {
            executeMoveRename();
            myMovedFiles.clear();
          }
          if (! myDirtyFiles.isEmpty()) {
            final List<VirtualFile> files = new ArrayList<VirtualFile>();
            final List<VirtualFile> dirs = new ArrayList<VirtualFile>();
            for (VirtualFile dirtyFile : myDirtyFiles) {
              if (dirtyFile != null) {
                if (dirtyFile.isDirectory()) {
                  dirs.add(dirtyFile);
                } else {
                  files.add(dirtyFile);
                }
              }
            }
            myDirtyScopeManager.filesDirty(files, dirs);
            myDirtyFiles.clear();
          }
          if (! myExceptions.isEmpty()) {
            AbstractVcsHelper.getInstance(myProject).showErrors(myExceptions, myVcs.getDisplayName() + " operations errors");
          }
        }
      }
    }

  }
}
