// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.status;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.util.paths.RootDirtySet;
import com.intellij.openapi.vfs.VirtualFile;
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
@Service(Service.Level.PROJECT)
public final class GitChangeProvider implements ChangeProvider {
  static final Logger LOG = Logger.getInstance("#GitStatus");
  private volatile boolean isRefreshInProgress = false;

  private final @NotNull Project project;

  public GitChangeProvider(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void getChanges(@NotNull VcsDirtyScope dirtyScope,
                         @NotNull ChangelistBuilder builder,
                         @NotNull ProgressIndicator progress,
                         @NotNull ChangeListManagerGate addGate) throws VcsException {
    BackgroundTaskUtil.syncPublisher(project, GitRefreshListener.TOPIC).progressStarted();
    isRefreshInProgress = true;
    try {
      LOG.debug("initial dirty scope: ", dirtyScope);
      GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);

      List<FilePath> newDirtyPaths = new ArrayList<>();
      NonChangedHolder holder = new NonChangedHolder(project, addGate);

      Map<VirtualFile, RootDirtySet> dirtyPaths = GitStagingAreaHolder.collectDirtyPathsPerRoot(dirtyScope);
      LOG.debug("after adding nested vcs roots to dirt: ", dirtyPaths);

      for (Map.Entry<VirtualFile, RootDirtySet> entry : dirtyPaths.entrySet()) {
        VirtualFile root = entry.getKey();
        RootDirtySet rootDirtyPaths = entry.getValue();

        LOG.debug("checking root: ", root);
        GitRepository repo = repositoryManager.getRepositoryForRoot(root);
        if (repo == null) continue;

        GitStagingAreaHolder stageAreaHolder = repo.getStagingAreaHolder();
        List<GitFileStatus> newChanges = stageAreaHolder.refresh(rootDirtyPaths);

        GitChangesCollector collector = GitChangesCollector.collect(project, repo, newChanges);
        holder.markHeadRevision(root, collector.getHead());

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

        BackgroundTaskUtil.syncPublisher(project, GitRefreshListener.TOPIC).repositoryUpdated(repo);
      }
      holder.feedBuilder(dirtyScope, builder);

      VcsDirtyScopeManager.getInstance(project).filePathsDirty(newDirtyPaths, null);
    }
    finally {
      isRefreshInProgress = false;
      BackgroundTaskUtil.syncPublisher(project, GitRefreshListener.TOPIC).progressStopped();
    }
  }

  private static final class NonChangedHolder {
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

        VirtualFile vcsFile = VcsUtil.resolveSymlinkIfNeeded(myProject, vf);
        FilePath filePath = VcsUtil.getFilePath(vcsFile);
        if (myProcessedPaths.contains(filePath)) continue;
        if (!dirtyScope.belongsTo(filePath)) continue;

        GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(vcsFile);
        if (repository == null) continue;
        if (repository.getUntrackedFilesHolder().containsUntrackedFile(filePath)) continue;
        if (repository.getUntrackedFilesHolder().containsIgnoredFile(filePath)) continue;

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
}
