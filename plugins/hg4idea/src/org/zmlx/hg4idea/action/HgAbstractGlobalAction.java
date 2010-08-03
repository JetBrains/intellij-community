// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgUtil;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgCommandException;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

abstract class HgAbstractGlobalAction extends AnAction {

  protected abstract HgGlobalCommandBuilder getHgGlobalCommandBuilder(Project project);
  private static final Logger LOG = Logger.getInstance(HgAbstractGlobalAction.class.getName());

  public void actionPerformed(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    HgGlobalCommand command = getHgGlobalCommandBuilder(project).build(findRepos(project));
    if (command == null) {
      return;
    }
    try {
      command.execute();
      HgUtil.markDirectoryDirty(project,command.getRepo());
    } catch (HgCommandException e) {
      handleException(project, e);
    } catch (InvocationTargetException e) {
      handleException(project, e);
    } catch (InterruptedException e) {
      handleException(project, e);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    HgVcs vcs = (HgVcs) ProjectLevelVcsManager.getInstance(project).findVcsByName(HgVcs.VCS_NAME);

    if (!vcs.isStarted()) {
      presentation.setEnabled(false);
    }
  }

  private List<VirtualFile> findRepos(Project project) {
    List<VirtualFile> repos = new LinkedList<VirtualFile>();
    VcsRoot[] roots = ProjectLevelVcsManager.getInstance(project).getAllVcsRoots();
    for (VcsRoot root : roots) {
      if (HgVcs.VCS_NAME.equals(root.vcs.getName())) {
        repos.add(root.path);
      }
    }
    return repos;
  }

  protected interface HgGlobalCommand {
    VirtualFile getRepo();
    void execute() throws HgCommandException;
  }

  protected interface HgGlobalCommandBuilder {
    @Nullable
    HgGlobalCommand build(Collection<VirtualFile> repos);
  }

  private static void handleException(Project project, Exception e) {
    LOG.info(e);
    VcsUtil.showErrorMessage(project, e.getMessage(), "Error");
  }

}
