// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.UtilUiBundle;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

public class ThreeStateCheckBox extends JCheckBox {
  public static final String THREE_STATE_CHECKBOX_STATE = "ThreeStateCheckbox.state";

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

  public ThreeStateCheckBox(@Nls String text) {
    this(text, null, State.DONT_CARE);
  }

  public ThreeStateCheckBox(@Nls String text, final State initial) {
    this(text, null, initial);
  }

  public ThreeStateCheckBox(@Nls String text, final Icon icon) {
    this(text, icon, State.DONT_CARE);
  }

  public ThreeStateCheckBox(@Nls String text, final Icon icon, final State initial) {
    super(text, icon);

    setModel(new ToggleButtonModel() {
      @Override
      public void setSelected(boolean selected) {
        setState(nextState());
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

  @NotNull
  protected State nextState() {
    return nextState(myState, myThirdStateEnabled);
  }

  @NotNull
  public static State nextState(@NotNull State state, boolean thirdStateEnabled) {
    return switch (state) {
      case SELECTED -> State.NOT_SELECTED;
      case NOT_SELECTED -> thirdStateEnabled ? State.DONT_CARE : State.SELECTED;
      case DONT_CARE -> State.SELECTED;
    };
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
    State oldState = myState;
    myState = state;

    String value = state == State.DONT_CARE ? "indeterminate" : null;
    putClientProperty("JButton.selectedState", value);

    firePropertyChange(THREE_STATE_CHECKBOX_STATE, oldState, state);

    repaint();
  }

  public State getState() {
    return myState;
  }


  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
      return;
    }

    if (getState() == State.DONT_CARE) {
      Icon icon = getIcon();
      if (icon == null) {
        icon = UIManager.getIcon("CheckBox.icon");
      }
      if (StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) {
        icon = JBUIScale.scaleIcon(EmptyIcon.create(20, 18));
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
        String text = getText();
        SwingUtilities.layoutCompoundLabel(
          this, getFontMetrics(getFont()), text, icon,
          getVerticalAlignment(), getHorizontalAlignment(),
          getVerticalTextPosition(), getHorizontalTextPosition(),
          r1, r2, r3,
          text == null ? 0 : getIconTextGap());

        // selected table cell: do not paint white on white
        g.setColor(UIUtil.getTreeForeground());
        int height = r2.height / 10;
        int width = r2.width / 3;
        g.fillRect(r2.x + r2.width / 2 - width / 2, r2.y + r2.height / 2 - height / 2, width, height);
      }
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleThreeStateCheckBox();
    }
    return accessibleContext;
  }

  /**
   * Emulate accessibility behavior of tri-state checkboxes, as tri-state checkboxes
   * are not part of the JAB specification.
   */
  protected class AccessibleThreeStateCheckBox extends AccessibleJCheckBox {
    @Override
    public AccessibleRole getAccessibleRole() {
      if (myThirdStateEnabled) {
        // Return TOGGLE_BUTTON so that screen readers don't announce the "not checked" state
        return AccessibleRole.TOGGLE_BUTTON;
      }

      return super.getAccessibleRole();
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      if (myThirdStateEnabled) {
        // Remove CHECKED so that screen readers don't announce the "checked" state
        AccessibleStateSet set = super.getAccessibleStateSet();
        set.remove(AccessibleState.CHECKED);
        return set;
      }

      return super.getAccessibleStateSet();
    }

    @Override
    public String getAccessibleName() {
      if (myThirdStateEnabled) {
        // Add a state description suffix to the accessible name, so that screen readers
        // announce the state as part of the accessible name.
        return addStateDescription(super.getAccessibleName());
      }
      return super.getAccessibleName();
    }

    private @Nls String addStateDescription(@Nls String name) {
      String key = switch (getState()) {
        case SELECTED -> "accessible.checkbox.name.checked";
        case NOT_SELECTED -> "accessible.checkbox.name.not.checked";
        case DONT_CARE -> "accessible.checkbox.name.partially.checked";
      };
      return AccessibleContextUtil.combineAccessibleStrings(name, UtilUiBundle.message(key));
    }
  }
}
