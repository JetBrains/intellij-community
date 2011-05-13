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
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.commands.StringScanner;

import java.io.File;
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
    final List<String> allPaths = new ArrayList<String>();

    for (FilePath p : myDirtyScope.getRecursivelyDirtyDirectories()) {
      addToPaths(p, allPaths);
    }
    for (FilePath p : myDirtyScope.getDirtyFilesNoExpand()) {
      addToPaths(p, allPaths);
    }

    if (includeChanges) {
      try {
        for (Change c : myChangeListManager.getChangesIn(myVcsRoot)) {
          switch (c.getType()) {
            case NEW:
            case DELETED:
            case MOVED:
              if (c.getAfterRevision() != null) {
                addToPaths(c.getAfterRevision().getFile(), allPaths);
              }
              if (c.getBeforeRevision() != null) {
                addToPaths(c.getBeforeRevision().getFile(), allPaths);
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

    removeCommonParents(allPaths);

    final List<FilePath> paths = new ArrayList<FilePath>(allPaths.size());
    for (String p : allPaths) {
      final File file = new File(p);
      paths.add(new FilePathImpl(file, file.isDirectory()));
    }
    return paths;
  }

  private void addToPaths(FilePath pathToAdd, List<String> paths) {
    if (myVcsRoot.equals(GitUtil.getGitRootOrNull(pathToAdd))) {
      paths.add(pathToAdd.getPath());
    }
  }

  private static void removeCommonParents(List<String> allPaths) {
    Collections.sort(allPaths);

    String prevPath = null;
    Iterator<String> it = allPaths.iterator();
    while (it.hasNext()) {
      String path = it.next();
      if (prevPath != null && FileUtil.startsWith(path, prevPath)) {      // the file is under previous file, so enough to check the parent
        it.remove();
      }
      else {
        prevPath = path;
      }
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
