/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action.mq;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.action.HgAbstractGlobalSingleRepoAction;
import org.zmlx.hg4idea.action.HgActionUtil;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgMqUnAppliedPatchesPanel;

import java.util.Collection;

public class HgShowUnAppliedPatchesAction extends HgAbstractGlobalSingleRepoAction {
  @Override
  protected void execute(@NotNull Project project, @NotNull Collection<HgRepository> repositories, @Nullable HgRepository selectedRepo) {
    if (selectedRepo != null) {
      showUnAppliedPatches(project, selectedRepo);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    HgRepository repository = HgActionUtil.getSelectedRepositoryFromEvent(e);
    e.getPresentation().setEnabledAndVisible(repository != null);
  }

  public static void showUnAppliedPatches(@NotNull Project project, @NotNull HgRepository selectedRepo) {
    ToolWindow toolWindow = ObjectUtils.assertNotNull(ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS));
    ContentUtilEx
      .addTabbedContent(toolWindow.getContentManager(), new HgMqUnAppliedPatchesPanel(selectedRepo), "MQ", selectedRepo.getRoot().getName(),
                        true);
    toolWindow.activate(null);
  }
}
