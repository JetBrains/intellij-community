// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
class SimpleBanner extends JPanel {
  private final AnimatedIcon.Default myAnimatedIcon = new AnimatedIcon.Default();
  private boolean myShowProgress;

  protected final JPanel myLeftPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
  protected final JLabel myProgress = new JLabel(EmptyIcon.ICON_16);
  protected Component myLeftComponent;
  protected Component myCenterComponent;

  public SimpleBanner() {
    super(new BorderLayout(10, 0));
    myLeftPanel.add(myProgress);
    add(BorderLayout.WEST, myLeftPanel);
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
  }

  boolean canShow() {
    return myLeftComponent != null || myCenterComponent != null || myShowProgress;
  }
}