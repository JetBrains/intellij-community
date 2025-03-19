// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ProxyShortcutSet implements ShortcutSet {
  private final String myActionId;

  ProxyShortcutSet(@NotNull String actionId) {
    myActionId = actionId;
  }

  @Override
  public Shortcut @NotNull [] getShortcuts() {
    KeymapManager manager = KeymapManager.getInstance();
    return manager != null ? manager.getActiveKeymap().getShortcuts(myActionId) : Shortcut.EMPTY_ARRAY;
  }

  public @NotNull String getActionId() {
    return myActionId;
  }
}