// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.status;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.index.GitFileStatus;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Git repository change provider
 */
public final class GitChangeProvider implements ChangeProvider {
  static final Logger LOG = Logger.getInstance("#GitStatus");
  public static final Topic<ChangeProviderListener> TOPIC = Topic.create("GitChangeProvider Progress", ChangeProviderListener.class);
  private volatile boolean isRefreshInProgress = false;

  @NotNull private final Project project;

  public GitChangeProvider(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void getChanges(@NotNull VcsDirtyScope dirtyScope,
                         @NotNull ChangelistBuilder builder,
                         @NotNull ProgressIndicator progress,
                         @NotNull ChangeListManagerGate addGate) throws VcsException {
    BackgroundTaskUtil.syncPublisher(project, TOPIC).progressStarted();
    isRefreshInProgress = true;

    LOG.debug("initial dirty scope: ", dirtyScope);
    appendNestedVcsRootsToDirt((VcsModifiableDirtyScope)dirtyScope);
    LOG.debug("after adding nested vcs roots to dirt: ", dirtyScope);

    GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
    Collection<VirtualFile> affectedRoots = dirtyScope.getAffectedContentRoots();
    Set<GitRepository> repos = ContainerUtil.map2SetNotNull(affectedRoots, repositoryManager::getRepositoryForRoot);

    try {
      List<FilePath> newDirtyPaths = new ArrayList<>();
      NonChangedHolder holder = new NonChangedHolder(project, addGate);

      Map<VirtualFile, List<FilePath>> dirtyPaths = GitStagingAreaHolder.collectDirtyPaths(dirtyScope);

      for (GitRepository repo : repos) {
        LOG.debug("checking root: ", repo.getRoot());
        List<FilePath> rootDirtyPaths = ContainerUtil.notNullize(dirtyPaths.get(repo.getRoot()));
        if (rootDirtyPaths.isEmpty()) continue;

        GitStagingAreaHolder stageAreaHolder = repo.getStagingAreaHolder();
        List<GitFileStatus> newChanges = stageAreaHolder.refresh(rootDirtyPaths);

        GitChangesCollector collector = GitChangesCollector.collect(project, repo, newChanges);
        holder.markHeadRevision(repo.getRoot(), collector.getHead());

        Collection<Change> changes = collector.getChanges();
        for (Change change : changes) {
          LOG.debug("process change: ", change);
          builder.processChange(change, GitVcs.getKey());

          FilePath beforePath = ChangesUtil.getBeforePath(change);
          FilePath afterPath = ChangesUtil.getAfterPath(change);

          if (beforePath != null) holder.markPathProcessed(beforePath);
          if (afterPath != null) holder.markPathProcessed(afterPath);

          if (change.isMoved() || change.isRenamed()) {
            if (dirtyScope.belongsTo(beforePath) != dirtyScope.belongsTo(afterPath)) {
              LOG.debug("schedule rename check for: ", change);
              newDirtyPaths.add(beforePath);
              newDirtyPaths.add(afterPath);
            }
          }
        }

        Collection<FilePath> untracked = repo.getUntrackedFilesHolder().retrieveUntrackedFilePaths();
        for (FilePath path : untracked) {
          builder.processUnversionedFile(path);
          holder.markPathProcessed(path);
        }

        BackgroundTaskUtil.syncPublisher(project, TOPIC).repositoryUpdated(repo);
      }
      holder.feedBuilder(dirtyScope, builder);

      VcsDirtyScopeManager.getInstance(project).filePathsDirty(newDirtyPaths, null);
    }
    finally {
      isRefreshInProgress = false;
      BackgroundTaskUtil.syncPublisher(project, TOPIC).progressStopped();
    }
  }

  private static void appendNestedVcsRootsToDirt(@NotNull VcsModifiableDirtyScope dirtyScope) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(dirtyScope.getProject());

    final Set<FilePath> recursivelyDirtyDirectories = dirtyScope.getRecursivelyDirtyDirectories();
    if (recursivelyDirtyDirectories.isEmpty()) {
      return;
    }

    VirtualFile[] rootsUnderGit = vcsManager.getRootsUnderVcs(dirtyScope.getVcs());

    Set<VirtualFile> dirtyDirs = new HashSet<>();
    for (FilePath dir : recursivelyDirtyDirectories) {
      VirtualFile vf = VcsUtil.getVirtualFileWithRefresh(dir.getIOFile());
      if (vf != null) {
        dirtyDirs.add(vf);
      }
    }

    for (VirtualFile root : rootsUnderGit) {
      if (dirtyDirs.contains(root)) continue;

      for (VirtualFile dirtyDir : dirtyDirs) {
        if (VfsUtilCore.isAncestor(dirtyDir, root, false)) {
          LOG.debug("adding git root for check. root: ", root, ", dir: ", dirtyDir);
          dirtyScope.addDirtyDirRecursively(VcsUtil.getFilePath(root));
          break;
        }
      }
    }
  }

  private static class NonChangedHolder {
    private final Project myProject;
    private final ChangeListManagerGate myAddGate;

    private final Set<FilePath> myProcessedPaths = new HashSet<>();
    private final Map<VirtualFile, VcsRevisionNumber> myHeadRevisions = new HashMap<>();

    private NonChangedHolder(Project project, ChangeListManagerGate addGate) {
      myProject = project;
      myAddGate = addGate;
    }

    public void markPathProcessed(@NotNull FilePath path) {
      myProcessedPaths.add(path);
    }

    public void markHeadRevision(@NotNull VirtualFile root, @NotNull VcsRevisionNumber revision) {
      myHeadRevisions.put(root, revision);
    }

    public void feedBuilder(@NotNull VcsDirtyScope dirtyScope, @NotNull ChangelistBuilder builder) throws VcsException {
      final VcsKey gitKey = GitVcs.getKey();

      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      for (Document document : fileDocumentManager.getUnsavedDocuments()) {
        VirtualFile vf = fileDocumentManager.getFile(document);
        if (vf == null || !vf.isValid()) continue;
        if (!fileDocumentManager.isFileModified(vf)) continue;
        if (myAddGate.getStatus(vf) != null) continue;

        FilePath filePath = VcsUtil.getFilePath(vf);
        if (myProcessedPaths.contains(filePath)) continue;
        if (!dirtyScope.belongsTo(filePath)) continue;

        GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(vf);
        if (repository == null) continue;
        VirtualFile root = repository.getRoot();


        VcsRevisionNumber beforeRevisionNumber = myHeadRevisions.get(root);
        if (beforeRevisionNumber == null) {
          beforeRevisionNumber = GitChangesCollector.getHead(repository);
          myHeadRevisions.put(root, beforeRevisionNumber);
        }

        Change change = new Change(GitContentRevision.createRevision(filePath, beforeRevisionNumber, myProject),
                                   GitContentRevision.createRevision(filePath, null, myProject), FileStatus.MODIFIED);

        LOG.debug("process in-memory change ", change);
        builder.processChange(change, gitKey);
      }
    }
  }

  @Override
  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  public boolean isRefreshInProgress() {
    return isRefreshInProgress;
  }

  public interface ChangeProviderListener {
    void repositoryUpdated(@NotNull GitRepository repository);
    default void progressStarted() {}
    default void progressStopped() {}
  }
}
