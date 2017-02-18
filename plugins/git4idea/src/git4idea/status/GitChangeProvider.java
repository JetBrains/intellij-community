/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.Convertor;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.config.GitVersion;
import git4idea.config.GitVersionSpecialty;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
    if (vcs == null) {
      // already disposed or not yet initialized => ignoring
      return;
    }

    if (LOG.isDebugEnabled()) LOG.debug("initial dirty scope: " + dirtyScope);
    appendNestedVcsRootsToDirt(dirtyScope, vcs, myVcsManager);
    if (LOG.isDebugEnabled()) LOG.debug("after adding nested vcs roots to dirt: " + dirtyScope);

    final Collection<VirtualFile> affected = dirtyScope.getAffectedContentRoots();
    Collection<VirtualFile> roots = GitUtil.gitRootsForPaths(affected);

    try {
      final MyNonChangedHolder holder = new MyNonChangedHolder(myProject, dirtyScope.getDirtyFilesNoExpand(), addGate,
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
        }
        for (VirtualFile f : collector.getUnversionedFiles()) {
          builder.processUnversionedFile(f);
          holder.unversioned(f);
        }
        holder.feedBuilder(builder);
      }
    }
    catch (VcsException e) {
      LOG.info(e);
      // most probably the error happened because git is not configured
      vcs.getExecutableValidator().showNotificationOrThrow(e);
    }
  }

  private static void appendNestedVcsRootsToDirt(final VcsDirtyScope dirtyScope, GitVcs vcs, final ProjectLevelVcsManager vcsManager) {
    final Set<FilePath> recursivelyDirtyDirectories = dirtyScope.getRecursivelyDirtyDirectories();
    if (recursivelyDirtyDirectories.isEmpty()) {
      return;
    }

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final Set<VirtualFile> rootsUnderGit = new HashSet<>(Arrays.asList(vcsManager.getRootsUnderVcs(vcs)));
    final Set<VirtualFile> inputColl = new HashSet<>(rootsUnderGit);
    final Set<VirtualFile> existingInScope = new HashSet<>();
    for (FilePath dir : recursivelyDirtyDirectories) {
      VirtualFile vf = dir.getVirtualFile();
      if (vf == null) {
        vf = lfs.findFileByIoFile(dir.getIOFile());
      }
      if (vf == null) {
        vf = lfs.refreshAndFindFileByIoFile(dir.getIOFile());
      }
      if (vf != null) {
        existingInScope.add(vf);
      }
    }
    inputColl.addAll(existingInScope);
    if (LOG.isDebugEnabled()) LOG.debug("appendNestedVcsRoots. collection to remove ancestors: " + inputColl);
    FileUtil.removeAncestors(inputColl, new Convertor<VirtualFile, String>() {
                               @Override
                               public String convert(VirtualFile o) {
                                 return o.getPath();
                               }
                             }, new PairProcessor<VirtualFile, VirtualFile>() {
                               @Override
                               public boolean process(VirtualFile parent, VirtualFile child) {
                                 if (! existingInScope.contains(child) && existingInScope.contains(parent)) {
                                   LOG.debug("adding git root for check. child: " + child.getPath() + ", parent: " + parent.getPath());
                                   ((VcsModifiableDirtyScope)dirtyScope).addDirtyDirRecursively(VcsUtil.getFilePath(child));
                                 }
                                 return true;
                               }
                             }
    );
  }

  private boolean isNewGitChangeProviderAvailable() {
    GitVcs vcs = GitVcs.getInstance(myProject);
    if (vcs == null) {
      return false;
    }
    final GitVersion version = vcs.getVersion();
    return GitVersionSpecialty.KNOWS_STATUS_PORCELAIN.existsIn(version);
  }

  private static class MyNonChangedHolder {
    private final Project myProject;
    private final Set<FilePath> myDirty;
    private final ChangeListManagerGate myAddGate;
    private FileDocumentManager myFileDocumentManager;
    private ProjectLevelVcsManager myVcsManager;

    private MyNonChangedHolder(final Project project,
                               final Set<FilePath> dirty,
                               final ChangeListManagerGate addGate,
                               FileDocumentManager fileDocumentManager, ProjectLevelVcsManager vcsManager) {
      myProject = project;
      myDirty = dirty;
      myAddGate = addGate;
      myFileDocumentManager = fileDocumentManager;
      myVcsManager = vcsManager;
    }

    public void changed(final Collection<Change> changes) {
      for (Change change : changes) {
        final FilePath beforePath = ChangesUtil.getBeforePath(change);
        if (beforePath != null) {
          myDirty.remove(beforePath);
        }
        final FilePath afterPath = ChangesUtil.getBeforePath(change);
        if (afterPath != null) {
          myDirty.remove(afterPath);
        }
      }
    }

    public void unversioned(final VirtualFile vf) {
      // NB: There was an exception that happened several times: vf == null.
      // Populating myUnversioned in the ChangeCollector makes nulls not possible in myUnversioned,
      // so proposing that the exception was fixed.
      // More detailed analysis will be needed in case the exception appears again. 2010-12-09.
      myDirty.remove(VcsUtil.getFilePath(vf));
    }

    public void feedBuilder(final ChangelistBuilder builder) throws VcsException {
      final VcsKey gitKey = GitVcs.getKey();

      for (FilePath filePath : myDirty) {
        final VirtualFile vf = filePath.getVirtualFile();
        if (vf != null) {
          if ((myAddGate.getStatus(vf) == null) && myFileDocumentManager.isFileModified(vf)) {
            final VirtualFile root = myVcsManager.getVcsRootFor(vf);
            if (root != null) {
              final GitRevisionNumber beforeRevisionNumber = GitChangeUtils.resolveReference(myProject, root, "HEAD");
              builder.processChange(new Change(GitContentRevision.createRevision(vf, beforeRevisionNumber, myProject),
                                               GitContentRevision.createRevision(vf, null, myProject), FileStatus.MODIFIED), gitKey);
            }
          }
        }
      }
    }
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  public void doCleanup(final List<VirtualFile> files) {
  }
}
