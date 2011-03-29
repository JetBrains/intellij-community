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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
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

import java.io.IOException;
import java.util.*;

/**
 * A collector for changes in the Git. It is introduced because changes are not
 * cannot be got as a sum of stateless operations.
 */
class ChangeCollector {
  private final Project myProject;
  private final ChangeListManager myChangeListManager;
  private final VcsDirtyScope myDirtyScope;
  private final VirtualFile myVcsRoot;

  private final List<VirtualFile> myUnversioned = new ArrayList<VirtualFile>(); // Unversioned files
  private final Set<String> myUnmergedNames = new HashSet<String>(); // Names of unmerged files
  private final List<Change> myChanges = new ArrayList<Change>(); // all changes
  private boolean myIsCollected = false; // indicates that collecting changes has been started
  private boolean myIsFailed = true; // indicates that collecting changes has been failed.

  public ChangeCollector(final Project project, ChangeListManager changeListManager, VcsDirtyScope dirtyScope, final VirtualFile vcsRoot) {
    myChangeListManager = changeListManager;
    myDirtyScope = dirtyScope;
    myVcsRoot = vcsRoot;
    myProject = project;
  }

  /**
   * Get unversioned files
   */
  public Collection<VirtualFile> unversioned() throws VcsException {
    ensureCollected();
    return myUnversioned;
  }

  /**
   * Get changes
   */
  public Collection<Change> changes() throws VcsException {
    ensureCollected();
    return myChanges;
  }


  /**
   * Ensure that changes has been collected.
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
    collectUnmergedAndUnversioned();
    collectDiffChanges();
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
   * Collect dirty file paths
   *
   * @param includeChanges if true, previous changes are included in collection
   * @return the set of dirty paths to check, the paths are automatically collapsed if the summary length more than limit
   */
  private Collection<FilePath> dirtyPaths(boolean includeChanges) {
    // TODO collapse paths with common prefix
    ArrayList<FilePath> paths = new ArrayList<FilePath>();
    FilePath rootPath = VcsUtil.getFilePath(myVcsRoot.getPath(), true);
    for (FilePath p : myDirtyScope.getRecursivelyDirtyDirectories()) {
      addToPaths(rootPath, paths, p);
    }
    ArrayList<FilePath> candidatePaths = new ArrayList<FilePath>();
    candidatePaths.addAll(myDirtyScope.getDirtyFilesNoExpand());
    if (includeChanges) {
      try {
        for (Change c : myChangeListManager.getChangesIn(myVcsRoot)) {
          switch (c.getType()) {
            case NEW:
            case DELETED:
            case MOVED:
              if (c.getAfterRevision() != null) {
                addToPaths(rootPath, paths, c.getAfterRevision().getFile());
              }
              if (c.getBeforeRevision() != null) {
                addToPaths(rootPath, paths, c.getBeforeRevision().getFile());
              }
            case MODIFICATION:
            default:
              // do nothing
          }
        }
      }
      catch (Exception t) {
        // ignore exceptions
      }
    }
    for (FilePath p : candidatePaths) {
      addToPaths(rootPath, paths, p);
    }
    return paths;
  }

  /**
   * Add path to the collection of the paths to check for this vcs root
   *
   * @param root  the root path
   * @param paths the existing paths
   * @param toAdd the path to add
   */
  void addToPaths(FilePath root, Collection<FilePath> paths, FilePath toAdd) {
    if (GitUtil.getGitRootOrNull(toAdd) != myVcsRoot) {
      return;
    }
    if (root.isUnder(toAdd, true)) {
      toAdd = root;
    }
    for (Iterator<FilePath> i = paths.iterator(); i.hasNext();) {
      FilePath p = i.next();
      if (isAncestor(toAdd, p, true)) { // toAdd is an ancestor of p => adding toAdd instead of p.
        i.remove();
      }
      if (isAncestor(p, toAdd, false)) { // p is an ancestor of toAdd => no need to add toAdd.
        return;
      }
    }
    paths.add(toAdd);
  }

  /**
   * Returns true if childCandidate file is located under parentCandidate.
   * This is an alternative to {@link com.intellij.openapi.vcs.FilePathImpl#isUnder(com.intellij.openapi.vcs.FilePath, boolean)}:
   * it doesn't check VirtualFile associated with this FilePath.
   * When we move a file we get a VcsDirtyScope with old and new FilePaths, but unfortunately the virtual file in the FilePath is
   * refreshed ({@link com.intellij.openapi.vcs.changes.VirtualFileHolder#cleanAndAdjustScope(com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope)}
   * and thus points to the new position which makes FilePathImpl#isUnder useless.
   *
   * @param parentCandidate FilePath which we check to be the parent of childCandidate.
   * @param childCandidate  FilePath which we check to be a child of parentCandidate.
   * @param strict          if false, the method also returns true if files are equal
   * @return true if childCandidate is a child of parentCandidate.
   */
  private static boolean isAncestor(FilePath parentCandidate, FilePath childCandidate, boolean strict) {
    try {
      if (childCandidate.getPath().length() < parentCandidate.getPath().length()) return false;
      if (childCandidate.getVirtualFile() != null && parentCandidate.getVirtualFile() != null) {
        return VfsUtil.isAncestor(parentCandidate.getVirtualFile(), childCandidate.getVirtualFile(), strict);
      }
      return FileUtil.isAncestor(parentCandidate.getIOFile(), childCandidate.getIOFile(), strict);
    }
    catch (IOException e) {
      return false;
    }
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
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.DIFF);
    handler.addParameters("--name-status", "--diff-filter=ADCMRUX", "-M", "HEAD");
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.setStdoutSuppressed(true);
    handler.endOptions();
    handler.addRelativePaths(dirtyPaths);
    if (handler.isLargeCommandLine()) {
      // if there are too much files, just get all changes for the project
      handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.DIFF);
      handler.addParameters("--name-status", "--diff-filter=ADCMRUX", "-M", "HEAD");
      handler.setNoSSH(true);
      handler.setSilent(true);
      handler.setStdoutSuppressed(true);
      handler.endOptions();
    }
    try {
      String output = handler.run();
      GitChangeUtils.parseChanges(myProject, myVcsRoot, null, GitChangeUtils.loadRevision(myProject, myVcsRoot, "HEAD"), output, myChanges,
                                  myUnmergedNames);
    }
    catch (VcsException ex) {
      if (!GitChangeUtils.isHeadMissing(ex)) {
        throw ex;
      }
      handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
      handler.addParameters("--cached");
      handler.setNoSSH(true);
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
          Change ch = new Change(null, GitContentRevision.createRevision(myVcsRoot, s, null, myProject, false, false), FileStatus.ADDED);
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
    handler.setNoSSH(true);
    handler.setStdoutSuppressed(true);
    // run handler and collect changes
    parseFiles(handler.run());
    // prepare handler
    handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
    handler.addParameters("-v", "--others", "--exclude-standard");
    handler.setSilent(true);
    handler.setNoSSH(true);
    handler.setStdoutSuppressed(true);
    handler.endOptions();
    handler.addRelativePaths(dirtyPaths);
    if(handler.isLargeCommandLine()) {
      handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.LS_FILES);
      handler.addParameters("-v", "--others", "--exclude-standard");
      handler.setSilent(true);
      handler.setNoSSH(true);
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
        if (GitUtil.gitRootOrNull(file) == myVcsRoot) {
          myUnversioned.add(file);
        }
      }
      else { //noinspection HardCodedStringLiteral
        if ('M' == status) {
          sc.boundedToken('\t');
          String file = GitUtil.unescapePath(sc.line());
          VirtualFile vFile = myVcsRoot.findFileByRelativePath(file);
          if (GitUtil.gitRootOrNull(vFile) != myVcsRoot) {
            continue;
          }
          if (!myUnmergedNames.add(file)) {
            continue;
          }
          // TODO handle conflict rename-modify
          // TODO handle conflict copy-modify
          // TODO handle conflict delete-modify
          // TODO handle conflict rename-delete
          // assume modify-modify conflict
          ContentRevision before = GitContentRevision.createRevision(myVcsRoot, file, new GitRevisionNumber("orig_head"), myProject, false, true);
          ContentRevision after = GitContentRevision.createRevision(myVcsRoot, file, null, myProject, false, false);
          myChanges.add(new Change(before, after, FileStatus.MERGED_WITH_CONFLICTS));
        }
        else {
          throw new VcsException("Unsupported type of the merge conflict detected: " + status);
        }
      }
    }
  }
}
