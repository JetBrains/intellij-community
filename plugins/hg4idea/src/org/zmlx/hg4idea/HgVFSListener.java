/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.VcsBackgroundTask;
import com.intellij.vcsUtil.VcsUtil;
import org.zmlx.hg4idea.command.*;

import java.util.*;

/**
 * Listens to VFS events (such as adding or deleting bunch of files) and performs necessary operations with the VCS.
 * @author Kirill Likhodedov
 */
public class HgVFSListener extends VcsVFSListener {

  private final VcsDirtyScopeManager dirtyScopeManager;

  protected HgVFSListener(final Project project, final HgVcs vcs) {
    super(project, vcs);
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  @Override
  protected String getAddTitle() {
    return HgVcsMessages.message("hg4idea.add.title");
  }

  @Override
  protected String getSingleFileAddTitle() {
    return HgVcsMessages.message("hg4idea.add.single.title");
  }

  @Override
  protected String getSingleFileAddPromptTemplate() {
    return HgVcsMessages.message("hg4idea.add.body");
  }

  @Override
  protected void executeAdd(List<VirtualFile> addedFiles, Map<VirtualFile, VirtualFile> copyFromMap) {
    // if a file is copied from another repository, then 'hg add' should be used instead of 'hg copy'.
    // Thus here we remove such files from the copyFromMap.
    for (Iterator<Map.Entry<VirtualFile, VirtualFile>> it = copyFromMap.entrySet().iterator(); it.hasNext(); ) {
      final Map.Entry<VirtualFile, VirtualFile> entry = it.next();
      final VirtualFile rootFrom = HgUtil.getHgRootOrNull(myProject, entry.getKey());
      final VirtualFile rootTo = HgUtil.getHgRootOrNull(myProject, entry.getValue());

      if (rootTo == null || !rootTo.equals(rootFrom)) {
        it.remove();
      }
    }

    // exclude files which are added to a directory which is not version controlled
    for (Iterator<VirtualFile> it = addedFiles.iterator(); it.hasNext(); ) {
      if (HgUtil.getHgRootOrNull(myProject, it.next()) == null) {
        it.remove();
      }
    }

    // select files to add if there is something to select
    if (!addedFiles.isEmpty() || !copyFromMap.isEmpty()) {
      super.executeAdd(addedFiles, copyFromMap);
    }
  }

  @Override
  protected void performAdding(Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap) {
    (new VcsBackgroundTask<VirtualFile>(myProject,
                                        HgVcsMessages.message("hg4idea.add.progress"),
                                        VcsConfiguration.getInstance(myProject).getAddRemoveOption(),
                                        addedFiles) {
      protected void process(final VirtualFile file) throws VcsException {
        if (file.isDirectory()) {
          return;
        }
        final VirtualFile copyFrom = copyFromMap.get(file);
        if (copyFrom != null) {
          (new HgCopyCommand(myProject)).execute(new HgFile(myProject, copyFrom), new HgFile(myProject, file));
        } else {
          (new HgAddCommand(myProject)).execute(new HgFile(myProject, file));
        }
        dirtyScopeManager.fileDirty(file);
      }

    }).queue();
  }

  @Override
  protected String getDeleteTitle() {
    return HgVcsMessages.message("hg4idea.remove.multiple.title");
  }

  @Override
  protected String getSingleFileDeleteTitle() {
    return HgVcsMessages.message("hg4idea.remove.single.title");
  }

  @Override
  protected String getSingleFileDeletePromptTemplate() {
    return HgVcsMessages.message("hg4idea.remove.single.body");
  }

  protected void executeDelete() {
    final List<FilePath> filesToDelete = new ArrayList<FilePath>(myDeletedWithoutConfirmFiles);
    final List<FilePath> deletedFiles = new ArrayList<FilePath>(myDeletedFiles);
    myDeletedWithoutConfirmFiles.clear();
    myDeletedFiles.clear();

    // skip unversioned files and files which are not under Mercurial
    final List<FilePath> unversionedFilePaths = new ArrayList<FilePath>();
    for (VirtualFile vf : ChangeListManagerImpl.getInstanceImpl(myProject).getUnversionedFiles()) {
      unversionedFilePaths.add(VcsUtil.getFilePath(vf.getPath()));
    }
    skipUnversionedAndNotUnderHg(unversionedFilePaths, filesToDelete);
    skipUnversionedAndNotUnderHg(unversionedFilePaths, deletedFiles);

    // newly added files (which were added to the repo but never committed should be removed from the VCS,
    // but without user confirmation.
    for (Iterator<FilePath> it = deletedFiles.iterator(); it.hasNext(); ) {
      final FilePath filePath = it.next();
      final HgLogCommand logCommand = new HgLogCommand(myProject);
      logCommand.setLogFile(true);
      logCommand.setFollowCopies(false);
      logCommand.setIncludeRemoved(true);
      final VirtualFile repo = HgUtil.getHgRootOrNull(myProject, filePath);
      if (repo == null) {
        continue;
      }
      final HgFile hgFile = new HgFile(repo, filePath);
      final List<HgFileRevision> localRevisions = logCommand.execute(hgFile, -1, true);
      // file is newly added, if it doesn't have a history or if the last history action was deleting this file.
      if (localRevisions == null || localRevisions.isEmpty() || localRevisions.get(0).getDeletedFiles().contains(hgFile.getRelativePath())) {
        it.remove();
        filesToDelete.add(filePath);
      }
    }

    // confirm removal from the VCS if needed
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

    if (!filesToDelete.isEmpty()) {
      performDeletion(filesToDelete);
    }
  }

    /**
     * Changes the given collection of files by filtering out unversioned files,
     * files which are not under Mercurial repository, and
     * newly added files (which were added to the repo, but never committed).
     * @param unversionedFiles  unversioned files retrieved from the ChangeListManager.
     *                          Passing as a parameter not to transform List<VirtualFile> to List<FilePath> twice.
     * @param filesToFilter     files to be filtered.
     */
    private void skipUnversionedAndNotUnderHg(Collection<FilePath> unversionedFiles, Collection<FilePath> filesToFilter) {
    for (Iterator<FilePath> iter = filesToFilter.iterator(); iter.hasNext(); ) {
      final FilePath filePath = iter.next();
      if (HgUtil.getHgRootOrNull(myProject, filePath) == null || unversionedFiles.contains(filePath)) {
        iter.remove();
      }
    }
  }

  @Override
  protected void performDeletion(List<FilePath> filesToDelete) {
    (new VcsBackgroundTask<FilePath>(myProject,
                                        HgVcsMessages.message("hg4idea.remove.progress"),
                                        VcsConfiguration.getInstance(myProject).getAddRemoveOption(),
                                        filesToDelete) {
      protected void process(final FilePath file) throws VcsException {
        if (file.isDirectory()) {
          return;
        }
        (new HgRemoveCommand(myProject)).execute(new HgFile(VcsUtil.getVcsRootFor(myProject, file), file));
        dirtyScopeManager.fileDirty(file);
      }

    }).queue();
  }

  @Override
  protected void performMoveRename(List<MovedFileInfo> movedFiles) {
    (new VcsBackgroundTask<MovedFileInfo>(myProject,
                                        HgVcsMessages.message("hg4idea.move.progress"),
                                        VcsConfiguration.getInstance(myProject).getAddRemoveOption(),
                                        movedFiles) {
      protected void process(final MovedFileInfo file) throws VcsException {
        final FilePath source = VcsUtil.getFilePath(file.myOldPath);
        final FilePath target = VcsUtil.getFilePath(file.myNewPath);
        (new HgMoveCommand(myProject)).execute(new HgFile(VcsUtil.getVcsRootFor(myProject, source), source), new HgFile(VcsUtil.getVcsRootFor(myProject, target), target));
        dirtyScopeManager.fileDirty(source);
        dirtyScopeManager.fileDirty(target);
      }

    }).queue();
  }

  @Override
  protected boolean isDirectoryVersioningSupported() {
    return false;
  }
}
