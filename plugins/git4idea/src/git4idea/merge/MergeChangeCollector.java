// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Collect changes for merge or pull operations
 */
public class MergeChangeCollector {
  @NotNull private final HashSet<String> myUnmergedPaths = new HashSet<>();

  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final GitRevisionNumber myStart; // Revision number before update (used for diff)
  @NotNull private final GitRepository myRepository;

  public MergeChangeCollector(@NotNull Project project, @NotNull GitRepository repository, @NotNull GitRevisionNumber start) {
    myStart = start;
    myProject = project;
    myRoot = repository.getRoot();
    myRepository = repository;
  }

  /**
   * Collects changed files during or after merge operation to the supplied container.
   */
  public void collect(@NotNull UpdatedFiles updatedFiles) throws VcsException {
    // collect unmerged
    Set<String> paths = getUnmergedPaths();
    addAll(updatedFiles, FileGroup.MERGED_WITH_CONFLICT_ID, paths);

    // collect other changes (ignoring unmerged)
    TreeSet<String> updated = new TreeSet<>();
    TreeSet<String> created = new TreeSet<>();
    TreeSet<String> removed = new TreeSet<>();

    String revisionsForDiff = getRevisionsForDiff();
    if (revisionsForDiff == null) {
      return;
    }
    getChangedFilesExceptUnmerged(updated, created, removed, revisionsForDiff);
    addAll(updatedFiles, FileGroup.UPDATED_ID, updated);
    addAll(updatedFiles, FileGroup.CREATED_ID, created);
    addAll(updatedFiles, FileGroup.REMOVED_FROM_REPOSITORY_ID, removed);
  }

  public int calcUpdatedFilesCount() throws VcsException {
    String revisionsForDiff = getRevisionsForDiff();
    if (revisionsForDiff == null) {
      return 0;
    }

    Set<String> updated = new HashSet<>();
    getChangedFilesExceptUnmerged(updated, updated, updated, revisionsForDiff);
    return updated.size() + getUnmergedPaths().size();
  }

  /**
   * Returns absolute paths to files which are currently unmerged, and also populates myUnmergedPaths with relative paths.
   */
  @NotNull
  private Set<String> getUnmergedPaths() throws VcsException {
    String root = myRoot.getPath();
    GitLineHandler h = new GitLineHandler(myProject, myRoot, GitCommand.LS_FILES);
    h.setSilent(true);
    h.addParameters("--unmerged");
    String output = Git.getInstance().runCommand(h).getOutputOrThrow();

    Set<String> paths = new HashSet<>();
    for (StringScanner s = new StringScanner(output); s.hasMoreData(); ) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      s.boundedToken('\t');
      String relative = s.line();
      if (!myUnmergedPaths.add(relative)) {
        continue;
      }
      String path = root + "/" + GitUtil.unescapePath(relative);
      paths.add(path);
    }
    return paths;
  }

  /**
   * @return The revision range which will be used to find merge diff (merge may be just finished, or in progress)
   * or null in case of error or inconsistency.
   */
  @Nullable
  public String getRevisionsForDiff() throws VcsException {
    GitRevisionNumber currentHead = GitRevisionNumber.resolve(myProject, myRoot, GitUtil.HEAD);
    if (currentHead.equals(myStart)) {
      // The head has not advanced. This means that this is a merge that did not commit.
      // This could be caused by --no-commit option or by failed two-head merge. The MERGE_HEAD
      // should be available. In case of --no-commit option, the MERGE_HEAD might contain
      // multiple heads separated by newline. The changes are collected separately for each head
      // and they are merged using TreeSet class (that also sorts the changes).
      File mergeHeadsFile = myRepository.getRepositoryFiles().getMergeHeadFile();
      try {
        if (mergeHeadsFile.exists()) {
          String mergeHeads = new String(FileUtil.loadFileText(mergeHeadsFile, CharsetToolkit.UTF8));
          for (StringScanner s = new StringScanner(mergeHeads); s.hasMoreData(); ) {
            String head = s.line();
            if (head.length() == 0) {
              continue;
            }
            // note that "..." cause the diff to start from common parent between head and merge head
            return myStart.getRev() + "..." + head;
          }
        }
      }
      catch (IOException e) {
        throw new VcsException(GitBundle.message("merge.error.unable.to.read.merge.head", mergeHeadsFile, e.getLocalizedMessage()), e);
      }
    }
    else {
      // Otherwise this is a merge that did created a commit. And because of this the incoming changes
      // are diffs between old head and new head. The commit could have been multihead commit,
      // and the expression below considers it as well.
      return myStart.getRev() + ".." + GitUtil.HEAD;
    }
    return null;
  }

  /**
   * Populates the supplied collections of modified, created and removed files returned by 'git diff #revisions' command,
   * where revisions is the range of revisions to check.
   */
  private void getChangedFilesExceptUnmerged(@NotNull Collection<? super String> updated,
                                             @NotNull Collection<? super String> created,
                                             @NotNull Collection<? super String> removed,
                                             @NotNull String revisions) throws VcsException {
    String root = myRoot.getPath();
    GitLineHandler h = new GitLineHandler(myProject, myRoot, GitCommand.DIFF);
    h.setSilent(true);
    // note that moves are not detected here
    h.addParameters("--name-status", "--diff-filter=ADMRUX", "--no-renames", revisions);
    for (StringScanner s = new StringScanner(Git.getInstance().runCommand(h).getOutputOrThrow()); s.hasMoreData(); ) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      char status = s.peek();
      s.boundedToken('\t');
      final String relative = s.line();
      // eliminate conflicts
      if (myUnmergedPaths.contains(relative)) {
        continue;
      }
      String path = root + "/" + GitUtil.unescapePath(relative);
      Collection<? super String> collection = switch (status) {
        case 'M' -> updated;
        case 'A' -> created;
        case 'D' -> removed;
        default -> throw new IllegalStateException("Unexpected status: " + status);
      };
      collection.add(path);
    }
  }

  /**
   * Add all paths to the group
   */
  private static void addAll(@NotNull UpdatedFiles updates, @NotNull String groupId, @NotNull Set<String> paths) {
    FileGroup fileGroup = updates.getGroupById(groupId);
    for (String path : paths) {
      fileGroup.add(path, GitVcs.getKey(), null);
    }
  }
}
