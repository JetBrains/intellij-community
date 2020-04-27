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
    if (LOG.isDebugEnabled()) LOG.debug("initial dirty scope: " + dirtyScope);
    appendNestedVcsRootsToDirt(dirtyScope, vcs, ProjectLevelVcsManager.getInstance(project));
    if (LOG.isDebugEnabled()) LOG.debug("after adding nested vcs roots to dirt: " + dirtyScope);

    final Collection<VirtualFile> affected = dirtyScope.getAffectedContentRoots();
    Set<GitRepository> repos = ContainerUtil.map2SetNotNull(affected, repositoryManager::getRepositoryForRoot);

    List<FilePath> newDirtyPaths = new ArrayList<>();

    try {
      final MyNonChangedHolder holder = new MyNonChangedHolder(project, addGate);

      Map<VirtualFile, List<FilePath>> dirtyPaths =
        GitChangesCollector.collectDirtyPaths(vcs, dirtyScope, ChangeListManager.getInstance(project),
                                              ProjectLevelVcsManager.getInstance(project));

      for (GitRepository repo : repos) {
        LOG.debug("checking root: " + repo.getRoot().getPath());
        List<FilePath> rootDirtyPaths = ContainerUtil.notNullize(dirtyPaths.get(repo.getRoot()));
        GitChangesCollector collector = GitChangesCollector.collect(project, Git.getInstance(), repo, rootDirtyPaths);
        final Collection<Change> changes = collector.getChanges();
        holder.changed(changes);
        for (Change file : changes) {
          LOG.debug("process change: " + ChangesUtil.getFilePath(file).getPath());
          builder.processChange(file, GitVcs.getKey());

          if (file.isMoved() || file.isRenamed()) {
            FilePath beforePath = Objects.requireNonNull(ChangesUtil.getBeforePath(file));
            FilePath afterPath = Objects.requireNonNull(ChangesUtil.getAfterPath(file));

            if (dirtyScope.belongsTo(beforePath) != dirtyScope.belongsTo(afterPath)) {
              newDirtyPaths.add(beforePath);
              newDirtyPaths.add(afterPath);
            }
          }
        }
        for (FilePath path : collector.getUnversionedFilePaths()) {
          builder.processUnversionedFile(path);
          holder.unversioned(path);
        }

        GitConflictsHolder conflictsHolder = repo.getConflictsHolder();
        conflictsHolder.refresh(dirtyScope, collector.getConflicts());
      }
      holder.feedBuilder(builder);

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
          LOG.debug("adding git root for check. root: " + root.getPath() + ", dir: " + dirtyDir.getPath());
          ((VcsModifiableDirtyScope)dirtyScope).addDirtyDirRecursively(VcsUtil.getFilePath(root));
          break;
        }
      }
    }
  }

  private static class MyNonChangedHolder {
    private final Project myProject;
    private final Set<FilePath> myProcessedPaths;
    private final ChangeListManagerGate myAddGate;

    private MyNonChangedHolder(Project project, ChangeListManagerGate addGate) {
      myProject = project;
      myProcessedPaths = new HashSet<>();
      myAddGate = addGate;
    }

    public void changed(final Collection<? extends Change> changes) {
      for (Change change : changes) {
        final FilePath beforePath = ChangesUtil.getBeforePath(change);
        if (beforePath != null) {
          myProcessedPaths.add(beforePath);
        }
        final FilePath afterPath = ChangesUtil.getAfterPath(change);
        if (afterPath != null) {
          myProcessedPaths.add(afterPath);
        }
      }
    }

    public void unversioned(FilePath path) {
      // NB: There was an exception that happened several times: path == null.
      // Populating myUnversioned in the ChangeCollector makes nulls not possible in myUnversioned,
      // so proposing that the exception was fixed.
      // More detailed analysis will be needed in case the exception appears again. 2010-12-09.
      myProcessedPaths.add(path);
    }

    public void feedBuilder(final ChangelistBuilder builder) throws VcsException {
      final VcsKey gitKey = GitVcs.getKey();

      Map<VirtualFile, GitRevisionNumber> baseRevisions = new HashMap<>();

      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      for (Document document : fileDocumentManager.getUnsavedDocuments()) {
        VirtualFile vf = fileDocumentManager.getFile(document);
        if (vf == null || !vf.isValid()) continue;
        if (myAddGate.getStatus(vf) != null || !fileDocumentManager.isFileModified(vf)) continue;

        FilePath filePath = VcsUtil.getFilePath(vf);
        if (myProcessedPaths.contains(filePath)) continue;

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

        LOG.debug("process in-memory change " + change);
        builder.processChange(change, gitKey);
      }
    }
  }

  @Override
  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }
}
