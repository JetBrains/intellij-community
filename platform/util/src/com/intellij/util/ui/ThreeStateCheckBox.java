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
package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

/**
 * @author spleaner
 */
public class ThreeStateCheckBox extends JCheckBox {
  private State myState;
  private boolean myThirdStateEnabled = true;

  public enum State {
    SELECTED, NOT_SELECTED, DONT_CARE
  }

  public ThreeStateCheckBox() {
    this(null, null, State.DONT_CARE);
  }

  public ThreeStateCheckBox(final State initial) {
    this(null, null, initial);
  }

  public ThreeStateCheckBox(final String text) {
    this(text, null, State.DONT_CARE);
  }

  public ThreeStateCheckBox(final String text, final State initial) {
    this(text, null, initial);
  }

  public ThreeStateCheckBox(final String text, final Icon icon) {
    this(text, icon, State.DONT_CARE);
  }

  public ThreeStateCheckBox(final String text, final Icon icon, final State initial) {
    super(text, icon);

    setModel(new ToggleButtonModel() {
      @Override
      public void setSelected(boolean selected) {
        myState = nextState();
        fireStateChanged();
        fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, this, ItemEvent.SELECTED));
      }

      @Override
      public boolean isSelected() {
        return myState == State.SELECTED;
      }
    });

    setState(initial);
  }

  private State nextState() {
    switch (myState) {
      case SELECTED:
        return State.NOT_SELECTED;
      case NOT_SELECTED:
        if (myThirdStateEnabled) {
          return State.DONT_CARE;
        }
        else {
          return State.SELECTED;
        }
      default:
        return State.SELECTED;
    }
  }

  public boolean isThirdStateEnabled() {
    return myThirdStateEnabled;
  }

  public void setThirdStateEnabled(final boolean thirdStateEnabled) {
    myThirdStateEnabled = thirdStateEnabled;
  }

  @Override
  public void setSelected(final boolean b) {
    setState(b ? State.SELECTED : State.NOT_SELECTED);
  }

  public void setState(State state) {
    myState = state;
    repaint();
  }

  public State getState() {
    return myState;
  }


  @Override
  protected void paintComponent(Graphics g) {
    if (UIUtil.isUnderAquaLookAndFeel()) {
      paintIndeterminateIcon(g);
      return;
    }

    super.paintComponent(g);
    switch (getState()) {
      case DONT_CARE:
        Icon icon = getIcon();
        if (icon == null) {
          icon = UIManager.getIcon("CheckBox.icon");
        }
        if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
          icon = EmptyIcon.create(20, 18);
        }
        if (icon != null) {
          final Insets i = getInsets();
          final Rectangle r = getBounds();
          final Rectangle r1 = new Rectangle();
          r1.x = i.left;
          r1.y = i.top;
          r1.width = r.width - (i.right + r1.x);
          r1.height = r.height - (i.bottom + r1.y);

          final Rectangle r2 = new Rectangle();
          final Rectangle r3 = new Rectangle();
          SwingUtilities.layoutCompoundLabel(
            this, getFontMetrics(getFont()), getText(), icon,
            getVerticalAlignment(), getHorizontalAlignment(),
            getVerticalTextPosition(), getHorizontalTextPosition(),
            r1, r2, r3,
            getText() == null ? 0 : getIconTextGap());

          // selected table cell: do not paint white on white
          g.setColor(UIUtil.getTreeForeground());
          int height = r2.height / 10;
          int width = r2.width / 3;
          g.fillRect(r2.x + r2.width / 2 - width / 2, r2.y + r2.height / 2 - height / 2, width, height);
        }
        break;
      default:
        break;
    }
  }

  protected void paintIndeterminateIcon(Graphics g) {
    State initial = getState();
    try {
      if (getState() == State.DONT_CARE) {
        setSelected(true);
        putClientProperty("JButton.selectedState", "indeterminate");
      } else {
        putClientProperty("JButton.selectedState", null);
      }
      super.paintComponent(g);
    } finally {
      setState(initial);
    }
  }
}
