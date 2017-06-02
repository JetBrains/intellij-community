/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.impl.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class VcsLogCreatePatchActionProvider implements AnActionExtensionProvider {
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsLogDataKeys.VCS_LOG_UI) != null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    e.getPresentation().setEnabled(changes != null && changes.length > 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUtil.triggerUsage(e);

    Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES);
    String commitMessage = e.getData(VcsDataKeys.PRESET_COMMIT_MESSAGE);
    CreatePatchFromChangesAction.createPatch(e.getProject(), commitMessage, Arrays.asList(changes));
  }
}
