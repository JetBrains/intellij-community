// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

final class ShortcutFilteringPanel extends JPanel {
  private final KeyboardShortcutPanel myKeyboardPanel = new KeyboardShortcutPanel(false, new VerticalLayout(2));
  private final MouseShortcutPanel myMousePanel = new MouseShortcutPanel(true);

  private Shortcut myShortcut;
  private JBPopup myPopup;

  private final ChangeListener myChangeListener = new ChangeListener() {
    @Override
    public void stateChanged(ChangeEvent event) {
      boolean selected = myKeyboardPanel.mySecondStrokeEnable.isSelected();
      myKeyboardPanel.mySecondStroke.setVisible(selected);
      myMousePanel.setVisible(!selected);
      if (selected && myShortcut instanceof MouseShortcut) {
        setShortcut(null);
      }
    }
  };
  private final PropertyChangeListener myPropertyListener = new PropertyChangeListener() {
    private volatile boolean myInternal;

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      boolean internal = myInternal;
      myInternal = true;
      Object value = event.getNewValue();
      if (ShortcutFilteringPanel.this == event.getSource()) {
        if (value instanceof KeyboardShortcut) {
          KeyboardShortcut shortcut = (KeyboardShortcut)value;
          myMousePanel.setShortcut(null);
          myKeyboardPanel.setShortcut(shortcut);
          if (null != shortcut.getSecondKeyStroke()) {
            myKeyboardPanel.mySecondStrokeEnable.setSelected(true);
          }
        }
        else {
          MouseShortcut shortcut = value instanceof MouseShortcut ? (MouseShortcut)value : null;
          String text = shortcut == null ? null : KeymapUtil.getMouseShortcutText(shortcut);
          myMousePanel.setShortcut(shortcut);
          myKeyboardPanel.setShortcut(null);
          myKeyboardPanel.myFirstStroke.setText(text);
          myKeyboardPanel.mySecondStroke.setText(null);
          myKeyboardPanel.mySecondStroke.setEnabled(false);
        }
      }
      else if (value instanceof Shortcut) {
        setShortcut((Shortcut)value);
      }
      else if (!internal) {
        setShortcut(null);
      }
      myInternal = internal;
    }
  };

  ShortcutFilteringPanel() {
    super(new VerticalLayout(2));

    myKeyboardPanel.myFirstStroke.setColumns(20);
    myKeyboardPanel.myFirstStroke.putClientProperty("JTextField.variant", "search");
    myKeyboardPanel.mySecondStroke.setColumns(20);
    myKeyboardPanel.mySecondStroke.putClientProperty("JTextField.variant", "search");
    myKeyboardPanel.mySecondStroke.setVisible(false);
    myKeyboardPanel.mySecondStrokeEnable.setText(KeyMapBundle.message("filter.enable.second.stroke.checkbox"));
    myKeyboardPanel.mySecondStrokeEnable.addChangeListener(myChangeListener);
    myKeyboardPanel.add(VerticalLayout.TOP, myKeyboardPanel.myFirstStroke);
    myKeyboardPanel.add(VerticalLayout.TOP, myKeyboardPanel.mySecondStrokeEnable);
    myKeyboardPanel.add(VerticalLayout.TOP, myKeyboardPanel.mySecondStroke);
    myKeyboardPanel.addPropertyChangeListener("shortcut", myPropertyListener);
    myKeyboardPanel.setBorder(JBUI.Borders.empty(4));

    JLabel label = new JLabel(KeyMapBundle.message("filter.mouse.pad.label"));
    label.setOpaque(false);
    label.setIcon(AllIcons.General.Mouse);
    label.setForeground(UIUtil.getContextHelpForeground());
    label.setBorder(JBUI.Borders.empty(14, 4));
    myMousePanel.add(BorderLayout.CENTER, label);
    myMousePanel.addPropertyChangeListener("shortcut", myPropertyListener);
    myMousePanel.setBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0));

    add(VerticalLayout.TOP, myKeyboardPanel);
    add(VerticalLayout.TOP, myMousePanel);
    addPropertyChangeListener("shortcut", myPropertyListener);
  }

  Shortcut getShortcut() {
    return myShortcut;
  }

  void setShortcut(Shortcut shortcut) {
    Shortcut old = myShortcut;
    if (old != null || shortcut != null) {
      myShortcut = shortcut;
      firePropertyChange("shortcut", old, shortcut);
    }
  }

  void showPopup(Component component, Component emitter) {
    if (myPopup == null || myPopup.isDisposed()) {
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, myKeyboardPanel.myFirstStroke)
        .setRequestFocus(true)
        .setTitle(KeyMapBundle.message("filter.settings.popup.title"))
        .setCancelKeyEnabled(false)
        .setMovable(true)
        .createPopup();
      IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
        boolean isEscWasPressed;
        @Override
        public boolean dispatch(@NotNull AWTEvent e) {
          if (e instanceof KeyEvent && e.getID() == KeyEvent.KEY_PRESSED) {
            boolean isEsc =  ((KeyEvent)e).getKeyCode() == KeyEvent.VK_ESCAPE;
            if (isEscWasPressed && isEsc) {
              myPopup.cancel();
            }
            isEscWasPressed = isEsc;
          }
          return false;
        }
      }, myPopup);
    }

    HelpTooltip.setMasterPopup(emitter, myPopup);
    myPopup.showUnderneathOf(component);
  }

  void hidePopup() {
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }
  }
}
