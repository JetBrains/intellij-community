// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.CompareWithSelectedRevisionAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.util.ObjectUtils.*;

public class GitCompareWithSelectedRevisionAction extends CompareWithSelectedRevisionAction {

  @Override
  protected void showSelectedRevision(@NotNull AbstractVcs vcs,
                                      @NotNull VcsFileRevision revision,
                                      @NotNull VirtualFile file,
                                      @NotNull Project project) {
    if (isForDirectory(file)) {
      final GitRepository repo = notNull(GitUtil.getRepositoryManager(project).getRepositoryForFile(file));
      VcsDiffUtil.showDiffWithRevisionUnderModalProgress(
        project,
        file,
        new GitRevisionNumber(assertNotNull(repo.getCurrentRevision())),
        new GitRevisionNumber(revision.getRevisionNumber().asString()),
        GitCompareWithSelectedRevisionAction::getDiffChanges
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
                                                   @NotNull GitRevisionNumber revision) throws VcsException {
    assert file.isDirectory();
    final GitRepository repo = GitUtil.getRepositoryManager(project).getRepositoryForFile(file);
    if (repo == null) {
      throw new VcsException("Couldn't find Git Repository for " + file.getName());
    }
    FilePath filePath = VcsUtil.getFilePath(file);
    assert filePath.isDirectory();
    return GitChangeUtils.getDiffWithWorkingDir(project, repo.getRoot(), revision.asString(), Collections.singletonList(filePath), false);
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
    final GitRepository repo = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath);
    return repo != null && !repo.isFresh();
  }
}
