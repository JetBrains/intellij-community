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
package git4idea.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Git repository change provider
 */
public class GitChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#git4idea.changes.GitChangeProvider");
  /**
   * the project
   */
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project a project
   */
  public GitChangeProvider(@NotNull Project project) {
    myProject = project;
  }

  /**
   * {@inheritDoc}
   */
  public void getChanges(final VcsDirtyScope dirtyScope,
                         final ChangelistBuilder builder,
                         final ProgressIndicator progress,
                         final ChangeListManagerGate addGate) throws VcsException {
    final Collection<VirtualFile> affected = dirtyScope.getAffectedContentRootsWithCheck();
    if (dirtyScope.getAffectedContentRoots().size() != affected.size()) {
      final Set<VirtualFile> set = new HashSet<VirtualFile>(affected);
      set.removeAll(dirtyScope.getAffectedContentRoots());
      for (VirtualFile file : set) {
        ((VcsAppendableDirtyScope) dirtyScope).addDirtyDirRecursively(new FilePathImpl(file));
      }
    }
    Collection<VirtualFile> roots = GitUtil.gitRootsForPaths(affected);

    final MyNonChangedHolder holder = new MyNonChangedHolder(myProject, dirtyScope.getDirtyFilesNoExpand(), addGate);

    for (VirtualFile root : roots) {
      ChangeCollector c = new ChangeCollector(myProject, dirtyScope, root);
      final Collection<Change> changes = c.changes();
      holder.changed(changes);
      for (Change file : changes) {
        builder.processChange(file, GitVcs.getKey());
      }
      for (VirtualFile f : c.unversioned()) {
        builder.processUnversionedFile(f);
        holder.unversioned(f);
      }
      holder.feedBuilder(builder);
    }
  }

  private static class MyNonChangedHolder {
    private final Project myProject;
    private final Set<FilePath> myDirty;
    private final ChangeListManagerGate myAddGate;

    private MyNonChangedHolder(final Project project, final Set<FilePath> dirty, final ChangeListManagerGate addGate) {
      myProject = project;
      myDirty = dirty;
      myAddGate = addGate;
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
      myDirty.remove(new FilePathImpl(vf));
    }

    public void feedBuilder(final ChangelistBuilder builder) throws VcsException {
      final VcsKey gitKey = GitVcs.getKey();
      final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
      final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();

      for (FilePath filePath : myDirty) {
        final VirtualFile vf = filePath.getVirtualFile();
        if (vf != null) {
          if ((! FileStatus.ADDED.equals(myAddGate.getStatus(vf))) && fileDocumentManager.isFileModifiedAndDocumentUnsaved(vf)) {
            final VirtualFile root = vcsManager.getVcsRootFor(vf);
            if (root != null) {
              final GitRevisionNumber beforeRevisionNumber = GitChangeUtils.loadRevision(myProject, root, "HEAD");
              builder.processChange(new Change(GitContentRevision.createRevision(vf, beforeRevisionNumber, myProject),
                                               GitContentRevision.createRevision(vf, null, myProject), FileStatus.MODIFIED), gitKey);
            }
          }
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void doCleanup(final List<VirtualFile> files) {
  }
}
