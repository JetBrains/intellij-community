// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.status;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.changes.GitChangeUtils;
import git4idea.changes.GitChangeUtils.GitDiffChange;
import git4idea.index.GitFileStatus;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * <p>
 * Collects changes from the Git repository in the given {@link VcsDirtyScope}
 * by calling {@code 'git status --porcelain -z'} on it.
 * Works only on Git 1.7.0 and later.
 * </p>
 * <p>
 * The class is immutable: collect changes and get the instance from where they can be retrieved by {@link #collect}.
 * </p>
 *
 * @author Kirill Likhodedov
 */
final class GitChangesCollector {
  private static final Logger LOG = Logger.getInstance(GitChangesCollector.class);

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myVcsRoot;

  private final VcsRevisionNumber myHead;
  private final Collection<Change> myChanges = new HashSet<>();

  /**
   * Collects the changes from git command line and returns the instance of GitNewChangesCollector from which these changes can be retrieved.
   * This may be lengthy.
   */
  @NotNull
  static GitChangesCollector collect(@NotNull Project project,
                                     @NotNull GitRepository repository,
                                     @NotNull List<GitFileStatus> changes) throws VcsException {
    VcsRevisionNumber head = getHead(repository);

    GitChangesCollector collector = new GitChangesCollector(project, repository.getRoot(), head);
    collector.collectChanges(changes);
    return collector;
  }

  @NotNull
  VcsRevisionNumber getHead() {
    return myHead;
  }

  @NotNull
  Collection<Change> getChanges() {
    return myChanges;
  }

  private GitChangesCollector(@NotNull Project project, @NotNull VirtualFile root, @NotNull VcsRevisionNumber head) {
    myProject = project;
    myVcsRoot = root;
    myHead = head;
  }

  private void collectChanges(List<GitFileStatus> changes) throws VcsException {
    List<FilePath> bothModifiedPaths = new ArrayList<>();

    for (GitFileStatus change : changes) {
      final char xStatus = change.getIndex();
      final char yStatus = change.getWorkTree();
      final FilePath filepath = change.getPath();
      final FilePath oldFilepath = change.getOrigPath();

      switch (xStatus) {
        case ' ':
          if (yStatus == 'M') {
            reportModified(filepath);
          }
          else if (yStatus == 'D') {
            reportDeleted(filepath);
          }
          else if (yStatus == 'A' || yStatus == 'C') {
            reportAdded(filepath);
          }
          else if (yStatus == 'T') {
            reportTypeChanged(filepath);
          }
          else if (yStatus == 'U') {
            reportConflict(filepath);
          }
          else if (yStatus == 'R') {
            reportRename(filepath, oldFilepath);
          }
          else {
            throwStatus(xStatus, yStatus);
          }
          break;

        case 'M':
          if (yStatus == 'M') {
            bothModifiedPaths.add(filepath); // schedule 'git diff HEAD' command to detect staged changes, that were reverted
          }
          else if (yStatus == ' ' || yStatus == 'T') {
            reportModified(filepath);
          }
          else if (yStatus == 'D') {
            reportDeleted(filepath);
          }
          else {
            throwStatus(xStatus, yStatus);
          }
          break;

        case 'C':
        case 'A':
          if (yStatus == 'M' || yStatus == ' ' || yStatus == 'T') {
            reportAdded(filepath);
          }
          else if (yStatus == 'D') {
            // added + deleted => no change (from IDEA point of view).
          }
          else if (yStatus == 'U') { // AU - unmerged, added by us
            reportConflict(filepath);
          }
          else if (yStatus == 'A') { // AA - unmerged, both added
            reportConflict(filepath);
          }
          else {
            throwStatus(xStatus, yStatus);
          }
          break;

        case 'D':
          if (yStatus == 'M' || yStatus == ' ' || yStatus == 'T') {
            reportDeleted(filepath);
          }
          else if (yStatus == 'U') { // DU - unmerged, deleted by us
            reportConflict(filepath);
          }
          else if (yStatus == 'D') { // DD - unmerged, both deleted
            reportConflict(filepath);
          }
          else if (yStatus == 'C') {
            reportModified(filepath);
          }
          else if (yStatus == 'R') {
            reportRename(filepath, oldFilepath);
          }
          else if (yStatus == 'A') {
            // [DA] status is not documented, but might be reported by git
            reportModified(filepath);
          }
          else {
            throwStatus(xStatus, yStatus);
          }
          break;

        case 'U':
          if (yStatus == 'U' || yStatus == 'T') { // UU - unmerged, both modified
            reportConflict(filepath);
          }
          else if (yStatus == 'A') { // UA - unmerged, added by them
            reportConflict(filepath);
          }
          else if (yStatus == 'D') { // UD - unmerged, deleted by them
            reportConflict(filepath);
          }
          else {
            throwStatus(xStatus, yStatus);
          }
          break;

        case 'R':
          if (yStatus == 'D') {
            reportDeleted(oldFilepath);
          }
          else if (yStatus == ' ' || yStatus == 'M' || yStatus == 'T') {
            reportRename(filepath, oldFilepath);
          }
          else {
            throwStatus(xStatus, yStatus);
          }
          break;

        case 'T':
          if (yStatus == ' ' || yStatus == 'M') {
            reportTypeChanged(filepath);
          }
          else if (yStatus == 'D') {
            reportDeleted(filepath);
          }
          else if (yStatus == 'T') {
            reportConflict(filepath);
          }
          else {
            throwStatus(xStatus, yStatus);
          }
          break;

        default:
          throwStatus(xStatus, yStatus);
      }
    }

    collectStagedUnstagedModifications(bothModifiedPaths);
  }

  private void collectStagedUnstagedModifications(@NotNull List<FilePath> bothModifiedPaths) throws VcsException {
    if (bothModifiedPaths.isEmpty()) return;

    Collection<GitDiffChange> changes = GitChangeUtils.getWorkingTreeChanges(myProject, myVcsRoot, bothModifiedPaths, false);

    // no directories expected here, hierarchical comparator is not necessary
    Set<FilePath> expectedPaths = new HashSet<>(bothModifiedPaths);

    for (GitDiffChange change : changes) {
      FilePath filePath = change.getFilePath();
      if (expectedPaths.contains(filePath)) {
        reportModified(filePath);
      }
    }
  }

  @NotNull
  static VcsRevisionNumber getHead(@NotNull GitRepository repository) {
    // we force update the GitRepository, because update is asynchronous, and thus the GitChangeProvider may be asked for changes
    // before the GitRepositoryUpdater has captures the current revision change and has updated the GitRepository.
    repository.update();
    final String rev = repository.getCurrentRevision();
    return rev != null ? new GitRevisionNumber(rev) : VcsRevisionNumber.NULL;
  }

  private static void throwStatus(char xStatus, char yStatus) throws VcsException {
    throw new VcsException(String.format("Unexpected symbol as status: '%s%s'", xStatus, yStatus));
  }

  private void reportModified(FilePath filepath) {
    ContentRevision before = GitContentRevision.createRevision(filepath, myHead, myProject);
    ContentRevision after = GitContentRevision.createRevision(filepath, null, myProject);
    reportChange(FileStatus.MODIFIED, before, after);
  }

  private void reportTypeChanged(FilePath filepath) {
    ContentRevision before = GitContentRevision.createRevision(filepath, myHead, myProject);
    ContentRevision after = GitContentRevision.createRevisionForTypeChange(filepath, null, myProject);
    reportChange(FileStatus.MODIFIED, before, after);
  }

  private void reportAdded(FilePath filepath) {
    ContentRevision before = null;
    ContentRevision after = GitContentRevision.createRevision(filepath, null, myProject);
    reportChange(FileStatus.ADDED, before, after);
  }

  private void reportDeleted(FilePath filepath) {
    ContentRevision before = GitContentRevision.createRevision(filepath, myHead, myProject);
    ContentRevision after = null;
    reportChange(FileStatus.DELETED, before, after);
  }

  private void reportRename(FilePath filepath, FilePath oldFilepath) {
    ContentRevision before = GitContentRevision.createRevision(oldFilepath, myHead, myProject);
    ContentRevision after = GitContentRevision.createRevision(filepath, null, myProject);
    reportChange(FileStatus.MODIFIED, before, after);
  }

  private void reportConflict(FilePath filepath) {
    ContentRevision before = GitContentRevision.createRevision(filepath, myHead, myProject);
    ContentRevision after = GitContentRevision.createRevision(filepath, null, myProject);
    reportChange(FileStatus.MERGED_WITH_CONFLICTS, before, after);
  }

  private void reportChange(FileStatus status, ContentRevision before, ContentRevision after) {
    myChanges.add(new Change(before, after, status));
  }
}
