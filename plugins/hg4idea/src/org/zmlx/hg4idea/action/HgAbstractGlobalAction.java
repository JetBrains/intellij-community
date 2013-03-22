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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

abstract class HgAbstractGlobalAction extends AnAction {
  protected HgAbstractGlobalAction(Icon icon) {
    super(icon);
  }

  protected HgAbstractGlobalAction() {
  }

  private static final Logger LOG = Logger.getInstance(HgAbstractGlobalAction.class.getName());

  public void actionPerformed(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    VirtualFile file = event.getData(PlatformDataKeys.VIRTUAL_FILE);
    VirtualFile repo = file != null ? HgUtil.getHgRootOrNull(project, file) : null;
    execute(project, HgUtil.getHgRepositories(project), repo);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
    }
  }

  protected abstract void execute(Project project, Collection<VirtualFile> repositories, @Nullable VirtualFile selectedRepo);

  protected static void handleException(Project project, Exception e) {
    LOG.info(e);
    new HgCommandResultNotifier(project).notifyError(null, "Error", e.getMessage());
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

}
