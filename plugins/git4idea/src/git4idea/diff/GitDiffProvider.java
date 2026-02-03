// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.diff;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffMixin;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitFileRevision;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.history.GitFileHistory;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.stash.GitRevisionContentPreLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.vcs.history.VcsDiffUtil.createChangesWithCurrentContentForFile;

/**
 * Git diff provider
 */
@Service(Service.Level.PROJECT)
public final class GitDiffProvider implements DiffProvider, DiffMixin {
  /**
   * The context project
   */
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project the context project
   */
  public GitDiffProvider(@NotNull Project project) {
    myProject = project;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @Nullable VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    if (file.isDirectory()) {
      return null;
    }
    try {
      return GitHistoryUtils.getCurrentRevision(myProject, VcsUtil.getFilePath(file), "HEAD");
    }
    catch (VcsException e) {
      return null;
    }
  }

  @Override
  public @Nullable VcsRevisionDescription getCurrentRevisionDescription(final VirtualFile file) {
    if (file.isDirectory()) {
      return null;
    }
    try {
      return GitHistoryUtils.getCurrentRevisionDescription(myProject, VcsUtil.getFilePath(file));
    }
    catch (VcsException e) {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @Nullable ItemLatestState getLastRevision(VirtualFile file) {
    if (file.isDirectory()) {
      return null;
    }
    if (!hasGoodFileStatus(file)) {
      return null;
    }
    try {
      return GitHistoryUtils.getLastRevision(myProject, VcsUtil.getFilePath(file));
    }
    catch (VcsException e) {
      return null;
    }
  }

  private boolean hasGoodFileStatus(VirtualFile file) {
    FileStatus status = ChangeListManager.getInstance(myProject).getStatus(file);
    return status == FileStatus.NOT_CHANGED ||
           status == FileStatus.DELETED ||
           status == FileStatus.MODIFIED ||
           status == FileStatus.MERGED_WITH_CONFLICTS;
  }

  @Override
  public @Nullable ContentRevision createCurrentFileContent(@NotNull VirtualFile file) {
    if (file.isDirectory()) return null;
    if (GitRepositoryManager.getInstance(myProject).getRepositoryForFile(file) == null) return null;

    VcsRevisionNumber revisionNumber = getCurrentRevision(file);
    FilePath filePath = VcsUtil.getLastCommitPath(myProject, VcsUtil.getFilePath(file));
    return GitContentRevision.createRevision(filePath, revisionNumber, myProject, file.getCharset());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public @Nullable ContentRevision createFileContent(VcsRevisionNumber revisionNumber, VirtualFile selectedFile) {
    if (selectedFile.isDirectory()) {
      return null;
    }
    if (GitRepositoryManager.getInstance(myProject).getRepositoryForFile(selectedFile) == null) {
      return null;
    }

    // faster, if there were no renames
    FilePath filePath = VcsUtil.getFilePath(selectedFile);
    try {
      final CommittedChangesProvider committedChangesProvider = GitVcs.getInstance(myProject).getCommittedChangesProvider();
      final Pair<CommittedChangeList, FilePath> pair = committedChangesProvider.getOneList(selectedFile, revisionNumber);
      if (pair != null) {
        return GitContentRevision.createRevision(pair.getSecond(), revisionNumber, myProject, selectedFile.getCharset());
      }
    }
    catch (VcsException e) {
      GitVcs.getInstance(myProject)
        .showErrors(Collections.singletonList(e), GitBundle.message("diff.find.error", selectedFile.getPresentableUrl()));
    }

    try {

      for (VcsFileRevision f : GitFileHistory.collectHistory(myProject, filePath)) {
        GitFileRevision gitRevision = (GitFileRevision)f;
        if (f.getRevisionNumber().equals(revisionNumber)) {
          return GitContentRevision.createRevision(gitRevision.getPath(), revisionNumber, myProject, selectedFile.getCharset());
        }
      }
      GitContentRevision candidate =
        (GitContentRevision)GitContentRevision.createRevision(filePath, revisionNumber, myProject,
                                                              selectedFile.getCharset());
      try {
        candidate.getContent();
        return candidate;
      }
      catch (VcsException e) {
        // file does not exists
      }
    }
    catch (VcsException e) {
      GitVcs.getInstance(myProject)
        .showErrors(Collections.singletonList(e), GitBundle.message("diff.find.error", selectedFile.getPresentableUrl()));
    }
    return null;
  }

  @Override
  public ItemLatestState getLastRevision(FilePath filePath) {
    if (filePath.isDirectory()) {
      return null;
    }
    final VirtualFile vf = filePath.getVirtualFile();
    if (vf != null) {
      if (!hasGoodFileStatus(vf)) {
        return null;
      }
    }
    try {
      return GitHistoryUtils.getLastRevision(myProject, filePath);
    }
    catch (VcsException e) {
      return null;
    }
  }

  @Override
  public VcsRevisionNumber getLatestCommittedRevision(VirtualFile vcsRoot) {
    // todo
    return null;
  }

  @Override
  public void preloadBaseRevisions(@NotNull VirtualFile root, @NotNull Collection<Change> revisions) {
    new GitRevisionContentPreLoader(myProject).preload(root, revisions);
  }

  @Override
  public boolean canCompareWithWorkingDir() {
    return true;
  }

  @Override
  public @NotNull Collection<Change> compareWithWorkingDir(@NotNull VirtualFile fileOrDir,
                                                           @NotNull VcsRevisionNumber revNum) throws VcsException {
    final GitRepository repo = GitUtil.getRepositoryForFile(myProject, fileOrDir);
    FilePath filePath = VcsUtil.getFilePath(fileOrDir);

    final Collection<Change> changes = GitChangeUtils.getDiffWithWorkingDir(myProject, repo.getRoot(), revNum.asString(),
                                                                            Collections.singletonList(filePath), false);
    return changes.isEmpty() && !filePath.isDirectory()
           ? createChangesWithCurrentContentForFile(filePath, GitContentRevision.createRevision(filePath, revNum, myProject))
           : changes;
  }
}
