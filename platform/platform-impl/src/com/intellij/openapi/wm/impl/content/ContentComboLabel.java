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

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

public class ContentComboLabel extends BaseLabel {

  private ComboIcon myComboIcon = new ComboIcon() {
    @Override
    public Rectangle getIconRec() {
      return new Rectangle(getWidth() - getIconWidth(), 0, getIconWidth(), getHeight());
    }

    @Override
    public boolean isActive() {
      return myUi.myWindow.isActive();
    }
  };
  private ComboContentLayout myLayout;

  public ContentComboLabel(ComboContentLayout layout) {
    super(layout.myUi, true);
    myLayout = layout;
    addMouseListener(new MouseAdapter(){});
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);

    if (UIUtil.isActionClick(e)) {
      myUi.showContentPopup(e);
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
      g.translate(0, -TAB_SHIFT);
      super.paintComponent(g);
      g.translate(0, TAB_SHIFT);
    } else {
      super.paintComponent(g);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isToDrawCombo()) {
      return super.getPreferredSize();
    }

    int width = 0;
    for (int i = 0; i < myUi.myManager.getContentCount(); i++) {
      String text = myUi.myManager.getContent(i).getDisplayName();
      int eachTextWidth = getFontMetrics(getFont()).stringWidth(text);
      width = Math.max(eachTextWidth, width);
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
      int x = myComboIcon.getIconRec().x - 3;
      int yTop = myComboIcon.getIconRec().y;
      int yBottom = yTop + myComboIcon.getIconHeight();
      g.drawLine(x, yTop + 1, x, yBottom - 3);

    }
  }

  @Override
  public Content getContent() {
    return myUi.myManager.getSelectedContent();
  }
}
