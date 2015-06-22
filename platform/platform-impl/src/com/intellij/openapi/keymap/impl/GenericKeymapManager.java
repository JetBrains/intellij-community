package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

public class GenericKeymapManager extends DefaultKeymap {
  @Override
  public String getDefaultKeymapName() {
    if (SystemInfo.isMac) {
      return KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP;
    }
    else if (SystemInfo.isXWindow) {
      return KeymapManager.X_WINDOW_KEYMAP;
    }
    else {
      return KeymapManager.DEFAULT_IDEA_KEYMAP;
    }
  }

  @Override
  public String getKeymapPresentableName(@NotNull KeymapImpl keymap) {
    final String name = keymap.getName();

    if (getDefaultKeymapName().equals(name)) {
      return "Default";
    }

    if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name)) {
      return "IntelliJ IDEA Classic" + (SystemInfo.isMac ? " (Windows)" : "");
    }

    if ("Mac OS X".equals(name)) {
      return "IntelliJ IDEA Classic (OS X)";
    }

    return name;
  }
}
