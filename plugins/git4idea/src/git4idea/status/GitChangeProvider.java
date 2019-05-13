// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.config.GitVersionSpecialty;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * Git repository change provider
 */
public class GitChangeProvider implements ChangeProvider {

  private static final Logger LOG = Logger.getInstance("#GitStatus");

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final FileDocumentManager myFileDocumentManager;
  @NotNull private final ProjectLevelVcsManager myVcsManager;

  public GitChangeProvider(@NotNull Project project,
                           @NotNull Git git,
                           @NotNull ChangeListManager changeListManager,
                           @NotNull FileDocumentManager fileDocumentManager,
                           @NotNull ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myGit = git;
    myChangeListManager = changeListManager;
    myFileDocumentManager = fileDocumentManager;
    myVcsManager = vcsManager;
  }

  @Override
  public void getChanges(@NotNull VcsDirtyScope dirtyScope,
                         @NotNull final ChangelistBuilder builder,
                         @NotNull final ProgressIndicator progress,
                         @NotNull final ChangeListManagerGate addGate) throws VcsException {
    final GitVcs vcs = GitVcs.getInstance(myProject);
    if (LOG.isDebugEnabled()) LOG.debug("initial dirty scope: " + dirtyScope);
    appendNestedVcsRootsToDirt(dirtyScope, vcs, myVcsManager);
    if (LOG.isDebugEnabled()) LOG.debug("after adding nested vcs roots to dirt: " + dirtyScope);

    final Collection<VirtualFile> affected = dirtyScope.getAffectedContentRoots();
    Collection<VirtualFile> roots = GitUtil.gitRootsForPaths(affected);

    List<FilePath> newDirtyPaths = new ArrayList<>();

    try {
      final MyNonChangedHolder holder = new MyNonChangedHolder(myProject, addGate,
                                                               myFileDocumentManager, myVcsManager);
      for (VirtualFile root : roots) {
        LOG.debug("checking root: " + root.getPath());
        GitChangesCollector collector = isNewGitChangeProviderAvailable()
                                        ? GitNewChangesCollector.collect(myProject, myGit, myChangeListManager, myVcsManager,
                                                                         vcs, dirtyScope, root)
                                        : GitOldChangesCollector.collect(myProject, myChangeListManager, myVcsManager,
                                                                         vcs, dirtyScope, root);
        final Collection<Change> changes = collector.getChanges();
        holder.changed(changes);
        for (Change file : changes) {
          LOG.debug("process change: " + ChangesUtil.getFilePath(file).getPath());
          builder.processChange(file, GitVcs.getKey());

          if (file.isMoved() || file.isRenamed()) {
            FilePath beforePath = assertNotNull(ChangesUtil.getBeforePath(file));
            FilePath afterPath = assertNotNull(ChangesUtil.getAfterPath(file));

            if (dirtyScope.belongsTo(beforePath) != dirtyScope.belongsTo(afterPath)) {
              newDirtyPaths.add(beforePath);
              newDirtyPaths.add(afterPath);
            }
          }
        }
        for (VirtualFile f : collector.getUnversionedFiles()) {
          builder.processUnversionedFile(f);
          holder.unversioned(f);
        }
      }
      holder.feedBuilder(builder);

      VcsDirtyScopeManager.getInstance(myProject).filePathsDirty(newDirtyPaths, null);
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

  private boolean isNewGitChangeProviderAvailable() {
    return GitVersionSpecialty.KNOWS_STATUS_PORCELAIN.existsIn(myProject);
  }

  private static class MyNonChangedHolder {
    private final Project myProject;
    private final Set<FilePath> myProcessedPaths;
    private final ChangeListManagerGate myAddGate;
    private final FileDocumentManager myFileDocumentManager;
    private final ProjectLevelVcsManager myVcsManager;

    private MyNonChangedHolder(final Project project,
                               final ChangeListManagerGate addGate,
                               FileDocumentManager fileDocumentManager, ProjectLevelVcsManager vcsManager) {
      myProject = project;
      myProcessedPaths = new HashSet<>();
      myAddGate = addGate;
      myFileDocumentManager = fileDocumentManager;
      myVcsManager = vcsManager;
    }

    public void changed(final Collection<Change> changes) {
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

    public void unversioned(final VirtualFile vf) {
      // NB: There was an exception that happened several times: vf == null.
      // Populating myUnversioned in the ChangeCollector makes nulls not possible in myUnversioned,
      // so proposing that the exception was fixed.
      // More detailed analysis will be needed in case the exception appears again. 2010-12-09.
      myProcessedPaths.add(VcsUtil.getFilePath(vf));
    }

    public void feedBuilder(final ChangelistBuilder builder) throws VcsException {
      final VcsKey gitKey = GitVcs.getKey();

      Map<VirtualFile, GitRevisionNumber> baseRevisions = new HashMap<>();

      for (Document document : myFileDocumentManager.getUnsavedDocuments()) {
        VirtualFile vf = myFileDocumentManager.getFile(document);
        if (vf == null || !vf.isValid()) continue;
        if (myAddGate.getStatus(vf) != null || !myFileDocumentManager.isFileModified(vf)) continue;

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

        Change change = new Change(GitContentRevision.createRevision(vf, beforeRevisionNumber, myProject),
                                   GitContentRevision.createRevision(vf, null, myProject), FileStatus.MODIFIED);

        LOG.debug("process in-memory change " + change);
        builder.processChange(change, gitKey);
      }
    }
  }

  @Override
  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  @Override
  public void doCleanup(final List<VirtualFile> files) {
  }
}
