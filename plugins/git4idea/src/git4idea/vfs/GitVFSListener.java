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
package git4idea.vfs;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsVFSListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitFileUtils;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Git virtual file adapter
 */
public class GitVFSListener extends VcsVFSListener {

  /**
   * A constructor for listener
   *
   * @param project a project
   * @param vcs     a vcs for that project
   */
  public GitVFSListener(final Project project, final GitVcs vcs) {
    super(project, vcs);
  }

  /**
   * {@inheritDoc}
   */
  protected String getAddTitle() {
    return GitBundle.getString("vfs.listener.add.title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getSingleFileAddTitle() {
    return GitBundle.getString("vfs.listener.add.single.title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getSingleFileAddPromptTemplate() {
    return GitBundle.getString("vfs.listener.add.single.prompt");
  }

  /**
   * {@inheritDoc}
   */
  protected void performAdding(final Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap) {
    final Map<VirtualFile, List<VirtualFile>> sortedFiles;
    try {
      sortedFiles = GitUtil.sortFilesByGitRoot(addedFiles, true);
    }
    catch (VcsException e) {
      gitVcs().showMessages(e.getMessage());
      return;
    }
    gitVcs().runInBackground(new Task.Backgroundable(myProject, GitBundle.getString("add.adding")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        // note that copied files are not processed because they are included into added files.
        for (Map.Entry<VirtualFile, List<VirtualFile>> e : sortedFiles.entrySet()) {
          try {
            final VirtualFile root = e.getKey();
            indicator.setText(root.getPresentableUrl());
            GitFileUtils.addFiles(myProject, root, e.getValue());
            GitUtil.markFilesDirty(myProject, e.getValue());
          }
          catch (final VcsException ex) {
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              public void run() {
                gitVcs().showMessages(ex.getMessage());
              }
            });
          }
        }
      }
    });
  }

  /**
   * @return casted vcs instance
   */
  private GitVcs gitVcs() {
    return ((GitVcs)myVcs);
  }

  /**
   * Perform adding the files using file paths
   *
   * @param addedFiles the added files
   */
  private void performAdding(Collection<FilePath> addedFiles) {
    final Map<VirtualFile, List<FilePath>> sortedFiles;
    try {
      sortedFiles = GitUtil.sortFilePathsByGitRoot(addedFiles, true);
    }
    catch (VcsException e) {
      gitVcs().showMessages(e.getMessage());
      return;
    }
    gitVcs().runInBackground(new Task.Backgroundable(myProject, GitBundle.getString("add.adding")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (Map.Entry<VirtualFile, List<FilePath>> e : sortedFiles.entrySet()) {
          try {
            final VirtualFile root = e.getKey();
            indicator.setText(root.getPresentableUrl());
            GitFileUtils.addPaths(myProject, root, e.getValue());
            GitUtil.markFilesDirty(myProject, e.getValue());
          }
          catch (final VcsException ex) {
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              public void run() {
                gitVcs().showMessages(ex.getMessage());
              }
            });
          }
        }
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  protected String getDeleteTitle() {
    return GitBundle.getString("vfs.listener.delete.title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getSingleFileDeleteTitle() {
    return GitBundle.getString("vfs.listener.delete.single.title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getSingleFileDeletePromptTemplate() {
    return GitBundle.getString("vfs.listener.delete.single.prompt");
  }

  /**
   * {@inheritDoc}
   */
  protected void performDeletion(final List<FilePath> filesToDelete) {
    final Map<VirtualFile, List<FilePath>> sortedFiles;
    try {
      sortedFiles = GitUtil.sortFilePathsByGitRoot(filesToDelete, true);
    }
    catch (VcsException e) {
      gitVcs().showMessages(e.getMessage());
      return;
    }
    gitVcs().runInBackground(new Task.Backgroundable(myProject, GitBundle.getString("remove.removing")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (Map.Entry<VirtualFile, List<FilePath>> e : sortedFiles.entrySet()) {
          try {
            final VirtualFile root = e.getKey();
            indicator.setText(root.getPresentableUrl());
            GitFileUtils.delete(myProject, root, e.getValue(), "--ignore-unmatch");
            GitUtil.markFilesDirty(myProject, e.getValue());
          }
          catch (final VcsException ex) {
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              public void run() {
                gitVcs().showMessages(ex.getMessage());
              }
            });
          }
        }
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  protected void performMoveRename(final List<MovedFileInfo> movedFiles) {
    // because git does not tracks moves, the file are just added and deleted.
    ArrayList<FilePath> added = new ArrayList<FilePath>();
    ArrayList<FilePath> removed = new ArrayList<FilePath>();
    for (MovedFileInfo m : movedFiles) {
      added.add(VcsUtil.getFilePath(m.myNewPath));
      removed.add(VcsUtil.getFilePath(m.myOldPath));
    }
    performAdding(added);
    performDeletion(removed);
  }

  /**
   * {@inheritDoc}
   */
  protected boolean isDirectoryVersioningSupported() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected Collection<FilePath> selectFilePathsToDelete(final List<FilePath> deletedFiles) {
    // For git asking about vcs delete does not make much sense. The result is practically identical.
    return deletedFiles;
  }
}