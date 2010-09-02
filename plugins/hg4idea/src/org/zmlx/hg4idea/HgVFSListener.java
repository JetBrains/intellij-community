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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.VcsBackgroundTask;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
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
  protected void performAdding(final Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap) {
    (new Task.ConditionalModal(myProject,
                               HgVcsMessages.message("hg4idea.add.progress"),
                               false,
                               VcsConfiguration.getInstance(myProject).getAddRemoveOption() ) {
      @Override public void run(@NotNull ProgressIndicator aProgressIndicator) {
        final ArrayList<HgFile> adds = new ArrayList<HgFile>();
        final HashMap<HgFile, HgFile> copies = new HashMap<HgFile, HgFile>(); // from -> to

        // separate adds from copies
        for (VirtualFile file : addedFiles) {
          if (file.isDirectory()) {
            continue;
          }

          final VirtualFile copyFrom = copyFromMap.get(file);
          if (copyFrom != null) {
            copies.put(new HgFile(myProject, copyFrom), new HgFile(myProject, file));
          } else {
            adds.add(new HgFile(myProject, file));
          }
        }

        // add for all files at once
        if (!adds.isEmpty()) {
          new HgAddCommand(myProject).execute(adds);
        }

        // copy needs to be run for each file separately
        if (!copies.isEmpty()) {
          for(Map.Entry<HgFile, HgFile> copy : copies.entrySet()) {
            new HgCopyCommand(myProject).execute(copy.getKey(), copy.getValue());
          }
        }

        for (VirtualFile file : addedFiles) {
          dirtyScopeManager.fileDirty(file);
        }
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

  @Override
  protected VcsDeleteType needConfirmDeletion(VirtualFile file) {
    //// newly added files (which were added to the repo but never committed) should be removed from the VCS,
    //// but without user confirmation.
    final FilePath filePath = VcsUtil.getFilePath(file.getPath());
    final VirtualFile repo = HgUtil.getHgRootOrNull(myProject, filePath);
    if (repo == null) {
      return super.needConfirmDeletion(file);
    }
    final HgFile hgFile = new HgFile(repo, filePath);

    final HgLogCommand logCommand = new HgLogCommand(myProject);
    logCommand.setLogFile(true);
    logCommand.setFollowCopies(false);
    logCommand.setIncludeRemoved(true);
    final List<HgFileRevision> localRevisions = logCommand.execute(hgFile, -1, true);
    // file is newly added, if it doesn't have a history or if the last history action was deleting this file.
    if (localRevisions == null || localRevisions.isEmpty() || localRevisions.get(0).getDeletedFiles().contains(hgFile.getRelativePath())) {
      return VcsDeleteType.SILENT;
    }
    return VcsDeleteType.CONFIRM;
  }

  protected void executeDelete() {
    final List<FilePath> filesToDelete = new ArrayList<FilePath>(myDeletedWithoutConfirmFiles);
    final List<FilePath> deletedFiles = new ArrayList<FilePath>(myDeletedFiles);
    myDeletedWithoutConfirmFiles.clear();
    myDeletedFiles.clear();

    // skip unversioned files and files which are not under Mercurial
    final ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
    skipUnversionedAndNotUnderHg(changeListManager, filesToDelete);
    skipUnversionedAndNotUnderHg(changeListManager, deletedFiles);

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
     * Changes the given collection of files by filtering out unversioned files and
     * files which are not under Mercurial repository.
     * @param changeListManager instance of the ChangeListManagerImpl to retrieve unversioned files from it.
     * @param filesToFilter     files to be filtered.
     */
  private void skipUnversionedAndNotUnderHg(ChangeListManagerImpl changeListManager, Collection<FilePath> filesToFilter) {
    for (Iterator<FilePath> iter = filesToFilter.iterator(); iter.hasNext(); ) {
      final FilePath filePath = iter.next();
      if (HgUtil.getHgRootOrNull(myProject, filePath) == null || changeListManager.isUnversioned(filePath.getVirtualFile())) {
        iter.remove();
      }
    }
  }

  @Override
  protected void performDeletion( final List<FilePath> filesToDelete) {
    (new Task.ConditionalModal(myProject,
                                        HgVcsMessages.message("hg4idea.remove.progress"),
                                        false,
                                        VcsConfiguration.getInstance(myProject).getAddRemoveOption()) {
      @Override public void run( @NotNull ProgressIndicator aProgressIndicator ) {
        final ArrayList<HgFile> deletes = new ArrayList<HgFile>();
        for (FilePath file : filesToDelete) {
          if (file.isDirectory()) {
            continue;
          }

          deletes.add(new HgFile(VcsUtil.getVcsRootFor(myProject, file), file));
        }

        if (!deletes.isEmpty()) {
          new HgRemoveCommand(myProject).execute(deletes);
        }

        for (HgFile file : deletes) {
          dirtyScopeManager.fileDirty(file.toFilePath());
        }
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
