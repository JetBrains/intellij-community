package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 * 
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.commands.GitCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Git virtual file adapter
 */
public class GitVirtualFileAdaptor extends VirtualFileAdapter {
  private final Project project;
  private final GitVcs host;
  private static final Logger log = Logger.getInstance(GitVirtualFileAdaptor.class.getName());
  @NonNls private static final String GIT_DIRECTORY = ".git";

  public GitVirtualFileAdaptor(@NotNull GitVcs host, @NotNull Project project) {
    this.host = host;
    this.project = project;
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    super.propertyChanged(event);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void contentsChanged(@NotNull VirtualFileEvent event) {
    super.contentsChanged(event);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    final String TITLE = GitBundle.getString("file.adapter.add.files.title");
    final String MESSAGE = GitBundle.getString("file.adapter.add.files.message");
    if (event.isFromRefresh()) return;

    final VirtualFile file = event.getFile();

    if (isFileProcessable(file)) {
      VcsShowConfirmationOption option = host.getAddConfirmation();
      if (option.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION) {
        List<VirtualFile> files = new ArrayList<VirtualFile>();
        files.add(file);

        AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
        Collection<VirtualFile> filesToAdd = helper.selectFilesToProcess(files, TITLE, null, TITLE, MESSAGE, option);

        VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
        if (filesToAdd != null && vcsRoot != null) {
          GitCommand command = new GitCommand(project, host.getSettings(), vcsRoot);
          try {
            command.add(filesToAdd.toArray(new VirtualFile[filesToAdd.size()]));
          }
          catch (VcsException e) {
            List<VcsException> es = new ArrayList<VcsException>();
            es.add(e);
            GitVcs.getInstance(project).showErrors(es, GitBundle.getString("file.adapter.error.title"));
          }
        }
      }
    }
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    super.fileDeleted(event);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    super.fileMoved(event);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void fileCopied(@NotNull VirtualFileCopyEvent event) {
    super.fileCopied(event);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
    super.beforePropertyChange(event);    //To change body of overridden methods use File | Settings | File Templates.
  }

  @Override
  public void beforeContentsChange(@NotNull VirtualFileEvent event) {
    super.beforeContentsChange(event);
  }

  @Override
  public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
    @NonNls final String TITLE = "Delete file(s)";
    @NonNls final String MESSAGE = "Do you want to schedule the following file for deletion from Git?\n{0}";

    VirtualFile file = event.getFile();

    //  In the case of multi-vcs project configurations, we need to skip all
    //  notifications on non-owned files
    if (!VcsUtil.isFileForVcs(file, project, host)) return;

    //  Do not ask user if the files created came from the vcs per se
    //  (obviously they are not new).
    if (event.isFromRefresh()) return;

    //  Take into account only processable files.
    // TODO check if it is a correct replacement for VcsUtil.isPathUnderProject(project, file)
    if (isFileProcessable(file) && VcsUtil.isFileUnderVcs(project, file.getUrl())) {
      VcsShowConfirmationOption option = host.getDeleteConfirmation();

      //  In the case when we need to perform "Delete" vcs action right upon
      //  the file's creation, put the file into the host's cache until it
      //  will be analyzed by the ChangeProvider.
      if (option.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
        deleteFile(file);
      }
      else if (option.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION) {
        List<VirtualFile> files = new ArrayList<VirtualFile>();
        files.add(file);

        AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
        Collection<VirtualFile> filesToAdd = helper.selectFilesToProcess(files, TITLE, null, TITLE, MESSAGE, option);

        if (filesToAdd != null) {
          deleteFile(file);
        }
        else {
          deleteFile(file);
        }
      }
      else {
        deleteFile(file);
      }
    }
  }

  @Override
  public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
    super.beforeFileMovement(event);
  }

  /**
   * File is not processable if it is outside the vcs scope or it is in the
   * list of excluded project files.
   *
   * @param file The file to check.
   * @return Returns true of the file can be added.
   */
  private boolean isFileProcessable(VirtualFile file) {
    VirtualFile base = project.getBaseDir();
    return base != null && file.getPath().startsWith(base.getPath()) && !file.getName().contains(GIT_DIRECTORY);
  }

  /**
   * Delete the specified file in Git
   *
   * @param file The file to delete
   */
  private void deleteFile(@NotNull VirtualFile file) {
    VirtualFile vcsRoot = VcsUtil.getVcsRootFor(project, file);
    GitCommand command = new GitCommand(project, host.getSettings(), vcsRoot);
    VirtualFile[] files = new VirtualFile[1];
    files[0] = file;
    try {
      command.delete(files);
    }
    catch (VcsException e) {
      log.error("Unable to delete file", e);
    }
  }
}
