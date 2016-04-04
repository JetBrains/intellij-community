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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;

import java.awt.BorderLayout;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * @author Sergey.Malenkov
 */
final class ShortcutFilteringPanel extends JPanel {
  final KeyboardShortcutPanel myKeyboardPanel = new KeyboardShortcutPanel(new VerticalLayout(JBUI.scale(2)));
  final MouseShortcutPanel myMousePanel = new MouseShortcutPanel(true);

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
          String text = shortcut == null ? null : KeymapUtil.getMouseShortcutText(
            shortcut.getButton(),
            shortcut.getModifiers(),
            shortcut.getClickCount());
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
    super(new VerticalLayout(JBUI.scale(2)));

    myKeyboardPanel.myFirstStroke.setColumns(13);
    myKeyboardPanel.myFirstStroke.putClientProperty("JTextField.variant", "search");
    myKeyboardPanel.mySecondStroke.setColumns(13);
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
    label.setIcon(AllIcons.General.MouseShortcut);
    label.setForeground(MouseShortcutPanel.FOREGROUND);
    label.setBorder(JBUI.Borders.empty(14, 4));
    myMousePanel.add(BorderLayout.CENTER, label);
    myMousePanel.addPropertyChangeListener("shortcut", myPropertyListener);
    myMousePanel.setBorder(JBUI.Borders.customLine(MouseShortcutPanel.BORDER, 1, 0, 0, 0));

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

  void showPopup(Component component) {
    if (myPopup == null || myPopup.getContent() == null) {
      myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, myKeyboardPanel.myFirstStroke)
        .setRequestFocus(true)
        .setTitle(KeyMapBundle.message("filter.settings.popup.title"))
        .setCancelKeyEnabled(false)
        .setMovable(true)
        .createPopup();
    }
    myPopup.showUnderneathOf(component);
  }

  void hidePopup() {
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }
  }
}
