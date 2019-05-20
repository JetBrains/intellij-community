// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GenericKeymapManager extends DefaultKeymap {
  public GenericKeymapManager(@NotNull List<? extends BundledKeymapProvider> providers) {
    super(providers);
  }

  public GenericKeymapManager() {
  }

  @NotNull
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

  @NotNull
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

    return super.getKeymapPresentableName(keymap);
  }
}
