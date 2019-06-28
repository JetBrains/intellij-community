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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitFormatException;
import git4idea.GitRevisionNumber;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandler;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitConflict;
import git4idea.repo.GitConflict.Status;
import git4idea.repo.GitRepository;
import git4idea.repo.GitUntrackedFilesHolder;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
class GitChangesCollector {
  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final AbstractVcs myVcs;
  @NotNull private final VcsDirtyScope myDirtyScope;
  @NotNull private final GitRepository myRepository;
  @NotNull private final VirtualFile myVcsRoot;

  private final Collection<Change> myChanges = new HashSet<>();
  private final Set<VirtualFile> myUnversionedFiles = new HashSet<>();
  private final Collection<GitConflict> myConflicts = new HashSet<>();

  /**
   * Collects the changes from git command line and returns the instance of GitNewChangesCollector from which these changes can be retrieved.
   * This may be lengthy.
   */
  @NotNull
  static GitChangesCollector collect(@NotNull Project project,
                                     @NotNull Git git,
                                     @NotNull ChangeListManager changeListManager,
                                     @NotNull ProjectLevelVcsManager vcsManager,
                                     @NotNull AbstractVcs vcs,
                                     @NotNull VcsDirtyScope dirtyScope,
                                     @NotNull GitRepository repository) throws VcsException {
    return new GitChangesCollector(project, git, changeListManager, vcsManager, vcs, dirtyScope, repository);
  }

  @NotNull
  Collection<VirtualFile> getUnversionedFiles() {
    return myUnversionedFiles;
  }

  @NotNull
  Collection<Change> getChanges() {
    return myChanges;
  }

  Collection<GitConflict> getConflicts() {
    return myConflicts;
  }

  private GitChangesCollector(@NotNull Project project,
                              @NotNull Git git,
                              @NotNull ChangeListManager changeListManager,
                              @NotNull ProjectLevelVcsManager vcsManager,
                              @NotNull AbstractVcs vcs,
                              @NotNull VcsDirtyScope dirtyScope,
                              @NotNull GitRepository repository) throws VcsException {
    myProject = project;
    myGit = git;
    myChangeListManager = changeListManager;
    myVcsManager = vcsManager;
    myVcs = vcs;
    myDirtyScope = dirtyScope;
    myRepository = repository;
    myVcsRoot = repository.getRoot();

    Collection<FilePath> dirtyPaths = dirtyPaths();
    if (!dirtyPaths.isEmpty()) {
      collectChanges(dirtyPaths);
      collectUnversionedFiles();
    }
  }

  /**
   * Collect dirty file paths, previous changes are included in collection.
   *
   * @return the set of dirty paths to check, the paths are automatically collapsed if the summary length more than limit
   */
  private Collection<FilePath> dirtyPaths() {
    final List<String> allPaths = new ArrayList<>();

    for (FilePath p : myDirtyScope.getRecursivelyDirtyDirectories()) {
      addToPaths(p, allPaths);
    }
    for (FilePath p : myDirtyScope.getDirtyFilesNoExpand()) {
      addToPaths(p, allPaths);
    }

    for (Change c : myChangeListManager.getChangesIn(myVcsRoot)) {
      switch (c.getType()) {
        case NEW:
        case DELETED:
        case MOVED:
          ContentRevision afterRevision = c.getAfterRevision();
          if (afterRevision != null) {
            addToPaths(afterRevision.getFile(), allPaths);
          }
          ContentRevision beforeRevision = c.getBeforeRevision();
          if (beforeRevision != null) {
            addToPaths(beforeRevision.getFile(), allPaths);
          }
        case MODIFICATION:
        default:
          // do nothing
      }
    }

    removeCommonParents(allPaths);

    return ContainerUtil.map(allPaths, VcsUtil::getFilePath);
  }

  private void addToPaths(FilePath pathToAdd, List<? super String> paths) {
    VcsRoot fileRoot = myVcsManager.getVcsRootObjectFor(pathToAdd);
    if (fileRoot != null && fileRoot.getVcs() != null && myVcs.equals(fileRoot.getVcs()) && myVcsRoot.equals(fileRoot.getPath())) {
      paths.add(pathToAdd.getPath());
    }
  }

  private static void removeCommonParents(List<String> allPaths) {
    Collections.sort(allPaths);

    String prevPath = null;
    Iterator<String> it = allPaths.iterator();
    while (it.hasNext()) {
      String path = it.next();
      if (prevPath != null && FileUtil.startsWith(path, prevPath, true)) { // the file is under previous file, so enough to check the parent
        it.remove();
      }
      else {
        prevPath = path;
      }
    }
  }

  // calls 'git status' and parses the output, feeding myChanges.
  private void collectChanges(Collection<? extends FilePath> dirtyPaths) throws VcsException {
    GitLineHandler handler = statusHandler(dirtyPaths);
    String output = myGit.runCommand(handler).getOutputOrThrow();
    parseOutput(output, handler);
  }

  private void collectUnversionedFiles() throws VcsException {
    GitUntrackedFilesHolder untrackedFilesHolder = myRepository.getUntrackedFilesHolder();
    myUnversionedFiles.addAll(untrackedFilesHolder.retrieveUntrackedFiles());
  }

  private GitLineHandler statusHandler(Collection<? extends FilePath> dirtyPaths) {
    GitLineHandler handler = new GitLineHandler(myProject, myVcsRoot, GitCommand.STATUS);
    final String[] params = {"--porcelain", "-z", "--untracked-files=no"};   // untracked files are stored separately
    handler.addParameters(params);
    handler.endOptions();
    handler.addRelativePaths(dirtyPaths);
    if (handler.isLargeCommandLine()) {
      // if there are too much files, just get all changes for the project
      handler = new GitLineHandler(myProject, myVcsRoot, GitCommand.STATUS);
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
      final String path = line.substring(3); // skipping the space
      final char xStatus = xyStatus.charAt(0);
      final char yStatus = xyStatus.charAt(1);

      final FilePath filepath = GitContentRevision.createPath(myVcsRoot, path);

      switch (xStatus) {
        case ' ':
          if (yStatus == 'M') {
            reportModified(filepath, head);
          } else if (yStatus == 'D') {
            reportDeleted(filepath, head);
          } else if (yStatus == 'A') {
            reportAdded(filepath);
          } else if (yStatus == 'T') {
            reportTypeChanged(filepath, head);
          } else if (yStatus == 'U') {
            reportConflict(filepath, head, Status.MODIFIED, Status.MODIFIED);
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
          }
          else if (yStatus == 'U') { // AU - unmerged, added by us
            reportConflict(filepath, head, Status.ADDED, Status.MODIFIED);
          }
          else if (yStatus == 'A') { // AA - unmerged, both added
            reportConflict(filepath, head, Status.ADDED, Status.ADDED);
          }
          else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'D':
          if (yStatus == 'M' || yStatus == ' ' || yStatus == 'T') {
            reportDeleted(filepath, head);
          } else if (yStatus == 'U') { // DU - unmerged, deleted by us
            reportConflict(filepath, head, Status.DELETED, Status.MODIFIED);
          } else if (yStatus == 'D') { // DD - unmerged, both deleted
            reportConflict(filepath, head, Status.DELETED, Status.DELETED);
          } else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'U':
          if (yStatus == 'U' || yStatus == 'T') { // UU - unmerged, both modified
            reportConflict(filepath, head, Status.MODIFIED, Status.MODIFIED);
          }
          else if (yStatus == 'A') { // UA - unmerged, added by them
            reportConflict(filepath, head, Status.MODIFIED, Status.ADDED);
          }
          else if (yStatus == 'D') { // UD - unmerged, deleted by them
            reportConflict(filepath, head, Status.MODIFIED, Status.DELETED);
          }
          else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'R':
          //noinspection AssignmentToForLoopParameter
          pos += 1;  // read the "from" filepath which is separated also by NUL character.
          FilePath oldFilepath = GitContentRevision.createPath(myVcsRoot, split[pos]);

          if (yStatus == 'D') {
            reportDeleted(oldFilepath, head);
          } else if (yStatus == ' ' || yStatus == 'M' || yStatus == 'T') {
            reportRename(filepath, oldFilepath, head);
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
    // we force update the GitRepository, because update is asynchronous, and thus the GitChangeProvider may be asked for changes
    // before the GitRepositoryUpdater has captures the current revision change and has updated the GitRepository.
    myRepository.update();
    final String rev = myRepository.getCurrentRevision();
    return rev != null ? new GitRevisionNumber(rev) : VcsRevisionNumber.NULL;
  }

  private static void throwYStatus(String output, GitHandler handler, String line, char xStatus, char yStatus) {
    throwGFE("Unexpected symbol as yStatus.", handler, output, line, xStatus, yStatus);
  }

  private static void throwGFE(String message, GitHandler handler, String output, String line, char xStatus, char yStatus) {
    throw new GitFormatException(String.format("%s\n xStatus=[%s], yStatus=[%s], line=[%s], \n" +
                                               "handler:\n%s\n output: \n%s",
                                               message, xStatus, yStatus, line.replace('\u0000', '!'), handler, output));
  }

  private void reportModified(FilePath filepath, VcsRevisionNumber head) {
    ContentRevision before = GitContentRevision.createRevision(filepath, head, myProject);
    ContentRevision after = GitContentRevision.createRevision(filepath, null, myProject);
    reportChange(FileStatus.MODIFIED, before, after);
  }

  private void reportTypeChanged(FilePath filepath, VcsRevisionNumber head) {
    ContentRevision before = GitContentRevision.createRevision(filepath, head, myProject);
    ContentRevision after = GitContentRevision.createRevisionForTypeChange(filepath, null, myProject);
    reportChange(FileStatus.MODIFIED, before, after);
  }

  private void reportAdded(FilePath filepath) {
    ContentRevision before = null;
    ContentRevision after = GitContentRevision.createRevision(filepath, null, myProject);
    reportChange(FileStatus.ADDED, before, after);
  }

  private void reportDeleted(FilePath filepath, VcsRevisionNumber head) {
    ContentRevision before = GitContentRevision.createRevision(filepath, head, myProject);
    ContentRevision after = null;
    reportChange(FileStatus.DELETED, before, after);
  }

  private void reportRename(FilePath filepath, FilePath oldFilepath, VcsRevisionNumber head) {
    ContentRevision before = GitContentRevision.createRevision(oldFilepath, head, myProject);
    ContentRevision after = GitContentRevision.createRevision(filepath, null, myProject);
    reportChange(FileStatus.MODIFIED, before, after);
  }

  private void reportConflict(FilePath filepath, VcsRevisionNumber head, Status oursStatus, Status theirsStatus) {
    myConflicts.add(new GitConflict(myVcsRoot, filepath, oursStatus, theirsStatus));

    // TODO: currently not displaying "both deleted" conflicts, because they can't be handled by GitMergeProvider (see IDEA-63156)
    if (oursStatus != Status.DELETED || theirsStatus != Status.DELETED) {
      ContentRevision before = GitContentRevision.createRevision(filepath, head, myProject);
      ContentRevision after = GitContentRevision.createRevision(filepath, null, myProject);
      reportChange(FileStatus.MERGED_WITH_CONFLICTS, before, after);
    }
  }

  private void reportChange(FileStatus status, ContentRevision before, ContentRevision after) {
    myChanges.add(new Change(before, after, status));
  }
}
