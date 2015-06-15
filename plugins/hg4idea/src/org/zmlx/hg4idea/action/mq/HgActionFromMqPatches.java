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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgMqUnAppliedPatchesPanel;

import java.util.List;

public abstract class HgActionFromMqPatches extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    final HgMqUnAppliedPatchesPanel patchInfo = e.getRequiredData(HgMqUnAppliedPatchesPanel.MQ_PATCHES);
    final List<String> names = patchInfo.getSelectedPatchNames();
    final HgRepository repository = patchInfo.getRepository();
    Runnable task = new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().getProgressIndicator().setText(getTitle());
        execute(repository, names);
      }
    };
    patchInfo.updatePatchSeriesInBackground(task);
  }

  @Override
  public void update(AnActionEvent e) {
    HgMqUnAppliedPatchesPanel patchInfo = e.getData(HgMqUnAppliedPatchesPanel.MQ_PATCHES);
    e.getPresentation().setEnabled(patchInfo != null && patchInfo.getSelectedRowsCount() != 0 && isEnabled(patchInfo.getRepository()));
  }

  protected boolean isEnabled(@NotNull HgRepository repository) {
    return true;        //todo should be improved, param not needed
  }

  protected abstract void execute(@NotNull HgRepository repository, @NotNull List<String> patchNames);

  @NotNull
  protected abstract String getTitle();
}
