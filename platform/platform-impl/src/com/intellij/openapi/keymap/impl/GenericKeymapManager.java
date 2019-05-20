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

  @SuppressWarnings("unused")
  public GenericKeymapManager() {
  }

  @NotNull
  @Override
  public String getDefaultKeymapName() {
    return SystemInfo.isMac ? KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP : super.getDefaultKeymapName();
  }

  @NotNull
  @Override
  public String getKeymapPresentableName(@NotNull KeymapImpl keymap) {
    String name = keymap.getName();
    if (getDefaultKeymapName().equals(name)) {
      return "Default";
    }
    else if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name)) {
      return "IntelliJ IDEA Classic" + (SystemInfo.isMac ? " (Windows)" : "");
    }
    else {
      return super.getKeymapPresentableName(keymap);
    }
  }
}
