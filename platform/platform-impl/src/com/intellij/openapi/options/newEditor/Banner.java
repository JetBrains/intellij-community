/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.project.Project;
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
    setComponentPopupMenuTo(myBreadcrumbs);
  }

  void setText(Collection<String> names) {
    ArrayList<Crumb> crumbs = new ArrayList<>();
    if (names != null) {
      for (String name : names) {
        crumbs.add(new Crumb.Impl(null, name, null));
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
      myProjectIcon.setText(OptionsBundle.message(project.isDefault()
                                                  ? "configurable.default.project.tooltip"
                                                  : "configurable.current.project.tooltip"));
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

  private static void setComponentPopupMenuTo(Breadcrumbs breadcrumbs) {
    breadcrumbs.setComponentPopupMenu(new JPopupMenu() {
      @Override
      public void show(Component invoker, int x, int y) {
        if (invoker != breadcrumbs) return;
        super.show(invoker, x, invoker.getHeight());
      }

      {
        add(new CopyAction(() -> CopyAction.createTransferable(breadcrumbs.getCrumbs())));
      }
    });
  }
}
