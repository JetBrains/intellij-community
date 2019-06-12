// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.NotNull;

/**
 * Please do not use this class outside impl package!!!
 * Please do not use this class even if you managed to make it public!!!
 * Thank you in advance.
 * The UI Engineers.
 */
final class ProxyShortcutSet implements ShortcutSet {
  private final String myActionId;

  ProxyShortcutSet(@NotNull String actionId) {
    myActionId = actionId;
  }

  @Override
  @NotNull
  public Shortcut[] getShortcuts() {
    return KeymapManager.getInstance().getActiveKeymap().getShortcuts(myActionId);
  }
}
