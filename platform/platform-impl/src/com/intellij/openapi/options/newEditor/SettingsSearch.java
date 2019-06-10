// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SearchTextField;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * @author Sergey.Malenkov
 */
abstract class SettingsSearch extends SearchTextField implements KeyListener {
  private boolean myDelegatingNow;

  SettingsSearch() {
    super("SettingsSearchHistory");
    updateToolTipText();
    addKeyListener(new KeyAdapter() {
    });
    if (!SystemInfo.isMac) {
      JTextField editor = getTextEditor();
      editor.putClientProperty("JTextField.variant", "search");
      if (!(editor.getUI() instanceof TextFieldWithPopupHandlerUI)) {
        editor.setUI((DarculaTextFieldUI)DarculaTextFieldUI.createUI(editor));
        editor.setBorder(new DarculaTextBorder());
      }
    }
  }

  abstract void onTextKeyEvent(KeyEvent event);

  void delegateKeyEvent(KeyEvent event) {
    keyEventToTextField(event);
  }

  @Override
  protected boolean preprocessEventForTextField(KeyEvent event) {
    if (!myDelegatingNow) {
      KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(event);
      String strokeString = stroke.toString();
      if ("pressed ESCAPE".equals(strokeString) && getText().length() > 0) {
        setText(""); // reset filter on ESC
        return true;
      }
      if (getTextEditor().isFocusOwner()) {
        try {
          myDelegatingNow = true;
          int code = stroke.getKeyCode();
          boolean treeNavigation = stroke.getModifiers() == 0 && (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN);
          if (treeNavigation || !hasAction(stroke, getTextEditor().getInputMap())) {
            onTextKeyEvent(event);
            return true;
          }
        }
        finally {
          myDelegatingNow = false;
        }
      }
    }
    return false;
  }

  private static boolean hasAction(KeyStroke stroke, InputMap map) {
    return map != null && map.get(stroke) != null;
  }

  @Override
  public void keyPressed(KeyEvent event) {
    keyTyped(event);
  }

  @Override
  public void keyReleased(KeyEvent event) {
    keyTyped(event);
  }

  @Override
  public void keyTyped(KeyEvent event) {
    Object source = event.getSource();
    if (source instanceof JTree) {
      JTree tree = (JTree)source;
      if (!hasAction(KeyStroke.getKeyStrokeForEvent(event), tree.getInputMap())) {
        delegateKeyEvent(event);
      }
    }
  }

  void updateToolTipText() {
    ShortcutSet set = SettingsDialog.getFindActionShortcutSet();
    String text = set == null ? null : StringUtil.join(set.getShortcuts(), KeymapUtil::getShortcutText, "\n");
    getTextEditor().setToolTipText(StringUtil.isEmpty(text) ? null : text);
  }
}
