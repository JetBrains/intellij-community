// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.status;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.repo.GitConflictsHolder;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Git repository change provider
 */
public final class GitChangeProvider implements ChangeProvider {
  static final Logger LOG = Logger.getInstance("#GitStatus");

  @NotNull private final Project project;

  public GitChangeProvider(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public void getChanges(@NotNull VcsDirtyScope dirtyScope,
                         @NotNull final ChangelistBuilder builder,
                         @NotNull final ProgressIndicator progress,
                         @NotNull final ChangeListManagerGate addGate) throws VcsException {
    final GitVcs vcs = GitVcs.getInstance(project);
    GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
    LOG.debug("initial dirty scope: ", dirtyScope);
    appendNestedVcsRootsToDirt(dirtyScope, vcs, ProjectLevelVcsManager.getInstance(project));
    LOG.debug("after adding nested vcs roots to dirt: ", dirtyScope);

    final Collection<VirtualFile> affected = dirtyScope.getAffectedContentRoots();
    Set<GitRepository> repos = ContainerUtil.map2SetNotNull(affected, repositoryManager::getRepositoryForRoot);

    List<FilePath> newDirtyPaths = new ArrayList<>();

    try {
      final NonChangedHolder holder = new NonChangedHolder(project, addGate);

      Map<VirtualFile, List<FilePath>> dirtyPaths =
        GitChangesCollector.collectDirtyPaths(vcs, dirtyScope, ChangeListManager.getInstance(project),
                                              ProjectLevelVcsManager.getInstance(project));

      for (GitRepository repo : repos) {
        LOG.debug("checking root: ", repo.getRoot());
        List<FilePath> rootDirtyPaths = ContainerUtil.notNullize(dirtyPaths.get(repo.getRoot()));
        GitChangesCollector collector = GitChangesCollector.collect(project, Git.getInstance(), repo, rootDirtyPaths);

        final Collection<Change> changes = collector.getChanges();
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

        GitConflictsHolder conflictsHolder = repo.getConflictsHolder();
        conflictsHolder.refresh(dirtyScope, collector.getConflicts());
      }
      holder.feedBuilder(dirtyScope, builder);

      VcsDirtyScopeManager.getInstance(project).filePathsDirty(newDirtyPaths, null);
    }
    catch (ProcessCanceledException pce) {
      if(pce.getCause() != null) throw new VcsException(pce.getCause().getMessage(), pce.getCause());
      else throw new VcsException("Cannot get changes from Git", pce);
    }
    catch (VcsException e) {
      LOG.info(e);
      throw e;
    }
  }

  private static void appendNestedVcsRootsToDirt(final VcsDirtyScope dirtyScope, GitVcs vcs, final ProjectLevelVcsManager vcsManager) {
    final Set<FilePath> recursivelyDirtyDirectories = dirtyScope.getRecursivelyDirtyDirectories();
    if (recursivelyDirtyDirectories.isEmpty()) {
      return;
    }

    VirtualFile[] rootsUnderGit = vcsManager.getRootsUnderVcs(vcs);

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
          ((VcsModifiableDirtyScope)dirtyScope).addDirtyDirRecursively(VcsUtil.getFilePath(root));
          break;
        }
      }
    }
  }

  private static class NonChangedHolder {
    private final Project myProject;
    private final ChangeListManagerGate myAddGate;

    private final Set<FilePath> myProcessedPaths = new HashSet<>();

    private NonChangedHolder(Project project, ChangeListManagerGate addGate) {
      myProject = project;
      myAddGate = addGate;
    }

    public void markPathProcessed(@NotNull FilePath path) {
      myProcessedPaths.add(path);
    }

    public void feedBuilder(@NotNull VcsDirtyScope dirtyScope, @NotNull ChangelistBuilder builder) throws VcsException {
      final VcsKey gitKey = GitVcs.getKey();

      Map<VirtualFile, GitRevisionNumber> baseRevisions = new HashMap<>();

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


        GitRevisionNumber beforeRevisionNumber = baseRevisions.get(root);
        if (beforeRevisionNumber == null) {
          beforeRevisionNumber = GitChangeUtils.resolveReference(myProject, root, "HEAD");
          baseRevisions.put(root, beforeRevisionNumber);
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
}
