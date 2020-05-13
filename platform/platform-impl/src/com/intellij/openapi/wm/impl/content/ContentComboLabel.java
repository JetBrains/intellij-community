// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.ui.Gray;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.popup.util.PopupState;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class ContentComboLabel extends BaseLabel {
  private final PopupState myPopupState = new PopupState();

  private final ComboIcon myComboIcon = new ComboIcon() {
    @Override
    public Rectangle getIconRec() {
      return new Rectangle(getWidth() - getIconWidth() - 3, 0, getIconWidth(), getHeight());
    }

    @Override
    public boolean isActive() {
      return myUi.window.isActive();
    }
  };
  private final ComboContentLayout myLayout;

  ContentComboLabel(@NotNull ComboContentLayout layout) {
    super(layout.myUi, true);

    myLayout = layout;
    addMouseListener(new MouseAdapter(){});
    if (ScreenReader.isActive()) {
      setFocusable(true);
      addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
            ToolWindowContentUi.toggleContentPopup(myUi, myUi.getContentManager());
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
      if (myPopupState.isRecentlyHidden()) return; // do not show new popup
      ToolWindowContentUi.toggleContentPopup(myUi, myUi.getContentManager(), myPopupState);
    }
  }

  void update() {
    setBorder(isToDrawCombo() ? JBUI.Borders.empty(0, 8) : JBUI.Borders.empty());
    updateTextAndIcon(getContent(), true);
  }

  @Override
  protected boolean allowEngravement() {
    return myUi == null || myUi.window.isActive();
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

  @Nullable
  @Override
  public Content getContent() {
    ContentManager contentManager = myUi.getContentManager();
    return contentManager == null ? null : contentManager.getSelectedContent();
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleContentComboLabel();
    }
    return accessibleContext;
  }

  private final class AccessibleContentComboLabel extends AccessibleBaseLabel implements AccessibleAction {
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
        ToolWindowContentUi.toggleContentPopup(myUi, myUi.getContentManager());
        return true;
      }
      else {
        return false;
      }
    }
  }
}
