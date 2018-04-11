// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.Gray;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

abstract class ContentLayout {

  static final Color TAB_BORDER_ACTIVE_WINDOW = new Color(38, 63, 106);
  static final Color TAB_BORDER_PASSIVE_WINDOW = new Color(130, 120, 111);

  static final Color TAB_BG_ACTIVE_WND_SELECTED_FROM = Gray._111;
  static final Color TAB_BG_ACTIVE_WND_SELECTED_TO = Gray._164;
  static final Color TAB_BG_ACTIVE_WND_UNSELECTED_FROM = Gray._130;
  static final Color TAB_BG_ACTIVE_WND_UNSELECTED_TO = Gray._85;
  static final Color TAB_BG_PASSIVE_WND_FROM = new Color(152, 143, 134);
  static final Color TAB_BG_PASSIVE_WND_TO = new Color(165, 157, 149);

  static final int TAB_ARC = 2;
  static final int TAB_SHIFT = 2;

  ToolWindowContentUi myUi;
  BaseLabel myIdLabel;

  ContentLayout(ToolWindowContentUi ui) {
    myUi = ui;
  }

  public abstract void init();

  public abstract void reset();

  public abstract void layout();

  public abstract void paintComponent(Graphics g);

  public abstract void paintChildren(Graphics g);

  public abstract void update();

  public abstract void rebuild();

  public abstract int getMinimumWidth();

  public abstract void contentAdded(ContentManagerEvent event);

  public abstract void contentRemoved(ContentManagerEvent event);

  protected void updateIdLabel(BaseLabel label) {
    String title = myUi.myWindow.getStripeTitle();
    if (myUi.myManager.getContentCount() != 1 || myUi.myManager.canCloseContents()) {
      title += ":";
    }
    label.setText(title);
    label.setBorder(JBUI.Borders.empty(0, 2, 0, 8));

    if (myUi.myManager.getContentCount() == 1) {
      final String text = myUi.myManager.getContent(0).getDisplayName();
      if (text != null && text.trim().length() > 0) {
        label.setText(label.getText() + " ");
        label.setBorder(JBUI.Borders.emptyLeft(2));
      }
    }

    label.setVisible(shouldShowId());
  }

  protected void fillTabShape(Graphics2D g2d, Shape shape, boolean isSelected, Rectangle bounds) {
    if (myUi.myWindow.isActive()) {
      if (isSelected) {
        g2d.setPaint(UIUtil.getGradientPaint(bounds.x, bounds.y, TAB_BG_ACTIVE_WND_SELECTED_FROM, bounds.x, (float)bounds.getMaxY(),
                                             TAB_BG_ACTIVE_WND_SELECTED_TO));
      }
      else {
        g2d.setPaint(UIUtil.getGradientPaint(bounds.x, bounds.y, TAB_BG_ACTIVE_WND_UNSELECTED_FROM, bounds.x, (float)bounds.getMaxY(), TAB_BG_ACTIVE_WND_UNSELECTED_TO));
      }
    }
    else {
      g2d.setPaint(
        UIUtil.getGradientPaint(bounds.x, bounds.y, TAB_BG_PASSIVE_WND_FROM, bounds.x, (float)bounds.getMaxY(), TAB_BG_PASSIVE_WND_TO));
    }

    g2d.fill(shape);
  }

  public abstract void showContentPopup(ListPopup listPopup);

  public abstract RelativeRectangle getRectangleFor(Content content);

  public abstract Component getComponentFor(Content content);

  public abstract String getCloseActionName();
  public abstract String getCloseAllButThisActionName();
  public abstract String getPreviousContentActionName();
  public abstract String getNextContentActionName();

  protected boolean shouldShowId() {
    final JComponent component = myUi.myWindow.getComponent();
    return component != null && !"true".equals(component.getClientProperty(ToolWindowContentUi.HIDE_ID_LABEL));
  }

  boolean isIdVisible() {
    return myIdLabel.isVisible();
  }
}
