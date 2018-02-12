/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.merge;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
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
import git4idea.repo.GitRepository;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * Collect change for merge or pull operations
 */
public class MergeChangeCollector {
  private final HashSet<String> myUnmergedPaths = new HashSet<>();
  private final Project myProject;
  private final VirtualFile myRoot;
  private final GitRevisionNumber myStart; // Revision number before update (used for diff)
  @NotNull private final GitRepository myRepository;

  public MergeChangeCollector(final Project project, final VirtualFile root, final GitRevisionNumber start) {
    myStart = start;
    myProject = project;
    myRoot = root;
    myRepository = assertNotNull(GitUtil.getRepositoryManager(project).getRepositoryForRoot(root));
  }

  /**
   * Collects changed files during or after merge operation to the supplied {@code updates} container.
   */
  public void collect(final UpdatedFiles updates, List<VcsException> exceptions) {
    try {
      // collect unmerged
      Set<String> paths = getUnmergedPaths();
      addAll(updates, FileGroup.MERGED_WITH_CONFLICT_ID, paths);

      // collect other changes (ignoring unmerged)
      TreeSet<String> updated = new TreeSet<>();
      TreeSet<String> created = new TreeSet<>();
      TreeSet<String> removed = new TreeSet<>();

      String revisionsForDiff = getRevisionsForDiff();
      if (revisionsForDiff ==  null) {
        return;
      }
      getChangedFilesExceptUnmerged(updated, created, removed, revisionsForDiff);
      addAll(updates, FileGroup.UPDATED_ID, updated);
      addAll(updates, FileGroup.CREATED_ID, created);
      addAll(updates, FileGroup.REMOVED_FROM_REPOSITORY_ID, removed);
    } catch (VcsException e) {
      exceptions.add(e);
    }
  }

  /**
   * Returns absolute paths to files which are currently unmerged, and also populates myUnmergedPaths with relative paths.
   */
  public @NotNull Set<String> getUnmergedPaths() throws VcsException {
    String root = myRoot.getPath();
    final GitLineHandler h = new GitLineHandler(myProject, myRoot, GitCommand.LS_FILES);
    h.setSilent(true);
    h.addParameters("--unmerged");
    final String result = Git.getInstance().runCommand(h).getOutputOrThrow();

    final Set<String> paths = new HashSet<>();
    for (StringScanner s = new StringScanner(result); s.hasMoreData();) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      s.boundedToken('\t');
      final String relative = s.line();
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
    String root = myRoot.getPath();
    GitRevisionNumber currentHead = GitRevisionNumber.resolve(myProject, myRoot, "HEAD");
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
          for (StringScanner s = new StringScanner(mergeHeads); s.hasMoreData();) {
            String head = s.line();
            if (head.length() == 0) {
              continue;
            }
            // note that "..." cause the diff to start from common parent between head and merge head
            return myStart.getRev() + "..." + head;
          }
        }
      } catch (IOException e) {
        //noinspection ThrowableInstanceNeverThrown
        throw new VcsException("Unable to read the file " + mergeHeadsFile + ": " + e.getMessage(), e);
      }
    } else {
      // Otherwise this is a merge that did created a commit. And because of this the incoming changes
      // are diffs between old head and new head. The commit could have been multihead commit,
      // and the expression below considers it as well.
      return myStart.getRev() + "..HEAD";
    }
    return null;
  }

  /**
   * Populates the supplied collections of modified, created and removed files returned by 'git diff #revisions' command,
   * where revisions is the range of revisions to check.
   */
  public void getChangedFilesExceptUnmerged(Collection<String> updated, Collection<String> created, Collection<String> removed, String revisions)
    throws VcsException {
    if (revisions == null) {
      return;
    }
    String root = myRoot.getPath();
    GitLineHandler h = new GitLineHandler(myProject, myRoot, GitCommand.DIFF);
    h.setSilent(true);
    // note that moves are not detected here
    h.addParameters("--name-status", "--diff-filter=ADMRUX", "--no-renames", revisions);
    for (StringScanner s = new StringScanner(Git.getInstance().runCommand(h).getOutputOrThrow()); s.hasMoreData();) {
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
      switch (status) {
        case 'M':
          updated.add(path);
          break;
        case 'A':
          created.add(path);
          break;
        case 'D':
          removed.add(path);
          break;
        default:
          throw new IllegalStateException("Unexpected status: " + status);
      }
    }
  }

  /**
   * Add all paths to the group
   */
  private static void addAll(final UpdatedFiles updates, String group_id, Set<String> paths) {
    FileGroup fileGroup = updates.getGroupById(group_id);
    final VcsKey vcsKey = GitVcs.getKey();
    for (String path : paths) {
      fileGroup.add(path, vcsKey, null);
    }
  }
}
