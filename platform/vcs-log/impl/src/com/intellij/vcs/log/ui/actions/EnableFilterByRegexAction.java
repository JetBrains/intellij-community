/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import org.jetbrains.annotations.NotNull;

public class EnableFilterByRegexAction extends ToggleAction implements DumbAware {

  @Override
  public boolean isSelected(AnActionEvent e) {
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (ui == null) return false;
    return ui.getTextFilterSettings().isFilterByRegexEnabled();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (ui != null) {
      ui.getTextFilterSettings().setFilterByRegexEnabled(state);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getData(VcsLogDataKeys.VCS_LOG_UI) != null);

    super.update(e);
  }
}
