/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ContentComboLabel extends BaseLabel {

  private final ComboIcon myComboIcon = new ComboIcon() {
    @Override
    public Rectangle getIconRec() {
      return new Rectangle(getWidth() - getIconWidth() - 3, 0, getIconWidth(), getHeight());
    }

    @Override
    public boolean isActive() {
      return myUi.myWindow.isActive();
    }
  };
  private final ComboContentLayout myLayout;

  public ContentComboLabel(ComboContentLayout layout) {
    super(layout.myUi, true);
    myLayout = layout;
    addMouseListener(new MouseAdapter(){});
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);

    if (UIUtil.isActionClick(e)) {
      myUi.toggleContentPopup();
    }
  }

  void update() {
    if (isToDrawCombo()) {
      setBorder(new EmptyBorder(0, 8, 0, 8));
    } else {
      setBorder(null);
    }

    updateTextAndIcon(myUi.myManager.getSelectedContent(), true);
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (isToDrawCombo()) {
      //g.translate(0, -TAB_SHIFT);
      super.paintComponent(g);
      //g.translate(0, TAB_SHIFT);
    } else {
      super.paintComponent(g);
    }
  }

  @Override
  protected boolean allowEngravement() {
    return myUi == null || myUi.myWindow.isActive();
  }

  @Override
  protected Color getActiveFg(boolean selected) {
    return super.getActiveFg(selected);
  }

  @Override
  protected Color getPassiveFg(boolean selected) {
    return super.getPassiveFg(selected);
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isToDrawCombo()) {
      return super.getPreferredSize();
    }

    int width = 0;
    for (int i = 0; i < myUi.myManager.getContentCount(); i++) {
      final Content content = myUi.myManager.getContent(i);
      assert content != null;
      String text = content.getDisplayName();
      final Icon icon = content.getUserData(ToolWindow.SHOW_CONTENT_ICON) == Boolean.TRUE ? content.getIcon() : null;
      FontMetrics metrics = getFontMetrics(getFont());
      int eachTextWidth = metrics.stringWidth(text != null ? text : "");
      int iconWidth = icon != null ? icon.getIconWidth() : 0;
      width = Math.max(eachTextWidth + iconWidth, width);
    }

    Border border = getBorder();
    if (border != null) {
      Insets insets = border.getBorderInsets(this);
      width += (insets.left + insets.right);
    }

    width += myComboIcon.getIconWidth();

    return new Dimension(width, super.getPreferredSize().height);
  }

  private boolean isToDrawCombo() {
    return myLayout.isToDrawCombo();
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);
    if (isToDrawCombo()) {
      myComboIcon.paintIcon(this, g);
      g.setColor(new Color(255, 255, 255, 100));
    }
  }

  @Override
  public Content getContent() {
    return myUi.myManager.getSelectedContent();
  }
}
