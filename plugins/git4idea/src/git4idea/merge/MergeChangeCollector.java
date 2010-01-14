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
package git4idea.merge;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

/**
 * Collect change for merge or pull operations
 */
public class MergeChangeCollector {
  /**
   * Unmerged paths
   */
  private final HashSet<String> myUnmergedPaths = new HashSet<String>();
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * The git root
   */
  private final VirtualFile myRoot;
  /**
   * Updates container
   */
  private final UpdatedFiles myUpdates;
  /**
   * Revision number before update (used for diff)
   */
  private final GitRevisionNumber myStart;

  /**
   * A constructor
   *
   * @param project the context project
   * @param root    the git root
   * @param start   the start revision
   * @param updates a container for updates
   */
  public MergeChangeCollector(final Project project, final VirtualFile root, final GitRevisionNumber start, final UpdatedFiles updates) {
    myStart = start;
    myProject = project;
    myRoot = root;
    myUpdates = updates;
  }

  /**
   * Collect changes
   *
   * @param exceptions a list of exceptions
   */
  public void collect(List<VcsException> exceptions) {
    final VcsKey vcsKey = GitVcs.getKey();
    try {
      // collect unmerged
      String root = myRoot.getPath();
      GitSimpleHandler h = new GitSimpleHandler(myProject, myRoot, GitCommand.LS_FILES);
      h.setNoSSH(true);
      h.setSilent(true);
      h.addParameters("--unmerged");
      for (StringScanner s = new StringScanner(h.run()); s.hasMoreData();) {
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
        myUpdates.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).add(path, vcsKey, null);
      }
      GitRevisionNumber currentHead = GitRevisionNumber.resolve(myProject, myRoot, "HEAD");
      // collect other changes (ignoring unmerged)
      TreeSet<String> updated = new TreeSet<String>();
      TreeSet<String> created = new TreeSet<String>();
      TreeSet<String> removed = new TreeSet<String>();
      if (currentHead.equals(myStart)) {
        // The head has not advanced. This means that this is a merge that did not commit.
        // This could be caused by --no-commit option or by failed two-head merge. The MERGE_HEAD
        // should be available. In case of --no-commit option, the MERGE_HEAD might contain
        // multiple heads separated by newline. The changes are collected separately for each head
        // and they are merged using TreeSet class (that also sorts the changes).
        File mergeHeadsFile = new File(root, ".git/MERGE_HEAD");
        try {
          if (mergeHeadsFile.exists()) {
            String mergeHeads = new String(FileUtil.loadFileText(mergeHeadsFile, GitUtil.UTF8_ENCODING));
            for (StringScanner s = new StringScanner(mergeHeads); s.hasMoreData();) {
              String head = s.line();
              if (head.length() == 0) {
                continue;
              }
              // note that "..." cause the diff to start from common parent between head and merge head
              processDiff(root, updated, created, removed, myStart.getRev() + "..." + head);
            }
          }
        }
        catch (IOException e) {
          //noinspection ThrowableInstanceNeverThrown
          exceptions.add(new VcsException("Unable to read the file " + mergeHeadsFile + ": " + e.getMessage(), e));
        }
      }
      else {
        // Otherwise this is a merge that did created a commit. And because of this the incoming changes
        // are diffs between old head and new head. The commit could have been multihead commit,
        // and the expression below considers it as well.
        processDiff(root, updated, created, removed, myStart.getRev() + "..HEAD");
      }
      addAll(FileGroup.UPDATED_ID, updated);
      addAll(FileGroup.CREATED_ID, created);
      addAll(FileGroup.REMOVED_FROM_REPOSITORY_ID, removed);
    }
    catch (VcsException e) {
      exceptions.add(e);
    }
  }

  /**
   * Process diff
   *
   * @param root      the vcs root
   * @param updated   the set of updated files
   * @param created   the set of created files
   * @param removed   the set of removed files
   * @param revisions the diff expressions
   * @throws VcsException if there is a problem with running git
   */
  private void processDiff(String root, TreeSet<String> updated, TreeSet<String> created, TreeSet<String> removed, String revisions)
    throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(myProject, myRoot, GitCommand.DIFF);
    h.setSilent(true);
    h.setNoSSH(true);
    // note that moves are not detected here
    h.addParameters("--name-status", "--diff-filter=ADMRUX", revisions);
    for (StringScanner s = new StringScanner(h.run()); s.hasMoreData();) {
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
   *
   * @param id    the group identifier
   * @param paths the set of paths
   */
  private void addAll(String id, TreeSet<String> paths) {
    FileGroup fileGroup = myUpdates.getGroupById(id);
    final VcsKey vcsKey = GitVcs.getKey();
    for (String path : paths) {
      fileGroup.add(path, vcsKey, null);
    }
  }
}
