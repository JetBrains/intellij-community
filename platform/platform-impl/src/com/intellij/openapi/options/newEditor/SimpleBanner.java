// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * @author Alexander Lobas
 */
class SimpleBanner extends JPanel {
  static final String CENTERED_HEADER_KEY = "ide.settings.header.centered";
  static final int DEFAULT_CENTER_COMPONENT_GAP = 10;

  private final AnimatedIcon.Default myAnimatedIcon = new AnimatedIcon.Default();
  private final boolean myUseCenteredLayout = RegistryManager.getInstance().get(CENTERED_HEADER_KEY).asBoolean();
  private boolean myShowProgress;

  protected final JPanel myLeftPanel;
  protected final JLabel myProgress = new JLabel(EmptyIcon.ICON_16);
  protected Component myLeftComponent;
  protected Component myCenterComponent;

  SimpleBanner() {
    super(new BorderLayout(DEFAULT_CENTER_COMPONENT_GAP, 0));
    myLeftPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.CENTER, 0, 0) {
      @Override
      public Dimension preferredLayoutSize(Container target) {
        return getPreferredLeftPanelSize(super.preferredLayoutSize(target));
      }

      @Override
      public void layoutContainer(Container target) {
        super.layoutContainer(target);
        if (myUseCenteredLayout) {
          centerComponentsVertically();
        }
        else {
          baselineLayout();
        }
      }
    });
    myLeftPanel.add(myProgress);
    add(BorderLayout.WEST, myLeftPanel);
  }

  Dimension getPreferredLeftPanelSize(Dimension size) {
    return size;
  }

  private void centerComponentsVertically() {
    int components = myLeftPanel.getComponentCount();
    for (int i = 0; i < components; i++) {
      Component component = myLeftPanel.getComponent(i);
      if (!component.isVisible()) {
        continue;
      }

      int y = (myLeftPanel.getHeight() - component.getHeight()) / 2;
      component.setLocation(component.getX(), y);
    }
  }

  private void baselineLayout() {
    Component template = getBaselineTemplate();
    if (template == null) {
      return;
    }

    // Ensure template's internal layout is up-to-date after FlowLayout resizing,
    // so that getBaseline() returns a value consistent with the current size.
    template.doLayout();

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
      if (component instanceof JLabel) {
        y = (int)(JBUIScale.scale(1.5f) + (template.getHeight() - component.getHeight()) / 2f);
      }
      else {
        y = baseline - component.getBaseline(component.getWidth(), component.getHeight());
      }

      component.setLocation(component.getX(), y);
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

  void setCenterComponentGap(int gap) {
    ((BorderLayout)getLayout()).setHgap(gap);
    revalidate();
  }

  void showProgress(boolean start) {
    myShowProgress = start;
    myProgress.setIcon(start ? myAnimatedIcon : EmptyIcon.ICON_16);
    updateProgressBorder();
  }

  void setProgressIndicatorVisible(boolean visible) {
    if (!visible) {
      showProgress(false);
    }
    myProgress.setVisible(visible);
  }

  void updateProgressBorder() {
    myProgress.setBorder(myShowProgress ? JBUI.Borders.emptyLeft(10) : null);
  }

  boolean canShow() {
    return myLeftComponent != null || myCenterComponent != null || myShowProgress;
  }

  boolean usesCenteredLayout() {
    return myUseCenteredLayout;
  }

  Component getBaselineTemplate() {
    return myCenterComponent;
  }
}
