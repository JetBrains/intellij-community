package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.SystemInfo;

public class GenericKeymapManager extends DefaultKeymap {
  @Override
  public String getDefaultKeymapName() {
    if (SystemInfo.isMac) {
      return "Mac OS X 10.5+";
    }
    else if (SystemInfo.isXWindow) {
      return KeymapManager.X_WINDOW_KEYMAP;
    }
    else {
      return KeymapManager.DEFAULT_IDEA_KEYMAP;
    }
  }

  @Override
  public String getKeymapPresentableName(KeymapImpl keymap) {
    final String name = keymap.getName();

    if (getDefaultKeymapName().equals(name)) {
      return "Default";
    }

    if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name)) {
      return "IntelliJ IDEA Classic";
    }

    if ("Mac OS X".equals(name)) {
      return "IntelliJ IDEA Classic (OS X)";
    }

    return name;
  }
}
