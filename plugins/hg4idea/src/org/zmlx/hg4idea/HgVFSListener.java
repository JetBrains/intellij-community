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

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.VcsBackgroundTask;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.provider.HgLocalIgnoredHolder;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Listens to VFS events (such as adding or deleting bunch of files) and performs necessary operations with the VCS.
 */
public class HgVFSListener extends VcsVFSListener {

  private final VcsDirtyScopeManager dirtyScopeManager;
  private static final Logger LOG = Logger.getInstance(HgVFSListener.class);

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
  protected void executeAdd(final List<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap) {
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
    // exclude files which are ignored in .hgignore in background and execute adding after that
    final Map<VirtualFile, Collection<VirtualFile>> sortedFiles = HgUtil.sortByHgRoots(myProject, addedFiles);
    final HashSet<VirtualFile> untrackedFiles = new HashSet<>();
    new Task.Backgroundable(myProject, HgVcsMessages.message("hg4idea.progress.checking.ignored"), false) {
      @Override
      public void run(@NotNull ProgressIndicator pi) {
        for (Map.Entry<VirtualFile, Collection<VirtualFile>> e : sortedFiles.entrySet()) {
          VirtualFile repo = e.getKey();
          final Collection<VirtualFile> files = e.getValue();
          pi.setText(repo.getPresentableUrl());
          Collection<VirtualFile> untrackedForRepo = new HgStatusCommand.Builder(false).unknown(true).removed(true).build(myProject)
            .getFiles(repo, new ArrayList<>(files));
          untrackedFiles.addAll(untrackedForRepo);
          List<VirtualFile> ignoredForRepo = files.stream().filter(file -> !untrackedForRepo.contains(file)).collect(Collectors.toList());
          getIgnoreRepoHolder(repo).addFiles(ignoredForRepo);
        }
        addedFiles.retainAll(untrackedFiles);
        // select files to add if there is something to select
        if (!addedFiles.isEmpty() || !copyFromMap.isEmpty()) {

          AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> originalExecuteAdd(addedFiles, copyFromMap));
        }
      }
    }.queue();
  }

  @NotNull
  HgLocalIgnoredHolder getIgnoreRepoHolder(@NotNull VirtualFile repoRoot) {
    return ObjectUtils.assertNotNull(HgUtil.getRepositoryManager(myProject).getRepositoryForRootQuick(repoRoot)).getLocalIgnoredHolder();
  }
  /**
   * The version of execute add before overriding
   *
   * @param addedFiles  the added files
   * @param copiedFiles the copied files
   */
  private void originalExecuteAdd(List<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copiedFiles) {
    super.executeAdd(addedFiles, copiedFiles);
  }

  @Override
  protected void performAdding(final Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap) {
    (new Task.ConditionalModal(myProject,
                               HgVcsMessages.message("hg4idea.add.progress"),
                               false,
                               VcsConfiguration.getInstance(myProject).getAddRemoveOption() ) {
      @Override public void run(@NotNull ProgressIndicator aProgressIndicator) {
        final ArrayList<VirtualFile> adds = new ArrayList<>();
        final HashMap<VirtualFile, VirtualFile> copies = new HashMap<>(); // from -> to
        //delete unversioned and ignored files from copy source
        LOG.assertTrue(myProject != null, "Project is null");
        Collection<VirtualFile> unversionedAndIgnoredFiles = new ArrayList<>();
        final Map<VirtualFile, Collection<VirtualFile>> sortedSourceFilesByRepos = HgUtil.sortByHgRoots(myProject, copyFromMap.values());
        HgStatusCommand statusCommand = new HgStatusCommand.Builder(false).unknown(true).ignored(true).build(myProject);
        for (Map.Entry<VirtualFile, Collection<VirtualFile>> entry : sortedSourceFilesByRepos.entrySet()) {
          Set<HgChange> changes =
            statusCommand.executeInCurrentThread(entry.getKey(), ContainerUtil.map(entry.getValue(),
                                                                                   virtualFile -> VcsUtil.getFilePath(virtualFile)));
          for (HgChange change : changes) {
            unversionedAndIgnoredFiles.add(change.afterFile().toFilePath().getVirtualFile());
          }
        }
        copyFromMap.values().removeAll(unversionedAndIgnoredFiles);

        // separate adds from copies
        for (VirtualFile file : addedFiles) {
          if (file.isDirectory()) {
            continue;
          }

          final VirtualFile copyFrom = copyFromMap.get(file);
          if (copyFrom != null) {
            copies.put(copyFrom, file);
          } else {
            adds.add(file);
          }
        }

        // add for all files at once
        if (!adds.isEmpty()) {
          new HgAddCommand(myProject).executeInCurrentThread(adds);
        }

        // copy needs to be run for each file separately
        if (!copies.isEmpty()) {
          for (Map.Entry<VirtualFile, VirtualFile> copy : copies.entrySet()) {
            new HgCopyCommand(myProject).executeInCurrentThread(copy.getKey(), copy.getValue());
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
  protected VcsDeleteType needConfirmDeletion(final VirtualFile file) {
    return ChangeListManagerImpl.getInstanceImpl(myProject).getUnversionedFiles().contains(file)
           ? VcsDeleteType.IGNORE
           : VcsDeleteType.CONFIRM;
  }

  protected void executeDelete() {
    final List<FilePath> filesToDelete = new ArrayList<>(myDeletedWithoutConfirmFiles);
    final List<FilePath> filesToConfirmDeletion = new ArrayList<>(myDeletedFiles);
    myDeletedWithoutConfirmFiles.clear();
    myDeletedFiles.clear();

    // skip files which are not under Mercurial
    skipNotUnderHg(filesToDelete);
    skipNotUnderHg(filesToConfirmDeletion);

    filesToDelete.removeAll(processAndGetVcsIgnored(filesToDelete));
    filesToConfirmDeletion.removeAll(processAndGetVcsIgnored(filesToConfirmDeletion));

    // newly added files (which were added to the repo but never committed) should be removed from the VCS,
    // but without user confirmation.
    for (Iterator<FilePath> it = filesToConfirmDeletion.iterator(); it.hasNext(); ) {
      FilePath filePath = it.next();
      Change fileChange = ChangeListManager.getInstance(myProject).getChange(filePath);
      if (fileChange != null && fileChange.getFileStatus().equals(FileStatus.ADDED)) {
        filesToDelete.add(filePath);
        it.remove();
      }
    }

    new Task.ConditionalModal(myProject,
                              HgVcsMessages.message("hg4idea.remove.progress"),
                              false,
                              VcsConfiguration.getInstance(myProject).getAddRemoveOption()) {
      @Override public void run( @NotNull ProgressIndicator indicator ) {
        // confirm removal from the VCS if needed
        if (myRemoveOption.getValue() != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
          if (myRemoveOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY || filesToConfirmDeletion.isEmpty()) {
            filesToDelete.addAll(filesToConfirmDeletion);
          }
          else {
            final AtomicReference<Collection<FilePath>> filePaths = new AtomicReference<>();
            ApplicationManager.getApplication().invokeAndWait(() -> filePaths.set(selectFilePathsToDelete(filesToConfirmDeletion)));
            if (filePaths.get() != null) {
              filesToDelete.addAll(filePaths.get());
            }
          }
        }

        if (!filesToDelete.isEmpty()) {
          performDeletion(filesToDelete);
        }
      }
    }.queue();
  }

  @NotNull
  private List<FilePath> processAndGetVcsIgnored(@NotNull List<FilePath> filePaths) {
    Map<VirtualFile, Collection<FilePath>> groupFilePathsByHgRoots = HgUtil.groupFilePathsByHgRoots(myProject, filePaths);
    return groupFilePathsByHgRoots.entrySet().stream()
      .map(entry -> getIgnoreRepoHolder(entry.getKey()).removeIgnoredFiles(entry.getValue()))
      .flatMap(List::stream).collect(Collectors.toList());
  }

  /**
   * Changes the given collection of files by filtering out unversioned files and
   * files which are not under Mercurial repository.
   *
   * @param filesToFilter    files to be filtered.
   */
  private void skipNotUnderHg(Collection<FilePath> filesToFilter) {
    for (Iterator<FilePath> iter = filesToFilter.iterator(); iter.hasNext(); ) {
      final FilePath filePath = iter.next();
      if (HgUtil.getHgRootOrNull(myProject, filePath) == null) {
        iter.remove();
      }
    }
  }

  @Override
  protected void performDeletion( final List<FilePath> filesToDelete) {
    final ArrayList<HgFile> deletes = new ArrayList<>();
    for (FilePath file : filesToDelete) {
      if (file.isDirectory()) {
        continue;
      }

      VirtualFile root = VcsUtil.getVcsRootFor(myProject, file);
      if (root != null) {
        deletes.add(new HgFile(root, file));
      }
    }

    if (!deletes.isEmpty()) {
      new HgRemoveCommand(myProject).executeInCurrentThread(deletes);
    }

    for (HgFile file : deletes) {
      dirtyScopeManager.fileDirty(file.toFilePath());
    }
  }

  @Override
  protected void performMoveRename(List<MovedFileInfo> movedFiles) {
    (new VcsBackgroundTask<MovedFileInfo>(myProject,
                                        HgVcsMessages.message("hg4idea.move.progress"),
                                        VcsConfiguration.getInstance(myProject).getAddRemoveOption(),
                                        movedFiles) {
      protected void process(final MovedFileInfo file) {
        final FilePath source = VcsUtil.getFilePath(file.myOldPath);
        final FilePath target = VcsUtil.getFilePath(file.myNewPath);
        VirtualFile sourceRoot = VcsUtil.getVcsRootFor(myProject, source);
        VirtualFile targetRoot = VcsUtil.getVcsRootFor(myProject, target);
        if (sourceRoot != null && targetRoot != null) {
          (new HgMoveCommand(myProject)).execute(new HgFile(sourceRoot, source), new HgFile(targetRoot, target));
        }
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
