package com.intellij.notification.impl.ui;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * @author spleaner
 */
public class StickyButton extends JToggleButton {

  public StickyButton(final String text, final ActionListener listener) {
    this(text);
    addActionListener(listener);
  }

  public StickyButton(final String text) {
    super(text);

    setRolloverEnabled(true);
    setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
    setUI(new StickyButtonUI());
  }

  @Override
  public void setUI(ButtonUI ui) {
    if (ui instanceof StickyButtonUI) {
      super.setUI(ui);
    } else {
      super.setUI(createUI());
    }
  }

  protected ButtonUI createUI() {
    return new StickyButtonUI();
  }

  @Override
  public Color getForeground() {
    if (/*model.isRollover() || */ model.isSelected()) {
      return Color.WHITE;
    }

    return super.getForeground();
  }
}
