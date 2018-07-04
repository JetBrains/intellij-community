/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ui.Gray;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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
    if (ScreenReader.isActive()) {
      setFocusable(true);
      addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
            myUi.toggleContentPopup();
          }
          super.keyPressed(e);
        }
      });
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);

    if (UIUtil.isActionClick(e)) {
      myUi.toggleContentPopup();
    }
  }

  void update() {
    setBorder(isToDrawCombo() ? JBUI.Borders.empty(0, 8) : JBUI.Borders.empty());
    updateTextAndIcon(getContent(), true);
  }

  @Override
  protected boolean allowEngravement() {
    return myUi == null || myUi.myWindow.isActive();
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    if (!isMinimumSizeSet()) {
      size.width = isToDrawCombo() ? myComboIcon.getIconWidth() : 0;
      Icon icon = getIcon();
      if (icon != null) size.width += icon.getIconWidth() + getIconTextGap();
      Insets insets = getInsets();
      if (insets != null) size.width += insets.left + insets.right;
    }
    return size;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (!isPreferredSizeSet() && isToDrawCombo()) {
      size.width += myComboIcon.getIconWidth();
    }
    return size;
  }

  private boolean isToDrawCombo() {
    return myLayout.isToDrawCombo();
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);
    if (isToDrawCombo()) {
      myComboIcon.paintIcon(this, g);
      g.setColor(Gray._255.withAlpha(100));
    }
  }

  @Override
  public Content getContent() {
    return myUi.myManager.getSelectedContent();
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleContentComboLabel();
    }
    return accessibleContext;
  }

  protected class AccessibleContentComboLabel extends AccessibleBaseLabel implements AccessibleAction {

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PUSH_BUTTON;
    }

    @Override
    public AccessibleAction getAccessibleAction() {
      return this;
    }

    // Implements AccessibleAction

    @Override
    public int getAccessibleActionCount() {
      return 1;
    }

    @Override
    public String getAccessibleActionDescription(int index) {
      return index == 0 ? UIManager.getString("ComboBox.togglePopupText") : null;
    }

    @Override
    public boolean doAccessibleAction(int index) {
      if (index == 0) {
        myUi.toggleContentPopup();
        return true;
      }
      else {
        return false;
      }
    }
  }
}
