// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.newEditor.settings.SettingsEditorAdvancedSettings;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class Banner extends SimpleBanner {
  private final JLabel myProjectIcon = new JLabel();
  private final Breadcrumbs myBreadcrumbs = new Breadcrumbs() {
    @Override
    protected int getFontStyle(Crumb crumb) {
      return Font.BOLD;
    }
  };
  private final JLabel savedLabel;

  Banner(Action action, String savedText) {
    myProjectIcon.setMinimumSize(new Dimension(0, 0));
    myProjectIcon.setIcon(AllIcons.General.ProjectConfigurable);
    myProjectIcon.setForeground(UIUtil.getContextHelpForeground());
    showProject(false);
    myLeftPanel.removeAll();
    myLeftPanel.add(myBreadcrumbs);
    myLeftPanel.add(myProjectIcon);
    myLeftPanel.add(myProgress);
    savedLabel = new JLabel(savedText);
    savedLabel.setVisible(false);
    if (SettingsEditorAdvancedSettings.INSTANCE.getInstantSettingsApply()) {
      add(savedLabel, BorderLayout.EAST);
    } else {
      add(new ActionLink(action), BorderLayout.EAST);
    }
  }

  void setSavedTextVisible(boolean visible) {
    savedLabel.setVisible(visible);
  }

  void setText(@NotNull Collection<@NlsContexts.ConfigurableName String> names) {
    List<Crumb> crumbs = new ArrayList<>();
    if (!names.isEmpty()) {
      List<Action> actions = CopySettingsPathAction.createSwingActions(() -> names);
      for (@NlsContexts.ConfigurableName String name : names) {
        crumbs.add(new Crumb.Impl(null, name, null, actions));
      }
    }
    myBreadcrumbs.setCrumbs(crumbs);
  }

  void setProjectText(@Nullable @Nls String projectText) {
    boolean visible = projectText != null;
    showProject(visible);
    if (visible) {
      myProjectIcon.setToolTipText(projectText);
    }
  }

  void showProject(boolean hasProject) {
    myProjectIcon.setVisible(hasProject);
  }

  @Override
  void setLeftComponent(Component component) {
    super.setLeftComponent(component);
    myBreadcrumbs.setVisible(component == null);
  }

  @Override
  Component getBaselineTemplate() {
    return myBreadcrumbs;
  }
}
