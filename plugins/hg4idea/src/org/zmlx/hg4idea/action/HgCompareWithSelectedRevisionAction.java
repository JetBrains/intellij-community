// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.CompareWithSelectedRevisionAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.ObjectUtils.notNull;

public class HgCompareWithSelectedRevisionAction extends CompareWithSelectedRevisionAction {

  @Override
  protected void showSelectedRevision(@NotNull AbstractVcs vcs,
                                      @NotNull VcsFileRevision revision,
                                      @NotNull VirtualFile file,
                                      @NotNull Project project) {
    if (isForDirectory(file)) {
      final HgRepository repo = notNull(HgUtil.getRepositoryManager(project).getRepositoryForFile(file));
      VcsDiffUtil.showDiffWithRevisionUnderModalProgress(
        project,
        file,
        HgRevisionNumber.getInstance("", assertNotNull(repo.getCurrentRevision())),
        (HgRevisionNumber) revision.getRevisionNumber(),
        HgCompareWithSelectedRevisionAction::getDiffChanges
      );
    }
    else {
      super.showSelectedRevision(vcs, revision, file, project);
    }
  }

  @Override
  public void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation) {
    if (isForDirectory(vcsContext)) {
      presentation.setVisible(isVisibleForDirectory(vcsContext));
      presentation.setEnabled(isEnabledForDirectory(vcsContext));
    }
    else {
      super.update(vcsContext, presentation);
    }
  }

  ////////////////////////////////////////////////////
  /// Implementation for directories

  @NotNull
  private static Collection<Change> getDiffChanges(@NotNull Project project, @NotNull VirtualFile file,
                                                   @NotNull HgRevisionNumber revision) throws VcsException {
    assert file.isDirectory();
    final HgRepository repo = HgUtil.getRepositoryManager(project).getRepositoryForFile(file);
    if (repo == null) {
      throw new VcsException("Couldn't find repository for " + file.getName());
    }
    FilePath filePath = VcsUtil.getFilePath(file);
    assert filePath.isDirectory();
    return HgUtil.getDiff(project, repo.getRoot(), filePath, revision, null);
  }

  private static boolean isForDirectory(@NotNull VcsContext vcsContext) {
    VirtualFile file = vcsContext.getSelectedFile();
    return file != null && isForDirectory(file);
  }

  private static boolean isForDirectory(@NotNull VirtualFile file) {
    return file.isDirectory();
  }

  private static boolean isVisibleForDirectory(@NotNull VcsContext vcsContext) {
    return vcsContext.getProject() != null;
  }

  private static boolean isEnabledForDirectory(@NotNull VcsContext vcsContext) {
    Project project = vcsContext.getProject();
    if (project == null) return false;
    VirtualFile file = vcsContext.getSelectedFile();
    if (file == null || !file.isDirectory()) return false;
    FilePath filePath = assertNotNull(vcsContext.getSelectedFilePath());
    final HgRepository repo = HgUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath);
    return repo != null && !repo.isFresh();
  }
}
