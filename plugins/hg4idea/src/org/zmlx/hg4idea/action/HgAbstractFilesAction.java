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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.TransactionRunnable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

abstract class HgAbstractFilesAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static final Logger LOG = Logger.getInstance(HgAbstractGlobalAction.class.getName());

  protected abstract boolean isEnabled(Project project, HgVcs vcs, VirtualFile file);

  protected abstract void batchPerform(Project project, final HgVcs activeVcs,
    List<VirtualFile> files, DataContext context);

  @Override
  public final void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (project == null || files == null || files.length == 0) {
      return;
    }

    project.save();

    final HgVcs vcs = HgVcs.getInstance(project);
    if ((vcs == null) || !ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, files)) {
      return;
    }

    final AbstractVcsHelper helper = AbstractVcsHelper.getInstance(project);
    List<VcsException> exceptions = helper.runTransactionRunnable(vcs, new TransactionRunnable() {
      @Override
      public void run(List<VcsException> exceptions) {
        try {
          execute(project, vcs, files, dataContext);
        } catch (VcsException ex) {
          exceptions.add(ex);
        }
      }
    }, null);

    helper.showErrors(exceptions, vcs.getDisplayName());
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (files == null || files.length == 0) {
      presentation.setEnabled(false);
      return;
    }

    final HgVcs vcs = HgVcs.getInstance(project);
    if ((vcs == null)) {
      presentation.setEnabled(false);
      return;
    }

    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(vcs, files)) {
      presentation.setEnabled(false);
      return;
    }

    boolean enabled = false;
    for (VirtualFile file : files) {
      boolean fileEnabled = isEnabled(project, vcs, file);
      if (fileEnabled) {
        enabled = true;
        break;
      }
    }

    presentation.setEnabled(enabled);
  }

  private void execute(Project project, final HgVcs activeVcs,
    final VirtualFile[] files, DataContext context) throws VcsException {

    List<VirtualFile> enabledFiles = new LinkedList<>();
    for (VirtualFile file : files) {
      if (isEnabled(project, activeVcs, file)) {
        enabledFiles.add(file);
      }
    }

    batchPerform(project, activeVcs, enabledFiles, context);

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (VirtualFile file : files) {
        file.refresh(false, true);
      }
    });

    try {
      for (VirtualFile file : enabledFiles) {
          HgUtil.markFileDirty(project, file);
      }
    } catch (InvocationTargetException | InterruptedException e) {
      LOG.info("Exception while marking files dirty", e);

    }
  }

}
