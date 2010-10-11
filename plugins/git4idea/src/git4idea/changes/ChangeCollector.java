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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;
import gnu.trove.THashSet;

import java.io.File;
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
    // ! order of collecting is important, because changes may intersect
    collectIndexChanges(dirtyPaths);
    collectWorkingTreeChanges(dirtyPaths);
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
      GitChangeUtils.parseChanges(myProject, myVcsRoot, null, myHeadRevision, output, myChanges, null);
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
   * Doesn't collect changes which already have been collected by collectIndexChanges().
   * For example, if a file was added and modified, it is collected from index (where it has the status of CREATED), but not from
   * the working tree (where it is MODIFIED).
   */
  private void collectWorkingTreeChanges(Collection<FilePath> dirtyPaths) throws VcsException {
    if (dirtyPaths == null || dirtyPaths.isEmpty()) {
      return;
    }
    // prepare handler
    final String[] parameters = {"-v", "--unmerged", "--others", "--deleted", "--modified", "--exclude-standard", "--full-name"};
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
   * Parses the output of the 'ls-files' command and fills myUnversioned and myChanges sets.
   * Doesn't record files which are already in myChanges.
   * Doesn't take care of unmerged files, because they are handled by collectIndexChanges.
   */
  private void parseLsFiles(String list) throws VcsException {
    final Set<String> removed = new HashSet<String>();
    final Set<String> changed = new HashSet<String>();
    final Set<String> unmerged = new HashSet<String>();

    // removed and unmerged files are also marked as changed in the 'ls-files' output
    // (so they are represented twice or even more times there).
    // we are going to collect them all and then exclude merged and removed files from the list of changed files.
    // although we won't collect unmerged files as changed, we need to collect them here to exclude them from the list of changed files.
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
          LOG.info("Unexpected type of the file status returned by git ls-file: [" + status + "]. Line: " + sc.line());
        }
      }
    }

    // removed and unmerged files are also marked as changed (in ls-files output), so remove them from that list
    changed.removeAll(removed);
    changed.removeAll(unmerged);

    // -- Precedence comparison between index and working tree changes --
    // Deletion on disk take the most precedence: if the file is deleted, we show it as deleted even if it was somehow modified in the index
    // Any other modifications in the working tree (actually it means editing the file since unversioned files are handled separately)
    // are less important than changes in the index:
    // if a file was modified in the index and in the working tree, we don't care: it's modified.
    // if a file was added and modified, then it's important that it's added.
    // if a file was renamed (in index) and then modified, its status is moved - it's more important.

    // 1. we collect changed paths to use further when deciding whether to add a modified file to myChanges.
    // 2. we also remove from myChanges any changes that happened with file which was then removed on disk.
    // 3. and we also take care of situation when a file was added to git and then removed from disk -
    //    although the file is new in index and deleted in the working tree, we don't show it as if there were no index at all.
    //    TODO: this solution is far from the best: the file remains in the index, but we don't show it.

    final Set<String> changedPaths = new HashSet<String>(myChanges.size());
    for (Iterator<Change> it = myChanges.iterator(); it.hasNext(); ) {
      boolean alreadyRemoved = false;
      final Change c = it.next();
      final ContentRevision before = c.getBeforeRevision();
      final ContentRevision after = c.getAfterRevision();
      if (after != null) {
        String path = after.getFile().getPath();
        if (path != null) {
          path = GitUtil.relativePath(VfsUtil.virtualToIoFile(myVcsRoot), new File(path));
          if (removed.contains(path)) {
            it.remove();
            alreadyRemoved = true;
            // additional condition for newly created files: if a file was added to git and then removed from disk, don't show it
            if (before == null) {
              removed.remove(path);
            }
          } else {
            changedPaths.add(path);
          }
        }
      }
      // if 'after' is null, the file was deleted by git rm command, so its status already is deleted and the file won't be shown in the
      // output of ls-files command

      if (before != null) {
        String path = before.getFile().getPath();
        if (path != null) {
          path = GitUtil.relativePath(VfsUtil.virtualToIoFile(myVcsRoot), new File(path));
          if (removed.contains(path)) {
            if (!alreadyRemoved) { it.remove(); } // don't remove twice 
          } else {
            changedPaths.add(path);
          }
        }
      }
    }

    // these files are removed from disk but not from git.
    for (String removedPath : removed) {
      ContentRevision before = GitContentRevision.createRevision(myVcsRoot, removedPath, myHeadRevision, myProject, true, true);
      myChanges.add(new Change(before, null, FileStatus.DELETED));
    }

    // these files are changed in working tree. Index change has precedence over working tree modification:
    // so if the file was already handled by diff-index, we don't add it again from working tree status.
    for (String changedPath : changed) {
      if (changedPaths.contains(changedPath)) { continue; }
      ContentRevision before = GitContentRevision.createRevision(myVcsRoot, changedPath, myHeadRevision, myProject, false, true);
      ContentRevision after = GitContentRevision.createRevision(myVcsRoot, changedPath, null, myProject, false, false);
      myChanges.add(new Change(before, after, FileStatus.MODIFIED));
    }
  }
}
