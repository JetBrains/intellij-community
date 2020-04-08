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
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.util.BekUtil;
import com.intellij.vcs.log.util.GraphSortPresentationUtil;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

public class IntelliSortChooserToggleAction extends ToggleAction implements DumbAware {

  public IntelliSortChooserToggleAction() {
    //noinspection DialogTitleCapitalization
    super(VcsLogBundle.message("vcs.log.action.intellisort.text"),
          VcsLogBundle.message("vcs.log.action.intellisort.description"),
          VcsLogIcons.IntelliSort);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    return properties != null &&
           properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE) &&
           !properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE).equals(PermanentGraph.SortType.Normal);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    if (properties != null && properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE)) {
      PermanentGraph.SortType bekSortType = state ? PermanentGraph.SortType.Bek : PermanentGraph.SortType.Normal;
      properties.set(MainVcsLogUiProperties.BEK_SORT_TYPE, bekSortType);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    VcsLogUi logUI = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);
    e.getPresentation().setVisible(BekUtil.isBekEnabled());
    e.getPresentation().setEnabled(BekUtil.isBekEnabled() && logUI != null);

    if (properties != null && properties.exists(MainVcsLogUiProperties.BEK_SORT_TYPE)) {
      String description;
      if (properties.get(MainVcsLogUiProperties.BEK_SORT_TYPE) == PermanentGraph.SortType.Normal) {
        String localizedDescription = GraphSortPresentationUtil.getLocalizedDescription(PermanentGraph.SortType.Bek);
        description = VcsLogBundle.message("vcs.log.action.turn.intellisort.on", StringUtil.toLowerCase(localizedDescription));
      }
      else {
        String localizedDescription = GraphSortPresentationUtil.getLocalizedDescription(PermanentGraph.SortType.Normal);
        description = VcsLogBundle.message("vcs.log.action.turn.intellisort.off", StringUtil.toLowerCase(localizedDescription));
      }
      e.getPresentation().setDescription(description);
      e.getPresentation().setText(description);
    }
    else {
      e.getPresentation().setText(VcsLogBundle.message("vcs.log.action.intellisort.text"));
      //noinspection DialogTitleCapitalization
      e.getPresentation().setDescription(VcsLogBundle.message("vcs.log.action.intellisort.description"));
    }
  }
}
