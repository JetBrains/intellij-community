// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.rd.GraphicsExKt;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.Gray;
import com.intellij.ui.content.Content;
import com.intellij.ui.popup.PopupState;
import com.intellij.ui.scale.JBUIScale;
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

final class ContentComboLabel extends ContentLabel {
  private final PopupState<JBPopup> myPopupState = PopupState.forPopup();

  private final ComboIcon myComboIcon = new ComboIcon() {
    @Override
    public Rectangle getIconRec() {
      return new Rectangle(getWidth() - getIconWidth() - ICONS_GAP, 0, getIconWidth(), getHeight());
    }

    @Override
    public boolean isActive() {
      return myUi.window.isActive();
    }
  };
  private final ComboContentLayout myLayout;

  ContentComboLabel(@NotNull ComboContentLayout layout) {
    super(layout.ui, true);

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
  protected @Nullable String getOriginalText() {
    Content content = getContent();
    //noinspection DialogTitleCapitalization
    return content != null ? content.getDisplayName() : null;
  }

  @Override
  protected void handleMouseClick(@NotNull MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_RELEASED) {
      handleActionsClick(e);
    }
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      if (findHoveredIcon() != null) return;

      if (UIUtil.isActionClick(e)) {
        if (myPopupState.isRecentlyHidden()) return; // do not show new popup
        ToolWindowContentUi.toggleContentPopup(myUi, myUi.getContentManager(), myPopupState);
      }
    }
  }

  void update() {
    setBorder(isToDrawCombo() ? JBUI.Borders.empty(0, 8) : JBUI.Borders.empty());
    updateTextAndIcon(getContent(), true, ExperimentalUI.isNewUI());
    updateAdditionalActions();
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
      if (hasActiveIcons()) size.width -= ICONS_GAP;
      size.width += myComboIcon.getIconWidth();
    }

    if (ExperimentalUI.isNewToolWindowsStripes()) {
      setBorder(myLayout.shouldShowId()
                ? JBUI.Borders.empty(0, JBUI.CurrentTheme.ToolWindow.headerTabLeftRightInsets().left, 0, ICONS_GAP)
                : JBUI.Borders.empty(0, JBUI.CurrentTheme.ToolWindow.headerLabelLeftRightInsets().left, 0, ICONS_GAP));
    }

    return size;
  }

  private boolean isToDrawCombo() {
    return myLayout.isToDrawCombo();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Color bgColor = getTabColor();
    if (bgColor != null) {
      int borderThickness = JBUIScale.scale(1);
      Dimension size = getSize();
      Rectangle rect = new Rectangle(0, borderThickness, size.width, size.height - 2 * borderThickness);
      GraphicsExKt.fill2DRect((Graphics2D)g, rect, bgColor);
    }
    super.paintComponent(g);
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
    return myUi.getContentManager().getSelectedContent();
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
