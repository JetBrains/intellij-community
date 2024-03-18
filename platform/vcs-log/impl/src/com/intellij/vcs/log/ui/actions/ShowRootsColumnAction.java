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
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;

public class ShowRootsColumnAction extends BooleanPropertyToggleAction {

  public ShowRootsColumnAction() {
    super(VcsLogBundle.messagePointer("vcs.log.action.show.root.names"));
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return CommonUiProperties.SHOW_ROOT_NAMES;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    VcsLogGraphTable table = e.getData(VcsLogInternalDataKeys.VCS_LOG_GRAPH_TABLE);
    if (table == null || !table.getColorManager().hasMultiplePaths()) e.getPresentation().setEnabledAndVisible(false);
  }
}
