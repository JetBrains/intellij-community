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
package git4idea.status;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * <p>
 *   Collects changes from the Git repository in the specified {@link com.intellij.openapi.vcs.changes.VcsDirtyScope}
 *   using the older technique that is replaced by {@link GitNewChangesCollector} for Git later than 1.7.0 inclusive.
 *   This class is used for Git older than 1.7.0 not inclusive, that don't have <code>'git status --porcelain'</code>.
 * </p>
 * <p>
 *   The method used by this class is less efficient and more error-prone than {@link GitNewChangesCollector} method.
 *   Thus this class is considered as a legacy code for Git 1.6.*. Read further for the implementation details and the ground for
 *   transferring to use {@code 'git status --porcelain'}.
 * </p>
 * <p>
 *   The following Git commands are called to get the changes, i.e. the state of the working tree combined with the state of index.
 *   <ul>
 *     <li>
 *       <b><code>'git update-index --refresh'</code></b> (called on the whole repository) - probably unnecessary (especially before 'git diff'),
 *       but is left not to break some older Gits occasionally. See the following links for some details:
 *       <a href="http://us.generation-nt.com/answer/bug-596126-git-status-does-not-refresh-index-fixed-since-1-7-1-1-please-consider-upgrading-1-7-1-2-squeeze-help-200234171.html">
 *       gitk doesn't refresh the index statinfo</a>;
 *       <a href="http://thread.gmane.org/gmane.comp.version-control.git/144176/focus">
 *       "Most git porcelain silently refreshes stat-dirty index entries"</a>;
 *       <a href="https://git.wiki.kernel.org/index.php/GitFaq#Can_I_import_from_tar_files_.28archives.29.3">update-index to import from tar files</a>.
 *     </li>
 *     <li>
 *       <b><code>'git ls-files --unmerged'</code></b> (called on the whole repository) - to get the list of unmerged files.
 *       It is not clear why it should be called on the whole repository. The decision to call it on the whole repository was made in
 *       <code>45687fe "<a href="http://youtrack.jetbrains.net/issue/IDEA-50573">IDEADEV-40577</a>: The ignored unmerged files are now reported"</code>,
 *       but neither the rollback & test, nor the analysis didn't recover the need for that. It is left however, since it is a legacy code.
 *     </li>
 *     <li>
 *       <b><code>'git ls-files --others --exclude-standard'</code></b> (called on the dirty scope) - to get the list of unversioned files.
 *       Note that this command is the only way to get the list of unversioned files, besides <code>'git status'</code>.
 *     </li>
 *     <li>
 *       <b><code>'git diff --name-status -M HEAD -- </code></b> (called on the dirty scope) - to get all other changes (except unversioned and
 *       unmerged).
 *       Note that there is also no way to get all tracked changes by a single command (except <code>'git status'</code>), since
 *       <code>'git diff'</code> returns either only not-staged changes, either (<code>'git diff HEAD'</code>) treats unmerged as modified.
 *     </li>
 *   </ul>
 * </p>
 * <p>
 *   <b>Performance measurement</b>
 *   was performed on a large repository (like IntelliJ IDEA), on a single machine, after several "warm-ups" when <code>'git status'</code> duration
 *   stabilizes.
 *   For the whole repository:
 *   <code>'git status'</code> takes ~ 1300 ms while these 4 commands take ~ 1870 ms
 *   ('update-index' ~ 270 ms, 'ls-files --unmerged' ~ 46 ms, 'ls files --others' ~ 820 ms, 'diff' ~ 650 ms)
 *   ; for a single file:
 *   <code>'git status'</code> takes ~ 375 ms, these 4 commands take ~ 750 ms.
 * </p>
 * <p>
 * The class is immutable: collect changes and get the instance from where they can be retrieved by {@link #collect}.
 * </p>
 *
 * @author Constantine Plotnikov
 * @author Kirill Likhodedov
 */
class GitOldChangesCollector extends GitChangesCollector {

  private final List<VirtualFile> myUnversioned = new ArrayList<>(); // Unversioned files
  private final Set<String> myUnmergedNames = new HashSet<>(); // Names of unmerged files
  private final List<Change> myChanges = new ArrayList<>(); // all changes

  /**
   * Collects the changes from git command line and returns the instance of GitNewChangesCollector from which these changes can be retrieved.
   * This may be lengthy.
   */
  @NotNull
  static GitOldChangesCollector collect(@NotNull Project project, @NotNull ChangeListManager changeListManager,
                                        @NotNull ProjectLevelVcsManager vcsManager, @NotNull AbstractVcs vcs,
                                        @NotNull VcsDirtyScope dirtyScope, @NotNull VirtualFile vcsRoot) throws VcsException {
    return new GitOldChangesCollector(project, changeListManager, vcsManager, vcs, dirtyScope, vcsRoot);
  }

  @NotNull
  @Override
  Collection<VirtualFile> getUnversionedFiles() {
    return myUnversioned;
  }

  @NotNull
  @Override
  Collection<Change> getChanges(){
    return myChanges;
  }

  private GitOldChangesCollector(@NotNull Project project, @NotNull ChangeListManager changeListManager,
                                 @NotNull ProjectLevelVcsManager vcsManager, @NotNull AbstractVcs vcs, @NotNull VcsDirtyScope dirtyScope,
                                 @NotNull VirtualFile vcsRoot) throws VcsException {
    super(project, changeListManager, vcsManager, vcs, dirtyScope, vcsRoot);
    updateIndex();
    collectUnmergedAndUnversioned();
    collectDiffChanges();
  }

  private void updateIndex() throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.UPDATE_INDEX);
    handler.addParameters("--refresh", "--ignore-missing");
    handler.setSilent(true);
    handler.setStdoutSuppressed(true);
    handler.ignoreErrorCode(1);
    handler.run();
  }

  /**
   * Collect diff with head
   *
   * @throws VcsException if there is a problem with running git
   */
  private void collectDiffChanges() throws VcsException {
    Collection<FilePath> dirtyPaths = dirtyPaths(true);
    if (dirtyPaths.isEmpty()) {
      return;
    }
    try {
      String output = GitChangeUtils.getDiffOutput(myProject, myVcsRoot, "HEAD", dirtyPaths);
      GitChangeUtils.parseChanges(myProject, myVcsRoot, null, GitChangeUtils.resolveReference(myProject, myVcsRoot, "HEAD"), output, myChanges,
                                  myUnmergedNames);
    }
    catch (VcsException ex) {
      if (!GitChangeUtils.isHeadMissing(ex)) {
        throw ex;
      }
      GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
      handler.addParameters("--cached");
      handler.setSilent(true);
      handler.setStdoutSuppressed(true);
      // During init diff does not works because HEAD
      // will appear only after the first commit.
      // In that case added files are cached in index.
      String output = handler.run();
      if (output.length() > 0) {
        StringTokenizer tokenizer = new StringTokenizer(output, "\n\r");
        while (tokenizer.hasMoreTokens()) {
          final String s = tokenizer.nextToken();
          Change ch = new Change(null, GitContentRevision.createRevision(myVcsRoot, s, null, myProject, false, false, true), FileStatus.ADDED);
          myChanges.add(ch);
        }
      }
    }
  }

  /**
   * Collect unversioned and unmerged files
   *
   * @throws VcsException if there is a problem with running git
   */
  private void collectUnmergedAndUnversioned() throws VcsException {
    Collection<FilePath> dirtyPaths = dirtyPaths(false);
    if (dirtyPaths.isEmpty()) {
      return;
    }
    // prepare handler
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
    handler.addParameters("-v", "--unmerged");
    handler.setSilent(true);
    handler.setStdoutSuppressed(true);
    // run handler and collect changes
    parseFiles(handler.run());
    // prepare handler
    handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
    handler.addParameters("-v", "--others", "--exclude-standard");
    handler.setSilent(true);
    handler.setStdoutSuppressed(true);
    handler.endOptions();
    handler.addRelativePaths(dirtyPaths);
    if(handler.isLargeCommandLine()) {
      handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
      handler.addParameters("-v", "--others", "--exclude-standard");
      handler.setSilent(true);
      handler.setStdoutSuppressed(true);
      handler.endOptions();
    }
    // run handler and collect changes
    parseFiles(handler.run());
  }

  private void parseFiles(String list) throws VcsException {
    for (StringScanner sc = new StringScanner(list); sc.hasMoreData();) {
      if (sc.isEol()) {
        sc.nextLine();
        continue;
      }
      char status = sc.peek();
      sc.skipChars(2);
      if ('?' == status) {
        VirtualFile file = myVcsRoot.findFileByRelativePath(GitUtil.unescapePath(sc.line()));
        if (Comparing.equal(GitUtil.gitRootOrNull(file), myVcsRoot)) {
          myUnversioned.add(file);
        }
      }
      else { //noinspection HardCodedStringLiteral
        if ('M' == status) {
          sc.boundedToken('\t');
          String file = GitUtil.unescapePath(sc.line());
          VirtualFile vFile = myVcsRoot.findFileByRelativePath(file);
          if (!Comparing.equal(GitUtil.gitRootOrNull(vFile), myVcsRoot)) {
            continue;
          }
          if (!myUnmergedNames.add(file)) {
            continue;
          }
          // assume modify-modify conflict
          ContentRevision before = GitContentRevision.createRevision(myVcsRoot, file, new GitRevisionNumber("orig_head"), myProject, false, true,
                                                                     true);
          ContentRevision after = GitContentRevision.createRevision(myVcsRoot, file, null, myProject, false, false, true);
          myChanges.add(new Change(before, after, FileStatus.MERGED_WITH_CONFLICTS));
        }
        else {
          throw new VcsException("Unsupported type of the merge conflict detected: " + status);
        }
      }
    }
  }
}
