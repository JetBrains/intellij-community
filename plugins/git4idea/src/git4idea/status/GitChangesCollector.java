// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.status;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitFormatException;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.changes.GitChangeUtils.GitDiffChange;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandler;
import git4idea.commands.GitLineHandler;
import git4idea.diff.GitSubmoduleContentRevision;
import git4idea.repo.GitConflict;
import git4idea.repo.GitConflict.Status;
import git4idea.repo.GitRepository;
import git4idea.repo.GitUntrackedFilesHolder;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * <p>
 * Collects changes from the Git repository in the given {@link com.intellij.openapi.vcs.changes.VcsDirtyScope}
 * by calling {@code 'git status --porcelain -z'} on it.
 * Works only on Git 1.7.0 and later.
 * </p>
 * <p>
 * The class is immutable: collect changes and get the instance from where they can be retrieved by {@link #collect}.
 * </p>
 *
 * @author Kirill Likhodedov
 */
class GitChangesCollector {
  private static final Logger LOG = Logger.getInstance(GitChangesCollector.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitRepository myRepository;
  @NotNull private final VirtualFile myVcsRoot;

  private final Collection<Change> myChanges = new HashSet<>();
  private final Set<FilePath> myUnversionedFiles = new HashSet<>();
  private final Collection<GitConflict> myConflicts = new HashSet<>();

  /**
   * Collects the changes from git command line and returns the instance of GitNewChangesCollector from which these changes can be retrieved.
   * This may be lengthy.
   */
  @NotNull
  static GitChangesCollector collect(@NotNull Project project,
                                     @NotNull Git git,
                                     @NotNull GitRepository repository,
                                     @NotNull Collection<FilePath> dirtyPaths) throws VcsException {
    return new GitChangesCollector(project, git, repository, dirtyPaths);
  }

  @NotNull
  Collection<FilePath> getUnversionedFilePaths() {
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
                              @NotNull GitRepository repository,
                              @NotNull Collection<FilePath> dirtyPaths) throws VcsException {
    myProject = project;
    myGit = git;
    myRepository = repository;
    myVcsRoot = repository.getRoot();

    if (!dirtyPaths.isEmpty()) {
      collectChanges(dirtyPaths);
      collectUnversionedFiles();
    }
  }

  /**
   * Collect dirty file paths, previous changes are included in collection.
   *
   * @return the set of dirty paths to check, grouped by root
   * The paths will be automatically collapsed later if the summary length more than limit, see {@link GitHandler#isLargeCommandLine()}.
   */
  @NotNull
  static Map<VirtualFile, List<FilePath>> collectDirtyPaths(@NotNull AbstractVcs vcs,
                                                            @NotNull VcsDirtyScope dirtyScope,
                                                            @NotNull ChangeListManager changeListManager,
                                                            @NotNull ProjectLevelVcsManager vcsManager) {
    Map<VirtualFile, List<FilePath>> result = new HashMap<>();

    for (FilePath p : dirtyScope.getRecursivelyDirtyDirectories()) {
      addToPaths(p, result, vcs, vcsManager);
    }
    for (FilePath p : dirtyScope.getDirtyFilesNoExpand()) {
      addToPaths(p, result, vcs, vcsManager);
    }

    for (Change c : changeListManager.getAllChanges()) {
      switch (c.getType()) {
        case NEW:
        case DELETED:
        case MOVED:
          FilePath afterPath = ChangesUtil.getAfterPath(c);
          if (afterPath != null) {
            addToPaths(afterPath, result, vcs, vcsManager);
          }
          FilePath beforePath = ChangesUtil.getBeforePath(c);
          if (beforePath != null) {
            addToPaths(beforePath, result, vcs, vcsManager);
          }
        case MODIFICATION:
        default:
          // do nothing
      }
    }

    for (VirtualFile root : result.keySet()) {
      List<FilePath> paths = result.get(root);
      removeCommonParents(paths);
    }

    return result;
  }

  private static void addToPaths(@NotNull FilePath filePath,
                                 @NotNull Map<VirtualFile, List<FilePath>> result,
                                 @NotNull AbstractVcs vcs,
                                 @NotNull ProjectLevelVcsManager vcsManager) {
    VcsRoot vcsRoot = vcsManager.getVcsRootObjectFor(filePath);
    if (vcsRoot != null && vcs.equals(vcsRoot.getVcs())) {
      VirtualFile root = vcsRoot.getPath();
      List<FilePath> paths = result.computeIfAbsent(root, key -> new ArrayList<>());
      paths.add(filePath);
    }
  }

  private static void removeCommonParents(List<FilePath> paths) {
    paths.sort(Comparator.comparing(FilePath::getPath));

    FilePath prevPath = null;
    Iterator<FilePath> it = paths.iterator();
    while (it.hasNext()) {
      FilePath path = it.next();
      // the file is under previous file, so enough to check the parent
      if (prevPath != null && FileUtil.startsWith(path.getPath(), prevPath.getPath(), true)) {
        it.remove();
      }
      else {
        prevPath = path;
      }
    }
  }

  // calls 'git status' and parses the output, feeding myChanges.
  private void collectChanges(Collection<? extends FilePath> dirtyPaths) throws VcsException {
    VcsRevisionNumber head = getHead();

    GitLineHandler handler = GitUtil.createHandlerWithPaths(dirtyPaths, () -> statusHandler());
    String output = myGit.runCommand(handler).getOutputOrThrow();
    List<FilePath> bothModifiedPaths = parseOutput(output, head, handler);

    collectStagedUnstagedModifications(bothModifiedPaths, head);
  }

  private void collectUnversionedFiles() throws VcsException {
    GitUntrackedFilesHolder untrackedFilesHolder = myRepository.getUntrackedFilesHolder();
    myUnversionedFiles.addAll(untrackedFilesHolder.retrieveUntrackedFilePaths());
  }

  private GitLineHandler statusHandler() {
    GitLineHandler handler = new GitLineHandler(myProject, myVcsRoot, GitCommand.STATUS);
    final String[] params = {"--porcelain", "-z", "--untracked-files=no"};   // untracked files are stored separately
    handler.addParameters(params);
    handler.setSilent(true);
    return handler;
  }

  /**
   * Parses the output of the 'git status --porcelain -z' command filling myChanges and myUnversionedFiles.
   * See <a href=http://www.kernel.org/pub/software/scm/git/docs/git-status.html#_output">Git man</a> for details.
   *
   * @param handler used for debugging purposes in case of parse error
   * @return list of MM paths, that should be checked explicitly (in case if staged and unstaged modifications cancel each other)
   */
  @NotNull
  private List<FilePath> parseOutput(@NotNull String output, @NotNull VcsRevisionNumber head, @NotNull GitHandler handler) {
    List<FilePath> bothModifiedPaths = new ArrayList<>();

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

      FilePath oldFilepath;
      if (xStatus == 'R' || xStatus == 'C' ||
          yStatus == 'R' || yStatus == 'C') {
        // We treat "Copy" as "Added", but we still have to read the old path not to break the format parsing.
        //noinspection AssignmentToForLoopParameter
        pos += 1;  // read the "from" filepath which is separated also by NUL character.
        oldFilepath = GitContentRevision.createPath(myVcsRoot, split[pos]);
      }
      else {
        oldFilepath = null;
      }

      switch (xStatus) {
        case ' ':
          if (yStatus == 'M') {
            reportModified(filepath, head);
          }
          else if (yStatus == 'D') {
            reportDeleted(filepath, head);
          }
          else if (yStatus == 'A' || yStatus == 'C') {
            reportAdded(filepath);
          }
          else if (yStatus == 'T') {
            reportTypeChanged(filepath, head);
          }
          else if (yStatus == 'U') {
            reportConflict(filepath, head, Status.MODIFIED, Status.MODIFIED);
          }
          else if (yStatus == 'R') {
            reportRename(filepath, oldFilepath, head);
          }
          else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'M':
          if (yStatus == 'M') {
            bothModifiedPaths.add(filepath); // schedule 'git diff HEAD' command to detect staged changes, that were reverted
          }
          else if (yStatus == ' ' || yStatus == 'T') {
            reportModified(filepath, head);
          }
          else if (yStatus == 'D') {
            reportDeleted(filepath, head);
          }
          else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'C':
        case 'A':
          if (yStatus == 'M' || yStatus == ' ' || yStatus == 'T') {
            reportAdded(filepath);
          }
          else if (yStatus == 'D') {
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
          }
          else if (yStatus == 'U') { // DU - unmerged, deleted by us
            reportConflict(filepath, head, Status.DELETED, Status.MODIFIED);
          }
          else if (yStatus == 'D') { // DD - unmerged, both deleted
            reportConflict(filepath, head, Status.DELETED, Status.DELETED);
          }
          else if (yStatus == 'C') {
            reportModified(filepath, head);
          }
          else if (yStatus == 'R') {
            reportRename(filepath, oldFilepath, head);
          }
          else if (yStatus == 'A') {
            // [DA] status is not documented, but might be reported by git
            reportModified(filepath, head);
          }
          else {
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
          if (yStatus == 'D') {
            reportDeleted(oldFilepath, head);
          }
          else if (yStatus == ' ' || yStatus == 'M' || yStatus == 'T') {
            reportRename(filepath, oldFilepath, head);
          }
          else {
            throwYStatus(output, handler, line, xStatus, yStatus);
          }
          break;

        case 'T'://TODO
          if (yStatus == ' ' || yStatus == 'M') {
            reportTypeChanged(filepath, head);
          }
          else if (yStatus == 'D') {
            reportDeleted(filepath, head);
          }
          else {
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

    return bothModifiedPaths;
  }

  private void collectStagedUnstagedModifications(@NotNull List<FilePath> bothModifiedPaths,
                                                  @NotNull VcsRevisionNumber head) throws VcsException {
    if (bothModifiedPaths.isEmpty()) return;

    Collection<GitDiffChange> changes = GitChangeUtils.getWorkingTreeChanges(myProject, myVcsRoot, bothModifiedPaths, false);

    // no directories expected here, hierarchical comparator is not necessary
    Set<FilePath> expectedPaths = new HashSet<>(bothModifiedPaths);

    for (GitDiffChange change : changes) {
      FilePath filePath = change.getFilePath();
      if (expectedPaths.contains(filePath)) {
        reportModified(filePath, head);
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
    Change change = new Change(before, after, status);

    FilePath filePath = ChangesUtil.getFilePath(change);
    VirtualFile root = ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(filePath);
    boolean isUnderOurRoot = myVcsRoot.equals(root) ||
                             before instanceof GitSubmoduleContentRevision ||
                             after instanceof GitSubmoduleContentRevision;
    if (!isUnderOurRoot) {
      LOG.warn(String.format("Ignoring change under another root: %s; root: %s; mapped root: %s", change, myVcsRoot, root));
      return;
    }

    myChanges.add(change);
  }
}
