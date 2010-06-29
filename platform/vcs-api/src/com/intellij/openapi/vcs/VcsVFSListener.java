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
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public abstract class VcsVFSListener implements Disposable {
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
  protected final List<FilePath> myDeletedFiles = new ArrayList<FilePath>();
  protected final List<FilePath> myDeletedWithoutConfirmFiles = new ArrayList<FilePath>();
  protected final List<MovedFileInfo> myMovedFiles = new ArrayList<MovedFileInfo>();

  protected enum VcsDeleteType { SILENT, CONFIRM, IGNORE }

  protected VcsVFSListener(final Project project, final AbstractVcs vcs) {
    myProject = project;
    myVcs = vcs;
    myChangeListManager = ChangeListManager.getInstance(project);

    final MyVirtualFileAdapter myVFSListener = new MyVirtualFileAdapter();
    final MyCommandAdapter myCommandListener = new MyCommandAdapter();

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    myAddOption = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.ADD, vcs);
    myRemoveOption = vcsManager.getStandardConfirmation(VcsConfiguration.StandardConfirmation.REMOVE, vcs);

    VirtualFileManager.getInstance().addVirtualFileListener(myVFSListener,this);
    CommandProcessor.getInstance().addCommandListener(myCommandListener,this);
  }

  public void dispose() {
  }

  protected boolean isEventIgnored(final VirtualFileEvent event) {
    return event.isFromRefresh() || ProjectLevelVcsManager.getInstance(myProject).getVcsFor(event.getFile()) != myVcs;
  }

  protected void executeAdd() {
    final List<VirtualFile> addedFiles = new ArrayList<VirtualFile>(myAddedFiles);
    final Map<VirtualFile, VirtualFile> copyFromMap = new HashMap<VirtualFile, VirtualFile>(myCopyFromMap);
    myAddedFiles.clear();
    myCopyFromMap.clear();

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
    if (file.isDirectory() && file instanceof NewVirtualFile && !isDirectoryVersioningSupported()){
      for (VirtualFile child : ((NewVirtualFile)file).getCachedChildren()) {
        addFileToDelete(child);
      }
    } else {
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


  protected void processMovedFile(VirtualFile file, String newParentPath, String newName) {
    if (FileStatusManager.getInstance(myProject).getStatus(file) != FileStatus.UNKNOWN) {
      final String newPath = newParentPath + "/" + newName;
      boolean foundExistingInfo = false;
      for(MovedFileInfo info: myMovedFiles) {
        if (info.myFile == file) {
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
  protected abstract void performAdding(final Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap);

  protected abstract String getDeleteTitle();
  protected abstract String getSingleFileDeleteTitle();
  protected abstract String getSingleFileDeletePromptTemplate();
  protected abstract void performDeletion(List<FilePath> filesToDelete);
  protected abstract void performMoveRename(List<MovedFileInfo> movedFiles);
  protected abstract boolean isDirectoryVersioningSupported();

  private class MyVirtualFileAdapter extends VirtualFileAdapter {
    public void fileCreated(final VirtualFileEvent event) {
      if (!isEventIgnored(event) && !myChangeListManager.isIgnoredFile(event.getFile()) &&
           (isDirectoryVersioningSupported() || !event.getFile().isDirectory())) {
        myAddedFiles.add(event.getFile());              
      }
    }

    public void fileCopied(final VirtualFileCopyEvent event) {
      if (isEventIgnored(event) || myChangeListManager.isIgnoredFile(event.getFile())) return;
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
      if (isEventIgnored(event)) { return; }
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
      if (isEventIgnored(event)) return;
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
      if (isEventIgnored(event)) return;
      final AbstractVcs oldVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(event.getOldParent());
      if (oldVcs != myVcs) {
        myAddedFiles.add(event.getFile());
      }
    }

    public void beforePropertyChange(final VirtualFilePropertyEvent event) {
      if (!isEventIgnored(event) && event.getPropertyName().equalsIgnoreCase(VirtualFile.PROP_NAME)) {
        String oldName = (String) event.getOldValue();
        String newName = (String) event.getNewValue();
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

    public void commandFinished(final CommandEvent event) {
      if (myProject != event.getProject()) return;
      myCommandLevel--;
      if (myCommandLevel == 0) {
        if (!myAddedFiles.isEmpty() || !myDeletedFiles.isEmpty() || !myDeletedWithoutConfirmFiles.isEmpty() || !myMovedFiles.isEmpty()) {
          // avoid reentering commandFinished handler - saving the documents may cause a "before file deletion" event firing,
          // which will cause closing the text editor, which will itself run a command that will be caught by this listener
          myCommandLevel++;
          try {
            FileDocumentManager.getInstance().saveAllDocuments();
          }
          finally {
            myCommandLevel--;
          }
          checkMovedAddedSourceBack();
          if (!myAddedFiles.isEmpty()) {
            executeAdd();
          }
          if (!myDeletedFiles.isEmpty() || !myDeletedWithoutConfirmFiles.isEmpty()) {
            executeDelete();
          }
          if (!myMovedFiles.isEmpty()) {
            executeMoveRename();
          }
        }
      }
    }
  }
}