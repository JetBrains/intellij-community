// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class EnableMatchCaseAction extends BooleanPropertyToggleAction {
  public EnableMatchCaseAction() {
    getTemplatePresentation().setSelectedIcon(AllIcons.Actions.MatchCaseSelected);
    getTemplatePresentation().setHoveredIcon(AllIcons.Actions.MatchCaseHovered);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    VcsLogUiProperties properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES);

    if (ui != null && properties != null && properties.exists(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE)) {
      Presentation presentation = e.getPresentation();
      boolean regexEnabled =
        properties.exists(MainVcsLogUiProperties.TEXT_FILTER_REGEX) && properties.get(MainVcsLogUiProperties.TEXT_FILTER_REGEX);
      if (!regexEnabled) {
        presentation.setText(VcsLogBundle.message("action.title.match.case"));
      }
      else {
        Collection<VcsLogProvider> providers = new LinkedHashSet<>(ui.getDataPack().getLogProviders().values());
        List<VcsLogProvider> supported = ContainerUtil.filter(providers, VcsLogProperties.CASE_INSENSITIVE_REGEX::getOrDefault);
        presentation.setVisible(true);
        presentation.setEnabled(!supported.isEmpty());
        if (providers.size() == supported.size() || supported.isEmpty()) {
          presentation.setText(VcsLogBundle.message("action.title.match.case"));
        }
        else {
          String supportedText = StringUtil.join(ContainerUtil.map(supported,
                                                                   p -> StringUtil.toLowerCase(p.getSupportedVcs().getName())), ", ");
          presentation.setText(VcsLogBundle.message("action.title.match.case.only.supported", supportedText));
        }
      }
    }
  }
}
