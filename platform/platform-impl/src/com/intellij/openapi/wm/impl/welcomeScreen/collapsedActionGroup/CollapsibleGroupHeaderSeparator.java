// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ui.SeparatorWithText;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingUtilities.layoutCompoundLabel;

/** Component - separator with text and arrow icon which could be controlled via [setExpandedState]. */
@ApiStatus.Internal
public class CollapsibleGroupHeaderSeparator extends SeparatorWithText {

  public enum GroupHeaderSeparatorState {
    COLLAPSED,
    EXPANDED,
    NONE
  }

  private GroupHeaderSeparatorState expandedState = GroupHeaderSeparatorState.NONE;

  public void setExpandedState(GroupHeaderSeparatorState state) {
    expandedState = state;
  }

  private Icon getIcon() {
    if (expandedState == GroupHeaderSeparatorState.NONE) {
      return null;
    }
    else if (expandedState == GroupHeaderSeparatorState.EXPANDED) {
      return AllIcons.General.ArrowDown;
    }
    else {
      return AllIcons.General.ArrowRight;
    }
  }

  // ToDo. Discussable. Better to introduce getIcon() method in SeparatorWithText and remove this method.
  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(getForeground());

    Rectangle bounds = new Rectangle(getWidth(), getHeight());
    JBInsets.removeFrom(bounds, getInsets());

    String caption = getCaption();
    if (caption != null) {
      int hGap = getHgap();
      bounds.x += hGap;
      bounds.width -= hGap + hGap;

      Icon icon = getIcon();

      Rectangle iconR = new Rectangle();
      Rectangle textR = new Rectangle();
      FontMetrics fm = g.getFontMetrics();
      String label = layoutCompoundLabel(fm, caption, icon, CENTER, myAlignment, CENTER, SwingConstants.TRAILING, bounds, iconR, textR, 4);
      textR.y += fm.getAscent();
      if (caption.equals(label)) {
        int y = textR.y + (int)fm.getLineMetrics(label, g).getStrikethroughOffset();
        paintLinePart(g, bounds.x, iconR.x, -hGap, y);
        paintLinePart(g, textR.x + textR.width, bounds.x + bounds.width, hGap, y);
      }
      UISettings.setupAntialiasing(g);
      g.setColor(getTextForeground());
      g.drawString(label, textR.x, textR.y);
      if (icon != null) {
        icon.paintIcon(this, g, iconR.x, iconR.y);
      }
    }
    else {
      paintLine(g, bounds.x, bounds.y, bounds.width);
    }
  }
}