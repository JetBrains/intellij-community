// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

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

  Banner(Action action) {
    myProjectIcon.setMinimumSize(new Dimension(0, 0));
    myProjectIcon.setIcon(AllIcons.General.ProjectConfigurable);
    myProjectIcon.setForeground(UIUtil.getContextHelpForeground());
    myProjectIcon.setVisible(false);
    myLeftPanel.add(myBreadcrumbs, 0);
    add(BorderLayout.CENTER, myProjectIcon);
    add(BorderLayout.EAST, RelativeFont.BOLD.install(new ActionLink(action)));
  }

  void setText(@NotNull Collection<String> names) {
    List<Crumb> crumbs = new ArrayList<>();
    if (!names.isEmpty()) {
      List<Action> actions = CopySettingsPathAction.createSwingActions(() -> names);
      for (String name : names) {
        crumbs.add(new Crumb.Impl(null, name, null, actions));
      }
    }
    myBreadcrumbs.setCrumbs(crumbs);
  }

  void setProject(Project project) {
    if (project == null) {
      myProjectIcon.setVisible(false);
    }
    else {
      myProjectIcon.setVisible(true);
      myProjectIcon.setText(project.isDefault()
                            ? IdeUICustomization.getInstance().projectMessage("configurable.default.project.tooltip")
                            : IdeUICustomization.getInstance().projectMessage("configurable.current.project.tooltip"));
    }
  }

  @Override
  void setCenterComponent(Component component) {
    boolean addProjectIcon = myCenterComponent != null && component == null;
    super.setCenterComponent(component);

    if (addProjectIcon) {
      getLayout().addLayoutComponent(BorderLayout.CENTER, myProjectIcon);
    }
  }

  @Override
  void setLeftComponent(Component component) {
    super.setLeftComponent(component);
    myBreadcrumbs.setVisible(component == null);
  }

  @Override
  void updateProgressBorder() {
  }

  @Override
  Component getBaselineTemplate() {
    return myBreadcrumbs;
  }
}
