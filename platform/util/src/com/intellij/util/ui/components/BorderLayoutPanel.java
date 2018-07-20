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
package com.intellij.util.ui.components;

import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;
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
    super(new BorderLayout(JBUI.scale(hgap), JBUI.scale(vgap)));
  }

  @NotNull
  public BorderLayoutPanel addToCenter(@NotNull Component comp) {
    add(comp, BorderLayout.CENTER);
    return this;
  }

  @NotNull
  public BorderLayoutPanel addToRight(@NotNull Component comp) {
    add(comp, BorderLayout.EAST);
    return this;
  }

  @NotNull
  public BorderLayoutPanel addToLeft(@NotNull Component comp) {
    add(comp, BorderLayout.WEST);
    return this;
  }

  @NotNull
  public BorderLayoutPanel addToTop(@NotNull Component comp) {
    add(comp, BorderLayout.NORTH);
    return this;
  }

  @NotNull
  public BorderLayoutPanel addToBottom(@NotNull Component comp) {
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
    public MyAccessibleContextDelegate(AccessibleContext context) {super(context);}

    /**
     * The parent should be the Swing parent of this panel, not the parent of the wrapped context,
     * because the parent of the wrapped context is ourselves, i.e. this would result in
     * an infinite accessible parent chain.
     */
    @Override
    public Accessible getAccessibleParent() {
      if (accessibleParent != null) {
        return accessibleParent;
      }
      else {
        Container parent = getParent();
        if (parent instanceof Accessible) {
          return (Accessible)parent;
        }
      }
      return null;
    }
  }
}
