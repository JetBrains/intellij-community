package com.intellij.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class DialogButtonGroup extends JPanel {
  public static final int TOP = 1;
  public static final int BOTTOM = 2;
  private int myPreferredH = 0;
  private int myPreferredW = 0;

  public DialogButtonGroup() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        upPressed();
      }
    },
                           KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
                           JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        downPressed();
      }
    },
                           KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
                           JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  public void addButton(AbstractButton button) {
    addButton(button, BOTTOM);
  }

  public void addButton(AbstractButton button, int position) {
    if (TOP == position) {
      add(button, 0);
      add(Box.createVerticalStrut(5), 1);
    }
    else {
      add(Box.createVerticalStrut(5));
      add(button);
    }
    Dimension prefSize = button.getPreferredSize();
    if (prefSize.height > myPreferredH) {
      myPreferredH = prefSize.height;
    }
    if (prefSize.width > myPreferredW) {
      myPreferredW = prefSize.width;
    }
    updateButtonSizes();
  }

  public void remove(AbstractButton button) {
    super.remove(button);
    updateButtonSizes();
  }

  public void grabFocus() {
    ((JComponent)getComponent(0)).grabFocus();
  }

  private void updateButtonSizes() {
    Dimension dim = new Dimension(myPreferredW, myPreferredH);
    Component[] components = getComponents();
    if (components == null) return;
    for (int i = 0; i < components.length; i++) {
      if (components[i] instanceof AbstractButton) {
        AbstractButton button = (AbstractButton)components[i];
        button.setPreferredSize(dim);
        button.setMaximumSize(dim);
        button.setMinimumSize(dim);
      }
    }
  }

  private void upPressed() {
    Component[] components = getComponents();
    for (int i = 0; i < components.length; i++) {
      if (components[i].hasFocus()) {
        if (i == 0) {
          components[components.length - 1].requestFocus();
          return;
        }
        components[i - 1].requestFocus();
        return;
      }
    }
  }

  private void downPressed() {
    Component[] components = getComponents();
    for (int i = 0; i < components.length; i++) {
      if (components[i].hasFocus()) {
        if (i == components.length - 1) {
          components[0].requestFocus();
          return;
        }
        components[i + 1].requestFocus();
        return;
      }
    }
  }
}
