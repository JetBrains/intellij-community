// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.JBColor;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.ui.components.labels.SwingActionLink;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Sergey.Malenkov
 */
final class Banner extends SimpleBanner {
  private final JLabel myProjectIcon = new JLabel();
  private final Breadcrumbs myBreadcrumbs = new Breadcrumbs() {
    protected int getFontStyle(Crumb crumb) {
      return Font.BOLD;
    }
  };

  Banner(Action action) {
    myProjectIcon.setMinimumSize(new Dimension(0, 0));
    myProjectIcon.setIcon(AllIcons.General.ProjectConfigurableBanner);
    myProjectIcon.setForeground(JBColor.GRAY);
    myProjectIcon.setVisible(false);
    myLeftPanel.add(myBreadcrumbs, 0);
    add(BorderLayout.CENTER, myProjectIcon);
    add(BorderLayout.EAST, RelativeFont.BOLD.install(new SwingActionLink(action)));
  }

  void setText(Collection<String> names) {
    ArrayList<Crumb> crumbs = new ArrayList<>();
    if (names != null && !names.isEmpty()) {
      CopyAction action = new CopyAction(() -> CopyAction.createTransferable(names));
      for (String name : names) {
        crumbs.add(new Crumb.Impl(null, name, null, action));
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
      String projectConceptName = IdeUICustomization.getInstance().getProjectConceptName();
      myProjectIcon.setText(project.isDefault()
                            ? OptionsBundle.message("configurable.default.project.tooltip", projectConceptName)
                            : OptionsBundle.message("configurable.current.project.tooltip", projectConceptName));
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
}
