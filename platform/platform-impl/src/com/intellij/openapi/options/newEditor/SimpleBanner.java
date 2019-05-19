// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
class SimpleBanner extends JPanel {
  private final AnimatedIcon.Default myAnimatedIcon = new AnimatedIcon.Default();
  private boolean myShowProgress;

  protected final JPanel myLeftPanel;
  protected final JLabel myProgress = new JLabel(EmptyIcon.ICON_16);
  protected Component myLeftComponent;
  protected Component myCenterComponent;

  SimpleBanner() {
    super(new BorderLayout(10, 0));
    myLeftPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.CENTER, 0, 0) {
      @Override
      public Dimension preferredLayoutSize(Container target) {
        return getPreferredLeftPanelSize(super.preferredLayoutSize(target));
      }

      @Override
      public void layoutContainer(Container target) {
        super.layoutContainer(target);
        baselineLayout();
      }
    }) {
      @Override
      public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, 0, width, height + y);
      }
    };
    myLeftPanel.add(myProgress);
    add(BorderLayout.WEST, myLeftPanel);
  }

  @Override
  public void setBorder(Border border) {
    super.setBorder(border);
    if (myLeftPanel != null) {
      myLeftPanel.setBorder(border);
    }
  }

  Dimension getPreferredLeftPanelSize(Dimension size) {
    return size;
  }

  private void baselineLayout() {
    Component template = getBaselineTemplate();
    if (template == null) {
      return;
    }

    int baseline = template.getBaseline(template.getWidth(), template.getHeight());
    if (baseline == -1) {
      return;
    }

    int components = myLeftPanel.getComponentCount();
    for (int i = 0; i < components; i++) {
      Component component = myLeftPanel.getComponent(i);
      if (component == template) {
        continue;
      }

      int y;
      if (component == myProgress) {
        y = (int)(JBUI.scale(1.5f) + (template.getHeight() - component.getHeight()) / 2f);
      }
      else {
        y = baseline - component.getBaseline(component.getWidth(), component.getHeight());
      }

      component.setLocation(component.getX(), getInsets().top + y);
    }
  }

  void setLeftComponent(Component component) {
    if (myLeftComponent != null) {
      myLeftPanel.remove(myLeftComponent);
      myLeftComponent = null;
    }
    if (component != null) {
      myLeftComponent = component;
      myLeftPanel.add(component, 0);
    }
    updateProgressBorder();
  }

  void setCenterComponent(Component component) {
    if (myCenterComponent != null) {
      remove(myCenterComponent);
    }

    myCenterComponent = component;

    if (component != null) {
      add(component);
    }
  }

  void showProgress(boolean start) {
    myShowProgress = start;
    myProgress.setIcon(start ? myAnimatedIcon : EmptyIcon.ICON_16);
    updateProgressBorder();
  }

  void updateProgressBorder() {
    myProgress.setBorder(myLeftPanel.getComponentCount() == 1 ? JBUI.Borders.emptyLeft(10) : null);
  }

  boolean canShow() {
    return myLeftComponent != null || myCenterComponent != null || myShowProgress;
  }

  Component getBaselineTemplate() {
    return myCenterComponent;
  }
}