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
package git4idea.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;
import gnu.trove.THashSet;

import java.util.*;

/**
 * A collector for changes in Git - both in working tree and index.
 * Changes are collected from the native commands output.
 * Except for 'git status --porcelain' which was introduced in Git 1.7.0, there is no other command with reliable output format
 * which could give the whole status of working tree and index comparing to repository HEAD.
 * So we use two commands:
 *   git diff-index --cached --name-status -M HEAD   to get the difference between the index and HEAD
 *   git ls-files -oudmv --exclude-standard          to get the difference between the working tree and the index
 * Speed comparison shows that the two commands is the fastest combination to get the total information about changes.
 */
class ChangeCollector {
  private final VcsDirtyScope myDirtyScope;
  private final VirtualFile myVcsRoot;
  private final Project myProject;
  private final Set<VirtualFile> myUnversioned = new HashSet<VirtualFile>(); // unversioned files
  private final Set<Change> myChanges = new HashSet<Change>();         // all changes except unversioned (unmerged go here as well)
  private final Set<String> myUnmergedNames = new HashSet<String>();      // names of unmerged files
  private boolean myIsFailed = true; // this flag indicates that collecting changes has failed.
  private boolean myIsCollected = false; // this flag indicates that collecting changes has started
  private GitRevisionNumber myHeadRevision; // current HEAD
  private static final Logger LOG = Logger.getInstance(ChangeCollector.class.getName());

  public ChangeCollector(final Project project, final VcsDirtyScope dirtyScope, final VirtualFile vcsRoot) {
    myDirtyScope = dirtyScope;
    myVcsRoot = vcsRoot;
    myProject = project;
  }

  /**
   * Collect changes if they weren't collected yet and return unversioned files.
   * @return unversioned files
   */
  public Collection<VirtualFile> unversioned() throws VcsException {
    ensureCollected();
    return myUnversioned;
  }

  /**
   * Collect changes if they weren't collected yet and return all changes but unversioned files.
   * @return all changes except unversioned ones.
   */
  public Collection<Change> changes() throws VcsException {
    ensureCollected();
    return myChanges;
  }

  /**
   * Collect changes if needed.
   */
  private void ensureCollected() throws VcsException {
    if (myIsCollected) {
      if (myIsFailed) {
        throw new IllegalStateException("The method should not be called after after exception has been thrown.");
      }
      else {
        return;
      }
    }
    myIsCollected = true;
    updateIndex();
    myHeadRevision = GitChangeUtils.loadRevision(myProject, myVcsRoot, "HEAD");
    final Collection<FilePath> dirtyPaths = getDirtyPaths();
    collectWorkingTreeChanges(dirtyPaths);
    collectIndexChanges(dirtyPaths);
    myIsFailed = false;
  }

  private void updateIndex() throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.UPDATE_INDEX);
    handler.addParameters("--refresh", "--ignore-missing");
    handler.setSilent(true);
    handler.setNoSSH(true);
    handler.setStdoutSuppressed(true);
    handler.ignoreErrorCode(1);
    handler.run();
  }

  /**
   * From the dirty scope extracts all dirth paths. Collapses common ancestors.
   * @return dirty file paths.
   */
  private Collection<FilePath> getDirtyPaths() {
    final Set<FilePath> paths = new THashSet<FilePath>();
    final FilePath rootPath = VcsUtil.getFilePath(myVcsRoot.getPath(), true);

    boolean allRepositoryDirty = false;
    // In VcsDirtyScopeImpl.getRecursivelyDirtyDirectories() common dirs are collapsed.
    // But we need to exclude directories which are not inside our vcs root.
    for (FilePath p : myDirtyScope.getRecursivelyDirtyDirectories()) {
      if (addToPaths(rootPath, paths, p)) {
        allRepositoryDirty = true;
        break;
      }
    }
    if (allRepositoryDirty) { return Collections.singleton(rootPath); }

    for (FilePath p : myDirtyScope.getDirtyFilesNoExpand()) {
      if (addToPaths(rootPath, paths, p)) {
        allRepositoryDirty = true;
        break;
      }
    }
    if (allRepositoryDirty) { return Collections.singleton(rootPath); }
    return paths;
  }

  /**
   * <p>Adds the given path to the collection of paths, but only in the way of collapsing common prefixes.
   * That is: if any of the directories of the collection contains toAdd, nothing changes;
   * if toAdd contains any of the directories of the collection, then toAdd is preferable than the latter.</p>
   * <p>Also the situation when the whole repository is contained in toAdd is considered, and true is returned in that case.</p>
   *
   * @param root  repository root.
   * @param paths existing paths.
   * @param toAdd the path to add.
   * @return true if all repository is dirty, false if not.
   */
  private static boolean addToPaths(FilePath root, Collection<FilePath> paths, FilePath toAdd) {
    final VirtualFile gitRoot = GitUtil.getGitRootOrNull(toAdd);
    // the check is needed for multi-repository configurations (otherwise will try to get status of directories outside the repository).
    if (gitRoot == null || !gitRoot.getPath().equals(root.getPath())) {
      return false;
    }

    if (root.isUnder(toAdd, true)) {
      // the dirty directory which is being added contains the root => the whole repository is dirty
      return true;
    }
    for (Iterator<FilePath> i = paths.iterator(); i.hasNext();) {
      FilePath p = i.next();
      if (p.isUnder(toAdd, true)) {
        i.remove();
      }
      if (toAdd.isUnder(p, false)) {
        return false;
      }
    }
    paths.add(toAdd);
    return false;
  }

  /**
   * Collects changes between HEAD and index via 'git diff-index --cached'.
   */
  private void collectIndexChanges(Collection<FilePath> dirtyPaths) throws VcsException {
    if (dirtyPaths == null || dirtyPaths.isEmpty()) {
      return;
    }
    final String[] diffParameters = { "--cached", "--name-status", "-M", "HEAD" };
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.DIFF_INDEX);
    handler.addParameters(diffParameters);
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.setStdoutSuppressed(true);
    handler.endOptions();
    handler.addRelativePaths(dirtyPaths);
    if (handler.isLargeCommandLine()) {
      // if there are too much files, just get all changes for the project
      handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.DIFF_INDEX);
      handler.addParameters(diffParameters);
      handler.setNoSSH(true);
      handler.setSilent(true);
      handler.setStdoutSuppressed(true);
      handler.endOptions();
    }
    try {
      String output = handler.run();
      // unmerged files may appear both in the index and the working tree => ignoring them here as we've already collected them from the working tree.
      GitChangeUtils.parseChanges(myProject, myVcsRoot, null, myHeadRevision, output, myChanges, myUnmergedNames);
    }
    catch (VcsException ex) {
      if (!GitChangeUtils.isHeadMissing(ex)) {
        throw ex;
      }
      // During init diff does not works because HEAD
      // will appear only after the first commit.
      // In that case added files are cached in index.
      handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
      handler.addParameters("--cached");
      handler.setNoSSH(true);
      handler.setSilent(true);
      handler.setStdoutSuppressed(true);
      String output = handler.run();
      if (output.length() > 0) {
        StringTokenizer tokenizer = new StringTokenizer(output, "\n\r");
        while (tokenizer.hasMoreTokens()) {
          final String s = tokenizer.nextToken();
          Change ch = new Change(null, GitContentRevision.createRevision(myVcsRoot, s, null, myProject, false, false), FileStatus.ADDED);
          myChanges.add(ch);
        }
      }
    }
  }

  /**
   * Collects changes between the index and the working tree, including unversioned files.
   * 'git ls-files -douvm --exclude-standard' is used for that.
   */
  private void collectWorkingTreeChanges(Collection<FilePath> dirtyPaths) throws VcsException {
    if (dirtyPaths == null || dirtyPaths.isEmpty()) {
      return;
    }
    // prepare handler
    final String[] parameters = {"-v", "--unmerged", "--others", "--deleted", "--modified", "--exclude-standard"};
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
    handler.addParameters(parameters);
    handler.setSilent(true);
    handler.setNoSSH(true);
    handler.setStdoutSuppressed(true);
    handler.endOptions();
    handler.addRelativePaths(dirtyPaths);
    if (handler.isLargeCommandLine()) {
      handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
      handler.addParameters(parameters);
      handler.setSilent(true);
      handler.setNoSSH(true);
      handler.setStdoutSuppressed(true);
      handler.endOptions();
    }
    // run handler and collect changes
    parseLsFiles(handler.run());
  }

  /**
   * Parses the output of the 'ls-files' command and fills myUnversioned, myUnmergedNames and myChanges sets.
   */
  private void parseLsFiles(String list) throws VcsException {
    final Set<String> removed = new HashSet<String>();
    final Set<String> changed = new HashSet<String>();
    final Set<String> unmerged = new HashSet<String>();

    // removed and unmerged files are also marked as changed in the 'ls-files' output
    // (so they are represented twice or even more times there).
    // we are going to collect them all and then exclude merged and removed files from the list of changed files.
    for (StringScanner sc = new StringScanner(list); sc.hasMoreData(); ) {
      if (sc.isEol()) {
        sc.nextLine();
        continue;
      }
      final char status = sc.peek();
      sc.skipChars(2);

      // format for ? (unversioned) and other statuses different, so we have 2 if-branches here
      if ('?' == status) {
        // ? <filename>
        VirtualFile file = myVcsRoot.findFileByRelativePath(GitUtil.unescapePath(sc.line()));
        myUnversioned.add(file);
      } else {
        // <status> <mode> <object> <stage>\t<filename>
        // TODO: don't rely on this tab character
        sc.boundedToken('\t');
        final String filePath = GitUtil.unescapePath(sc.line());
        if ('R' == status) { // removed on disk but not from git
          removed.add(filePath);
        } else if ('C' == status) {    // modified, unmerged or removed
          changed.add(filePath);
        } else if ('M' == status) {
          unmerged.add(filePath);
        } else {
          LOG.info("Unsupported type of the file status returned by git ls-file: [" + status + "]. Line: " + sc.line());
        }
      }
    }

    // remove duplicates from changes, which are already handled by removed and unmerged.
    // Then create Change objects and fill myChanges array.

    for (String removedPath : removed) {
      changed.remove(removedPath);
      ContentRevision before = GitContentRevision.createRevision(myVcsRoot, removedPath, myHeadRevision, myProject, true, true);
      myChanges.add(new Change(before, null, FileStatus.DELETED));
    }

    for (String unmergedPath : unmerged) {
      changed.remove(unmergedPath);
      myUnmergedNames.add(unmergedPath);
      ContentRevision before = GitContentRevision.createRevision(myVcsRoot, unmergedPath, new GitRevisionNumber("orig_head"), myProject, false, true);
      ContentRevision after = GitContentRevision.createRevision(myVcsRoot, unmergedPath, null, myProject, false, false);
      myChanges.add(new Change(before, after, FileStatus.MERGED_WITH_CONFLICTS));
    }

    for (String changedPath : changed) {
      ContentRevision before = GitContentRevision.createRevision(myVcsRoot, changedPath, null, myProject, false, true);
      ContentRevision after = GitContentRevision.createRevision(myVcsRoot, changedPath, myHeadRevision, myProject, false, false);
      myChanges.add(new Change(before, after, FileStatus.MODIFIED));
    }
  }
}
