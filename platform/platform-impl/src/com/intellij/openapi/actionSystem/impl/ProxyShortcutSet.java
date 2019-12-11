// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class ProxyShortcutSet implements ShortcutSet {
  private final String myActionId;

  ProxyShortcutSet(@NotNull String actionId) {
    myActionId = actionId;
  }

  @NotNull
  @Override
  public Shortcut[] getShortcuts() {
    KeymapManager manager = KeymapManager.getInstance();
    return manager != null ? manager.getActiveKeymap().getShortcuts(myActionId) : Shortcut.EMPTY_ARRAY;
  }
}