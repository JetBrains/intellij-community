// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.ex;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.options.SchemesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

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

  /**
   * Instructs the manager that one action should use shortcut of another one ({@code 'use-shortcut-of'} attribute at
   * action's config located at plugin.xml).
   *
   * @param sourceActionId if of the action which shortcut should be used for the 'target action'
   * @param targetActionId id of the action which should use shortcut of the 'source action'
   */
  public abstract void bindShortcuts(@NotNull String sourceActionId, @NotNull String targetActionId);

  public abstract void unbindShortcuts(String targetActionId);

  @NotNull
  public abstract Set<String> getBoundActions();

  @Nullable
  public abstract String getActionBinding(@NotNull String actionId);

  public abstract SchemeManager<Keymap> getSchemeManager();

  /**
   * @deprecated Please use {@link #getSchemeManager()}
   */
  @Deprecated
  public final SchemesManager<Keymap> getSchemesManager() {
    return (SchemesManager<Keymap>)getSchemeManager();
  }

  public abstract void addWeakListener(@NotNull KeymapManagerListener listener);

  public abstract void removeWeakListener(@NotNull KeymapManagerListener listenerToRemove);
}
