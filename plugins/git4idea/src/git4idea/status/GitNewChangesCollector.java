/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitFormatException;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandler;
import git4idea.commands.GitSimpleHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitUntrackedFilesHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>
 *   Collects changes from the Git repository in the given {@link com.intellij.openapi.vcs.changes.VcsDirtyScope}
 *   by calling {@code 'git status --porcelain -z'} on it.
 *   Works only on Git 1.7.0 and later.
 * </p>
 * <p>
 *   The class is immutable: collect changes and get the instance from where they can be retrieved by {@link #collect}.
 * </p>
 *
 * @author Kirill Likhodedov
 */
class GitNewChangesCollector extends GitChangesCollector {

  private static final Logger LOG = Logger.getInstance(GitNewChangesCollector.class);
  private final GitRepository myRepository;
  private final Collection<Change> myChanges = new HashSet<>();
  private final Set<VirtualFile> myUnversionedFiles = new HashSet<>();
  @NotNull private final Git myGit;

  /**
   * Collects the changes from git command line and returns the instance of GitNewChangesCollector from which these changes can be retrieved.
   * This may be lengthy.
   */
  @NotNull
  static GitNewChangesCollector collect(@NotNull Project project, @NotNull Git git, @NotNull ChangeListManager changeListManager,
                                        @NotNull ProjectLevelVcsManager vcsManager, @NotNull AbstractVcs vcs,
                                        @NotNull VcsDirtyScope dirtyScope, @NotNull VirtualFile vcsRoot) throws VcsException {
    return new GitNewChangesCollector(project, git, changeListManager, vcsManager, vcs, dirtyScope, vcsRoot);
  }

  @Override
  @NotNull
  Collection<VirtualFile> getUnversionedFiles() {
    return myUnversionedFiles;
  }

  @NotNull
  @Override
  Collection<Change> getChanges() {
    return myChanges;
  }

  private GitNewChangesCollector(@NotNull Project project, @NotNull Git git, @NotNull ChangeListManager changeListManager,
                                 @NotNull ProjectLevelVcsManager vcsManager, @NotNull AbstractVcs vcs,
                                 @NotNull VcsDirtyScope dirtyScope, @NotNull VirtualFile vcsRoot) throws VcsException
  {
    super(project, changeListManager, vcsManager, vcs, dirtyScope, vcsRoot);
    myGit = git;
    myRepository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(vcsRoot);

    Collection<FilePath> dirtyPaths = dirtyPaths(true);
    if (!dirtyPaths.isEmpty()) {
      collectChanges(dirtyPaths);
      collectUnversionedFiles();
    }
  }

  // calls 'git status' and parses the output, feeding myChanges.
  private void collectChanges(Collection<FilePath> dirtyPaths) throws VcsException {
    GitSimpleHandler handler = statusHandler(dirtyPaths);
    String output = handler.run();
    parseOutput(output, handler);
  }

  private void collectUnversionedFiles() throws VcsException {
    if (myRepository == null) {
      // if GitRepository was not initialized at the time of creation of the GitNewChangesCollector => collecting unversioned files by hands.
      myUnversionedFiles.addAll(myGit.untrackedFiles(myProject, myVcsRoot, null));
    } else {
      GitUntrackedFilesHolder untrackedFilesHolder = myRepository.getUntrackedFilesHolder();
      myUnversionedFiles.addAll(untrackedFilesHolder.retrieveUntrackedFiles());
    }
  }

  private GitSimpleHandler statusHandler(Collection<FilePath> dirtyPaths) {
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.STATUS);
    final String[] params = {"--porcelain", "-z", "--untracked-files=no"};   // untracked files are stored separately
    handler.addParameters(params);
    handler.endOptions();
    handler.addRelativePaths(dirtyPaths);
    if (handler.isLargeCommandLine()) {
      // if there are too much files, just get all changes for the project
      handler = new GitSimpleHandler(myProject, myVcsRoot, GitCommand.STATUS);
      handler.addParameters(params);
      handler.endOptions();
    }
    handler.setSilent(true);
    return handler;
  }

  /**
   * Parses the output of the 'git status --porcelain -z' command filling myChanges and myUnversionedFiles.
   * See <a href=http://www.kernel.org/pub/software/scm/git/docs/git-status.html#_output">Git man</a> for details.
   */
  // handler is here for debugging purposes in the case of parse error
  private void parseOutput(@NotNull String output, @NotNull GitHandler handler) throws VcsException {
    VcsRevisionNumber head = getHead();

    final String[] split = output.split("\u0000");

    for (int pos = 0; pos < split.length; pos++) {
      String line = split[pos];
      if (StringUtil.isEmptyOrSpaces(line)) { // skip empty lines if any (e.g. the whole output may be empty on a clean working tree).
        continue;
      }

      // format: XY_filename where _ stands for space.
      if (line.length() < 4) { // X, Y, space and at least one symbol for the file
        throwGFE("Line is too short.", handler, output, line, '0', '0');
      }
      final String xyStatus = line.substring(0, 2);
      final String filepath = line.substring(3); // skipping the space
      final char xStatus = xyStatus.charAt(0);
      final char yStatus = xyStatus.charAt(1);

      switch (xStatus) {
        case ' ':
          if (yStatus == 'M') {
            reportModified(filepath, head);
          } else if (yStatus == 'D') {
            reportDeleted(filepath, head);
          } else if (yStatus == 'T') {
            reportTypeChanged(filepath, head);
          } else if (yStatus == 'U') {
            reportConflict(filepath, head);
          } else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'M':
          if (yStatus == ' ' || yStatus == 'M' || yStatus == 'T') {
            reportModified(filepath, head);
          } else if (yStatus == 'D') {
            reportDeleted(filepath, head);
          } else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'C':
          //noinspection AssignmentToForLoopParameter
          pos += 1;  // read the "from" filepath which is separated also by NUL character.
          // NB: no "break" here!
          // we treat "Copy" as "Added", but we still have to read the old path not to break the format parsing.
        case 'A':
          if (yStatus == 'M' || yStatus == ' ' || yStatus == 'T') {
            reportAdded(filepath);
          } else if (yStatus == 'D') {
            // added + deleted => no change (from IDEA point of view).
          } else if (yStatus == 'U' || yStatus == 'A') { // AU - unmerged, added by us; AA - unmerged, both added
            reportConflict(filepath, head);
          }  else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'D':
          if (yStatus == 'M' || yStatus == ' ' || yStatus == 'T') {
            reportDeleted(filepath, head);
          } else if (yStatus == 'U') { // DU - unmerged, deleted by us
            reportConflict(filepath, head);
          } else if (yStatus == 'D') { // DD - unmerged, both deleted
            // TODO
            // currently not displaying, because "both deleted" conflicts can't be handled by our conflict resolver.
            // see IDEA-63156
          } else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'U':
          if (yStatus == 'U' || yStatus == 'A' || yStatus == 'D' || yStatus == 'T') {
            // UU - unmerged, both modified; UD - unmerged, deleted by them; UA - unmerged, added by them
            reportConflict(filepath, head);
          } else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'R':
          //noinspection AssignmentToForLoopParameter
          pos += 1;  // read the "from" filepath which is separated also by NUL character.
          String oldFilename = split[pos];

          if (yStatus == 'D') {
            reportDeleted(filepath, head);
          } else if (yStatus == ' ' || yStatus == 'M' || yStatus == 'T') {
            reportRename(filepath, oldFilename, head);
          } else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'T'://TODO
          if (yStatus == ' ' || yStatus == 'M') {
            reportTypeChanged(filepath, head);
          } else if (yStatus == 'D') {
            reportDeleted(filepath, head);
          } else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case '?':
          throwGFE("Unexpected unversioned file flag.", handler, output, line, xStatus, yStatus);
          break;

        case '!':
          throwGFE("Unexpected ignored file flag.", handler, output, line, xStatus, yStatus);

        default:
          throwGFE("Unexpected symbol as xStatus.", handler, output, line, xStatus, yStatus);

      }
    }
  }

  @NotNull
  private VcsRevisionNumber getHead() throws VcsException {
    if (myRepository != null) {
      // we force update the GitRepository, because update is asynchronous, and thus the GitChangeProvider may be asked for changes
      // before the GitRepositoryUpdater has captures the current revision change and has updated the GitRepository.
      myRepository.update();
      final String rev = myRepository.getCurrentRevision();
      return rev != null ? new GitRevisionNumber(rev) : VcsRevisionNumber.NULL;
    } else {
      // this may happen on the project startup, when GitChangeProvider may be queried before GitRepository has been initialized.
      LOG.info("GitRepository is null for root " + myVcsRoot);
      return getHeadFromGit();
    }
  }

  @NotNull
  private VcsRevisionNumber getHeadFromGit() throws VcsException {
    VcsRevisionNumber nativeHead = VcsRevisionNumber.NULL;
    try {
      nativeHead = GitChangeUtils.resolveReference(myProject, myVcsRoot, "HEAD");
    }
    catch (VcsException e) {
      if (!GitChangeUtils.isHeadMissing(e)) { // fresh repository
        throw e;
      }
    }
    return nativeHead;
  }

  private static void throwYStatus(String output, GitHandler handler, String line, char xStatus, char yStatus) {
    throwGFE("Unexpected symbol as yStatus.", handler, output, line, xStatus, yStatus);
  }

  private static void throwGFE(String message, GitHandler handler, String output, String line, char xStatus, char yStatus) {
    throw new GitFormatException(String.format("%s\n xStatus=[%s], yStatus=[%s], line=[%s], \n" +
                                               "handler:\n%s\n output: \n%s",
                                               message, xStatus, yStatus, line.replace('\u0000', '!'), handler, output));
  }

  private void reportModified(String filepath, VcsRevisionNumber head) throws VcsException {
    ContentRevision before = GitContentRevision.createRevision(myVcsRoot, filepath, head, myProject, false, true, false);
    ContentRevision after = GitContentRevision.createRevision(myVcsRoot, filepath, null, myProject, false, false, false);
    reportChange(FileStatus.MODIFIED, before, after);
  }

  private void reportTypeChanged(String filepath, VcsRevisionNumber head) throws VcsException {
    ContentRevision before = GitContentRevision.createRevision(myVcsRoot, filepath, head, myProject, false, true, false);
    ContentRevision after = GitContentRevision.createRevisionForTypeChange(myProject, myVcsRoot, filepath, null, false);
    reportChange(FileStatus.MODIFIED, before, after);
  }

  private void reportAdded(String filepath) throws VcsException {
    ContentRevision before = null;
    ContentRevision after = GitContentRevision.createRevision(myVcsRoot, filepath, null, myProject, false, false, false);
    reportChange(FileStatus.ADDED, before, after);
  }

  private void reportDeleted(String filepath, VcsRevisionNumber head) throws VcsException {
    ContentRevision before = GitContentRevision.createRevision(myVcsRoot, filepath, head, myProject, true, true, false);
    ContentRevision after = null;
    reportChange(FileStatus.DELETED, before, after);
  }

  private void reportRename(String filepath, String oldFilename, VcsRevisionNumber head) throws VcsException {
    ContentRevision before = GitContentRevision.createRevision(myVcsRoot, oldFilename, head, myProject, true, true, false);
    ContentRevision after = GitContentRevision.createRevision(myVcsRoot, filepath, null, myProject, false, false, false);
    reportChange(FileStatus.MODIFIED, before, after);
  }

  private void reportConflict(String filepath, VcsRevisionNumber head) throws VcsException {
    ContentRevision before = GitContentRevision.createRevision(myVcsRoot, filepath, head, myProject, false, true, false);
    ContentRevision after = GitContentRevision.createRevision(myVcsRoot, filepath, null, myProject, false, false, false);
    reportChange(FileStatus.MERGED_WITH_CONFLICTS, before, after);
  }

  private void reportChange(FileStatus status, ContentRevision before, ContentRevision after) {
    myChanges.add(new Change(before, after, status));
  }

}
