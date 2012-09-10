/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * User: Vassiliy.Kudryashov
 */
public class DropDownPanel extends JPanel {
  private static final Icon DOWN = AllIcons.General.ComboArrow;
  private static final Icon UP = IconUtil.flip(AllIcons.General.ComboArrow, false);

  private final JLabel myLabel;
  private @Nullable JComponent myContent;
  private boolean myExpanded = true;

  public DropDownPanel() {
    this(null, null, true);
  }

  public DropDownPanel(@Nullable String title, @Nullable JComponent content, boolean withSeparatorLine) {
    super(new BorderLayout());
    myLabel = new ButtonLabel(withSeparatorLine);
    add(myLabel, BorderLayout.NORTH);
    setTitle(title);
    setContent(content);
    setExpanded(false);
  }

  public void setContent(@Nullable JComponent content) {
    if (myContent != null) {
      remove(myContent);
    }

    myContent = content;

    if (myContent != null && myExpanded) {
      add(myContent, BorderLayout.CENTER);
    }
  }

  public void setTitle(@Nullable String title) {
    myLabel.setText(title);
  }

  public void setExpanded(boolean expanded) {
    if (myExpanded == expanded) {
      return;
    }
    myLabel.setIcon(expanded ? UP : DOWN);
    if (myContent != null) {
      if (expanded) {
        add(myContent, BorderLayout.CENTER);
      }
      else {
        remove(myContent);
      }
    }
    myExpanded = expanded;
    revalidate();
    repaint();
  }

  public boolean isExpanded() {
    return myExpanded;
  }

  private class ButtonLabel extends JLabel implements ActionButtonComponent {
    private boolean myMouseDown;
    private boolean myRollover;
    private final boolean myLine;

    private ButtonLabel(boolean withSeparatorLine) {
      myLine = withSeparatorLine;
      setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
      setIconTextGap(2);
      enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public void paintComponent(Graphics g) {
      ActionButtonLook.IDEA_LOOK.paintBackground(g, this);
      ActionButtonLook.IDEA_LOOK.paintBorder(g, this);
      super.paintComponent(g);
      if (myLine) {
        g.setColor(GroupedElementsRenderer.POPUP_SEPARATOR_FOREGROUND);
        Icon icon = getIcon();
        final FontMetrics fm = getFontMetrics(getFont());
        String text = getText();
        Border border = getBorder();
        Insets insets = border != null ? border.getBorderInsets(this) : new Insets(0, 0, 0, 0);
        int width = (icon != null ? icon.getIconWidth() : 0)
                    + (text != null ? getIconTextGap() + fm.stringWidth(text) : 0)
                    + insets.left;
        final int lineY = (UIUtil.isUnderNativeMacLookAndFeel() ? 1 : 3) + fm.getHeight() / 2;
        g.drawLine(width + 3, lineY, getWidth() - 3 - insets.right, lineY);
      }
    }

    @Override
    public int getPopState() {
      if (myRollover && myMouseDown && isEnabled()) {
        return PUSHED;
      }
      else {
        return !myRollover || !isEnabled() ? NORMAL : POPPED;
      }
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
      if (e.isConsumed()) return;
      boolean skipPress = e.isMetaDown() || e.getButton() != MouseEvent.BUTTON1;
      switch (e.getID()) {
        case MouseEvent.MOUSE_PRESSED:
          if (skipPress || !isEnabled()) return;
          myMouseDown = true;
          repaint();
          break;

        case MouseEvent.MOUSE_RELEASED:
          if (skipPress || !isEnabled()) return;
          myMouseDown = false;
          if (myRollover) {
            setExpanded(!isExpanded());
          }
          repaint();
          break;

        case MouseEvent.MOUSE_ENTERED:
          myRollover = true;
          repaint();
          break;

        case MouseEvent.MOUSE_EXITED:
          myRollover = false;
          repaint();
          break;
      }
    }
  }
}
