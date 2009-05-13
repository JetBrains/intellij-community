/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;

import java.util.*;

/**
 * A collector for changes in the Git. It is introduced because changes are not
 * cannot be got as a sum of stateless operations.
 */
class ChangeCollector {
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
   * @param project a project
   * @param vcsRoot a vcs root
   */
  public ChangeCollector(final Project project, final VirtualFile vcsRoot) {
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
    collectUnmergedAndUnversioned();
    collectDiffChanges();
    myIsFailed = false;
  }

  /**
   * Collect diff with head
   *
   * @throws VcsException if there is a problem with running git
   */
  private void collectDiffChanges() throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitHandler.DIFF);
    handler.addParameters("--name-status", "--diff-filter=ADCMRUX", "-M", "HEAD");
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.setStdoutSuppressed(true);
    handler.endOptions();
    try {
      String output = handler.run();
      GitChangeUtils.parseChanges(myProject, myVcsRoot, null, GitChangeUtils.loadRevision(myProject, myVcsRoot, "HEAD"), output, myChanges,
                                  myUnmergedNames);
    }
    catch (VcsException ex) {
      if (!GitChangeUtils.isHeadMissing(ex)) {
        throw ex;
      }
      handler = new GitSimpleHandler(myProject, myVcsRoot, GitHandler.LS_FILES);
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
          Change ch = new Change(null, GitContentRevision.createRevision(myVcsRoot, s, null, myProject, false), FileStatus.ADDED);
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
    // prepare handler
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitHandler.LS_FILES);
    handler.addParameters("-v", "--others", "--unmerged", "--exclude-standard");
    handler.setSilent(true);
    handler.setNoSSH(true);
    handler.setStdoutSuppressed(true);
    // run handler and collect changes
    String list = handler.run();
    for (String line : list.split("\n")) {
      if (line.length() == 0) {
        continue;
      }
      String[] tokens = line.split("[\t ]+");
      String file = GitUtil.unescapePath(tokens[tokens.length - 1]);
      if ("?".equals(tokens[0])) {
        myUnversioned.add(myVcsRoot.findFileByRelativePath(file));
      }
      else { //noinspection HardCodedStringLiteral
        if ("M".equals(tokens[0])) {
          if (!myUnmergedNames.add(file)) {
            continue;
          }
          // TODO handle conflict rename-modify
          // TODO handle conflict copy-modify
          // TODO handle conflict delete-modify
          // TODO handle conflict rename-delete
          // assume modify-modify conflict
          ContentRevision before = GitContentRevision.createRevision(myVcsRoot, file, new GitRevisionNumber("orig_head"), myProject, false);
          ContentRevision after = GitContentRevision.createRevision(myVcsRoot, file, null, myProject, false);
          myChanges.add(new Change(before, after, FileStatus.MERGED_WITH_CONFLICTS));
        }
        else {
          throw new VcsException("Unsupported type of the merge conflict detected: " + line);
        }
      }
    }
  }

}
