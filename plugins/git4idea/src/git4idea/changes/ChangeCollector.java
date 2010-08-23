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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
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

import java.util.*;

/**
 * A collector for changes in the Git. It is introduced because changes are not
 * cannot be got as a sum of stateless operations.
 */
class ChangeCollector {
  /**
   * The dirty scope used in the collector
   */
  private final VcsDirtyScope myDirtyScope;
  /**
   * a vcs root for changes
   */
  private final VirtualFile myVcsRoot;
  /**
   * a project for change collection
   */
  private final Project myProject;
  /**
   * Unversioned files
   */
  private final List<VirtualFile> myUnversioned = new ArrayList<VirtualFile>();
  /**
   * Names that are listed as unmerged
   */
  private final Set<String> myUnmergedNames = new HashSet<String>();
  /**
   * Names that are listed as unmerged
   */
  private final List<Change> myChanges = new ArrayList<Change>();
  /**
   * This flag indicates that collecting changes has been failed.
   */
  private boolean myIsFailed = true;
  /**
   * This flag indicates that collecting changes has been started
   */
  private boolean myIsCollected = false;

  /**
   * A constructor
   *
   * @param project    a project
   * @param dirtyScope the dirty scope to check
   * @param vcsRoot    a vcs root
   */
  public ChangeCollector(final Project project, VcsDirtyScope dirtyScope, final VirtualFile vcsRoot) {
    myDirtyScope = dirtyScope;
    myVcsRoot = vcsRoot;
    myProject = project;
  }

  /**
   * Get unversioned files
   *
   * @return an unversioned files
   * @throws VcsException if there is a problem with executing Git
   */
  public Collection<VirtualFile> unversioned() throws VcsException {
    ensureCollected();
    return myUnversioned;
  }

  /**
   * Get changes
   *
   * @return an unversioned files
   * @throws VcsException if there is a problem with executing Git
   */
  public Collection<Change> changes() throws VcsException {
    ensureCollected();
    return myChanges;
  }


  /**
   * Ensure that changes has been collected.
   *
   * @throws VcsException an exception
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
        ChangeListManager cm = ChangeListManager.getInstance(myProject);
        for (Change c : cm.getChangesIn(myVcsRoot)) {
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
      if (p.isUnder(toAdd, true)) {
        i.remove();
      }
      if (toAdd.isUnder(p, false)) {
        return;
      }
    }
    paths.add(toAdd);
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
