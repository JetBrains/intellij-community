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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

abstract class HgAbstractGlobalAction extends AnAction {
  protected HgAbstractGlobalAction(Icon icon) {
    super(icon);
  }

  protected HgAbstractGlobalAction() {
  }

  private static final Logger LOG = Logger.getInstance(HgAbstractGlobalAction.class.getName());

  public void actionPerformed(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    VirtualFile repo = file != null ? HgUtil.getHgRootOrNull(project, file) : null;
    List<VirtualFile> repos = HgUtil.getHgRepositories(project);
    if (!repos.isEmpty()) {
      execute(project, repos, repo);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    boolean enabled = isEnabled(e);
    e.getPresentation().setEnabled(enabled);
  }

  protected abstract void execute(@NotNull Project project,
                                  @NotNull Collection<VirtualFile> repositories,
                                  @Nullable VirtualFile selectedRepo);

  public static void handleException(@Nullable Project project, @NotNull Exception e) {
    handleException(project, "Error", e);
  }

  public static void handleException(@Nullable Project project, @NotNull String title, @NotNull Exception e) {
    LOG.info(e);
    new HgCommandResultNotifier(project).notifyError(null, title, e.getMessage());
  }

  protected void markDirtyAndHandleErrors(Project project, VirtualFile repository) {
    try {
      HgUtil.markDirectoryDirty(project, repository);
    }
    catch (InvocationTargetException e) {
      handleException(project, e);
    }
    catch (InterruptedException e) {
      handleException(project, e);
    }
  }

  public static boolean isEnabled(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return false;
    }
    HgVcs vcs = HgVcs.getInstance(project);
    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs);
    if (roots == null || roots.length == 0) {
      return false;
    }
    return true;
  }
}
