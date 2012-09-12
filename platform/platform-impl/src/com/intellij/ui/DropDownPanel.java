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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: Vassiliy.Kudryashov
 */
public class DropDownPanel extends JPanel {
  private static final Icon[] WRAPPERS = IconUtil.getEqualSizedIcons(AllIcons.General.ComboArrowRight, AllIcons.General.ComboArrow);
  private static final Icon DOWN = WRAPPERS[0];
  private static final Icon UP = WRAPPERS[1];

  private final ButtonLabel myLabel;
  private @Nullable JComponent myContent;
  private boolean myExpanded = true;
  private final List<ChangeListener> myListeners = new CopyOnWriteArrayList<ChangeListener>();

  public DropDownPanel() {
    this(null, null);
  }

  public DropDownPanel(@Nullable String title, @Nullable JComponent content) {
    super(new BorderLayout());
    myLabel = new ButtonLabel();
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
    myLabel.getLabel().setIcon(expanded ? UP : DOWN);
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
    ChangeEvent changeEvent = new ChangeEvent(this);
    for (ChangeListener listener : myListeners) {
      listener.stateChanged(changeEvent);
    }
  }

  public boolean isExpanded() {
    return myExpanded;
  }

  public void addChangeListener(ChangeListener listener) {
    if (listener != null) {
      myListeners.add(listener);
    }
  }
  public void removeChangeListener(ChangeListener listener) {
    if (listener != null) {
      myListeners.remove(listener);
    }
  }

  private class ButtonLabel extends TitledSeparator implements ActionButtonComponent {
    private boolean myMouseDown;
    private boolean myRollover;

    private ButtonLabel() {
      setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
      myLabel.setIconTextGap(2);
      enableEvents(AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      ActionButtonLook.IDEA_LOOK.paintBackground(g, this);
      ActionButtonLook.IDEA_LOOK.paintBorder(g, this);
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
