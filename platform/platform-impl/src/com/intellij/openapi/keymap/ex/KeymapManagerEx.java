// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.ex;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.options.SchemeManager;
import org.jetbrains.annotations.NotNull;

public abstract class KeymapManagerEx extends KeymapManager {
  public static KeymapManagerEx getInstanceEx() {
    return (KeymapManagerEx)getInstance();
  }

  /**
   * @return all available keymaps. The method return an empty array if no
   * keymaps are available.
   */
  public abstract Keymap[] getAllKeymaps();

  public abstract void setActiveKeymap(@NotNull Keymap keymap);

  public abstract SchemeManager<Keymap> getSchemeManager();

  public abstract void addWeakListener(@NotNull KeymapManagerListener listener);

  public abstract void removeWeakListener(@NotNull KeymapManagerListener listenerToRemove);
}
