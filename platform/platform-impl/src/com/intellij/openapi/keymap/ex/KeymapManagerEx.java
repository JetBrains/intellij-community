/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public abstract void setActiveKeymap(@Nullable Keymap keymap);

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
  @SuppressWarnings("deprecation")
  @Deprecated
  public final SchemesManager<Keymap> getSchemesManager() {
    return (SchemesManager<Keymap>)getSchemeManager();
  }

  public abstract void addWeakListener(@NotNull KeymapManagerListener listener);

  public abstract void removeWeakListener(@NotNull KeymapManagerListener listenerToRemove);
}
