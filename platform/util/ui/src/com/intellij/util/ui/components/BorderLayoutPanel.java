// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.components;

import com.intellij.ui.components.JBPanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class BorderLayoutPanel extends JBPanel<BorderLayoutPanel> {
  private boolean myDelegateAccessibleContext;

  public BorderLayoutPanel() {
    this(0, 0);
  }

  public BorderLayoutPanel(int hgap, int vgap) {
    super(new BorderLayout(JBUIScale.scale(hgap), JBUIScale.scale(vgap)));
  }

  @NotNull
  public final BorderLayoutPanel addToCenter(@NotNull Component comp) {
    add(comp, BorderLayout.CENTER);
    return this;
  }

  @NotNull
  public final BorderLayoutPanel addToRight(@NotNull Component comp) {
    add(comp, BorderLayout.EAST);
    return this;
  }

  @NotNull
  public final BorderLayoutPanel addToLeft(@NotNull Component comp) {
    add(comp, BorderLayout.WEST);
    return this;
  }

  @NotNull
  public final BorderLayoutPanel addToTop(@NotNull Component comp) {
    add(comp, BorderLayout.NORTH);
    return this;
  }

  @NotNull
  public final BorderLayoutPanel addToBottom(@NotNull Component comp) {
    add(comp, BorderLayout.SOUTH);
    return this;
  }

  /**
   * Enables delegating the {@link AccessibleContext} implementation of this panel to the first (and only) component contained
   * in this panel.
   *
   * By delegating to the inner component, we essentially remove this panel from the Accessibility component tree.
   *
   * The reason we need this is that many screen readers don't always know how to deal with labels wrapped in panels.
   * For example, they expect items of list boxes or combo boxes to have the {@link AccessibleRole#LABEL} as well as some text.
   */
  public void setDelegateAccessibleContextToWrappedComponent(boolean delegateAccessibleContext) {
    if (delegateAccessibleContext != myDelegateAccessibleContext) {
      this.accessibleContext = null;
    }
    myDelegateAccessibleContext = delegateAccessibleContext;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (this.accessibleContext == null) {
      if (myDelegateAccessibleContext) {
        if (getComponentCount() == 1) {
          AccessibleContext context = getComponent(0).getAccessibleContext();
          if (context != null) {
            this.accessibleContext = new MyAccessibleContextDelegate(context);
          }
        }
      }
    }
    return super.getAccessibleContext();
  }

  private class MyAccessibleContextDelegate extends AccessibleContextDelegate {
    MyAccessibleContextDelegate(AccessibleContext context) {super(context);}

    @Override
    public Container getDelegateParent() {
      return getParent();
    }
  }
}
