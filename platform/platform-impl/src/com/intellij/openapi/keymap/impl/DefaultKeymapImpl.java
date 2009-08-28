package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.awt.event.MouseEvent;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 21, 2003
 * Time: 9:00:35 PM
 * To change this template use Options | File Templates.
 */
class DefaultKeymapImpl extends KeymapImpl {
  @NonNls
  private static final String DEFAULT = "Default";

  public boolean canModify() {
    return false;
  }

  public String getPresentableName() {
    String name = getName();
    return KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name) ? DEFAULT : name;
  }

  public void readExternal(Element keymapElement, Keymap[] existingKeymaps) throws InvalidDataException {
    super.readExternal(keymapElement, existingKeymaps);

    if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(getName()) && !SystemInfo.X11PasteEnabledSystem) {
      addShortcut(IdeActions.ACTION_GOTO_DECLARATION, new MouseShortcut(MouseEvent.BUTTON2, 0, 1));
    }
  }
}
