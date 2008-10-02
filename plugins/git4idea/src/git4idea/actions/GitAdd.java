package git4idea.actions;
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
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBundle;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.GitVcsSettings;
import git4idea.commands.GitCommand;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Git "add" action
 */
public class GitAdd extends BasicAction {

  @Override
  public void perform(@NotNull Project project, GitVcs vcs, @NotNull List<VcsException> exceptions, @NotNull VirtualFile[] affectedFiles)
      throws VcsException {
    saveAll();

    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(GitVcs.getInstance(project), affectedFiles)) return;

    final Map<VirtualFile, List<VirtualFile>> roots = GitUtil.sortFilesByVcsRoot(project, affectedFiles);

    for (VirtualFile root : roots.keySet()) {
      GitCommand command = new GitCommand(project, vcs.getSettings(), root);
      List<VirtualFile> list = roots.get(root);
      VirtualFile[] vfiles = list.toArray(new VirtualFile[list.size()]);
      command.add(vfiles);
    }

    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    for (VirtualFile file : affectedFiles) {
      mgr.fileDirty(file);
      file.refresh(true, true);
    }
  }

  /**
   * Add the specified files to the project.
   *
   * @param project The project to add files to
   * @param files   The files to add
   * @throws VcsException If an error occurs
   */
  public static void addFiles(@NotNull Project project, @NotNull VirtualFile[] files) throws VcsException {
    final Map<VirtualFile, List<VirtualFile>> roots = GitUtil.sortFilesByVcsRoot(project, files);
    for (VirtualFile root : roots.keySet()) {
      GitCommand command = new GitCommand(project, GitVcsSettings.getInstance(project), root);
      List<VirtualFile> list = roots.get(root);
      VirtualFile[] vfiles = list.toArray(new VirtualFile[list.size()]);
      command.add(vfiles);
    }

    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
    for (VirtualFile file : files) {
      mgr.fileDirty(file);
      file.refresh(true, true);
    }
  }

  @Override
  @NotNull
  protected String getActionName(@NotNull AbstractVcs abstractvcs) {
    return GitBundle.getString("add.action.name");
  }

  @Override
  protected boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles) {
    for (VirtualFile file : vFiles) {
      FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
      if (fileStatus == FileStatus.NOT_CHANGED || fileStatus == FileStatus.DELETED) return false;
    }
    return true;
  }
}
