/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;

/**
 * @author Kirill Likhodedov
 */
public abstract class HgAction extends AnAction {
  protected HgAction() {
  }

  protected HgAction(Icon icon) {
    super(icon);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    VirtualFile repo = file != null ? HgUtil.getHgRootOrNull(project, file) : null;
    execute(project, repo);
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled = HgAbstractGlobalAction.isEnabled(e);
    e.getPresentation().setEnabled(enabled);
  }

  public abstract void execute(Project project, @Nullable VirtualFile selectedRepo);

}
