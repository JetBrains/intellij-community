// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.status;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vcs.util.paths.RecursiveFilePathSet;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitRefreshUsageCollector;
import git4idea.commands.GitHandler;
import git4idea.index.GitFileStatus;
import git4idea.index.GitIndexStatusUtilKt;
import git4idea.repo.GitConflict;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitStagingAreaHolder {
  private static final Logger LOG = Logger.getInstance(GitStagingAreaHolder.class);
  public static final Topic<StagingAreaListener> TOPIC = Topic.create("GitStagingAreaHolder change", StagingAreaListener.class);

  private final Project myProject;
  private final GitRepository myRepository;

  private final Object LOCK = new Object();
  private final List<GitFileStatus> myRecords = new ArrayList<>();

  public GitStagingAreaHolder(@NotNull GitRepository repository) {
    myProject = repository.getProject();
    myRepository = repository;
  }

  public @NotNull List<GitFileStatus> getAllRecords() {
    synchronized (LOCK) {
      return new ArrayList<>(myRecords);
    }
  }

  public @Nullable GitFileStatus findRecord(@NotNull FilePath path) {
    synchronized (LOCK) {
      return ContainerUtil.find(myRecords, it -> it.getPath().equals(path));
    }
  }

  public boolean hasConflicts() {
    synchronized (LOCK) {
      return ContainerUtil.exists(myRecords, record -> GitIndexStatusUtilKt.isConflicted(record.getIndex(), record.getWorkTree()));
    }
  }

  public @NotNull List<GitConflict> getAllConflicts() {
    synchronized (LOCK) {
      return ContainerUtil.mapNotNull(myRecords, this::createConflict);
    }
  }

  public @Nullable GitConflict findConflict(@NotNull FilePath path) {
    return createConflict(findRecord(path));
  }

  private @Nullable GitConflict createConflict(@Nullable GitFileStatus record) {
    if (record == null) return null;
    return createConflict(myRepository.getRoot(), record);
  }

  public static @Nullable GitConflict createConflict(@NotNull VirtualFile root, @NotNull GitFileStatus record) {
    if (!GitIndexStatusUtilKt.isConflicted(record.getIndex(), record.getWorkTree())) return null;
    return new GitConflict(root, record.getPath(),
                           getConflictStatus(record.getIndex()), getConflictStatus(record.getWorkTree()));
  }

  private static @NotNull GitConflict.Status getConflictStatus(char status) {
    if (status == 'A') return GitConflict.Status.ADDED;
    if (status == 'D') return GitConflict.Status.DELETED;
    return GitConflict.Status.MODIFIED;
  }


  /**
   * untracked/ignored files are processed separately in {@link git4idea.repo.GitUntrackedFilesHolder} and {@link git4idea.ignore.GitRepositoryIgnoredFilesHolder}
   */
  @ApiStatus.Internal
  public @NotNull List<GitFileStatus> refresh(@NotNull List<FilePath> dirtyPaths) throws VcsException {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    VirtualFile root = myRepository.getRoot();

    RecursiveFilePathSet dirtyScope = new RecursiveFilePathSet(true); // GitVcs#needsCaseSensitiveDirtyScope is true
    dirtyScope.addAll(dirtyPaths);

    boolean everythingDirty = dirtyScope.contains(VcsUtil.getFilePath(root));
    StructuredIdeActivity activity = GitRefreshUsageCollector.logStatusRefresh(myProject, everythingDirty);
    List<GitFileStatus> rootRecords = GitIndexStatusUtilKt.getStatus(myProject, root, dirtyPaths, true, false, false);
    activity.finished();

    rootRecords.removeIf(record -> {
      boolean isUnderDirtyScope = isUnder(record, dirtyScope);
      if (!isUnderDirtyScope) return true;

      VirtualFile recordRoot = vcsManager.getVcsRootFor(record.getPath());
      boolean isUnderOurRoot = root.equals(recordRoot) || isSubmoduleStatus(record, recordRoot);
      if (!isUnderOurRoot) {
        LOG.warn(String.format("Ignoring change under another root: %s; root: %s; mapped root: %s", record, root, recordRoot));
        return true;
      }

      return false;
    });

    synchronized (LOCK) {
      myRecords.removeIf(record -> isUnder(record, dirtyScope));
      myRecords.addAll(rootRecords);
    }

    BackgroundTaskUtil.syncPublisher(myProject, TOPIC).stagingAreaChanged(myRepository);

    return rootRecords;
  }

  private static boolean isUnder(@NotNull GitFileStatus record, @NotNull RecursiveFilePathSet dirtyScope) {
    return dirtyScope.hasAncestor(record.getPath()) ||
           record.getOrigPath() != null && dirtyScope.hasAncestor(record.getOrigPath());
  }

  private static boolean isSubmoduleStatus(@NotNull GitFileStatus record, @Nullable VirtualFile candidateRoot) {
    if (candidateRoot == null) return false;
    String recordPath = record.getPath().getPath();
    String rootPath = candidateRoot.getPath();
    return recordPath.equals(rootPath);
  }

  /**
   * Collect dirty file paths, previous changes are included in collection.
   *
   * @return the map whose values are lists of dirty paths to check, grouped by root
   * The paths will be automatically collapsed later if the summary length more than limit, see {@link GitHandler#isLargeCommandLine()}.
   */
  @ApiStatus.Internal
  public static @NotNull Map<VirtualFile, List<FilePath>> collectDirtyPathsPerRoot(@NotNull VcsDirtyScope dirtyScope) {
    Project project = dirtyScope.getProject();
    AbstractVcs vcs = dirtyScope.getVcs();
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);

    Map<VirtualFile, List<FilePath>> result = new HashMap<>();
    for (FilePath p : dirtyScope.getRecursivelyDirtyDirectories()) {
      addToPaths(p, result, vcs, vcsManager);
    }
    for (FilePath p : dirtyScope.getDirtyFilesNoExpand()) {
      addToPaths(p, result, vcs, vcsManager);
    }

    // do not use belongsTo to skip non-recursively dirty directories
    RecursiveFilePathSet recursivelyDirtyDirectories = new RecursiveFilePathSet(true);
    recursivelyDirtyDirectories.addAll(dirtyScope.getRecursivelyDirtyDirectories());

    // process nested vcs roots
    for (VirtualFile root : vcsManager.getRootsUnderVcs(vcs)) {
      FilePath rootPath = VcsUtil.getFilePath(root);
      if (recursivelyDirtyDirectories.hasAncestor(rootPath)) {
        LOG.debug("adding git root for check. root: ", root);
        List<FilePath> paths = result.computeIfAbsent(root, key -> new ArrayList<>());
        paths.add(rootPath);
      }
    }

    // Git will not detect renames unless both affected paths are passed to the 'git status' command.
    // Thus, we are forced to pass all deleted/added files in a repository to ensure all renames are detected.
    for (VirtualFile root : result.keySet()) {
      GitRepository gitRepository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root);
      if (gitRepository == null) continue;

      List<FilePath> rootPaths = result.get(root);

      List<GitFileStatus> records = gitRepository.getStagingAreaHolder().getAllRecords();
      for (GitFileStatus record : records) {
        FilePath filePath = record.getPath();
        FilePath origPath = record.getOrigPath();
        if (origPath != null) {
          rootPaths.add(origPath);
          rootPaths.add(filePath);
        }
        else if (isStatusCodeForPotentialRename(record.getIndex()) ||
                 isStatusCodeForPotentialRename(record.getWorkTree())) {
          rootPaths.add(filePath);
        }
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

  private static boolean isStatusCodeForPotentialRename(char statusCode) {
    return GitIndexStatusUtilKt.isRenamed(statusCode) ||
           GitIndexStatusUtilKt.isDeleted(statusCode) ||
           GitIndexStatusUtilKt.isAdded(statusCode);
  }

  public interface StagingAreaListener {
    void stagingAreaChanged(@NotNull GitRepository repository);
  }
}
