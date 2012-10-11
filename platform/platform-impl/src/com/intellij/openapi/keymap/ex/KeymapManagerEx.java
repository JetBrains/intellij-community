/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.options.SchemesManager;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class KeymapManagerEx extends KeymapManager {
  public static KeymapManagerEx getInstanceEx(){
    return (KeymapManagerEx)getInstance();
  }

  /**
   * @return all available keymaps. The method return an empty array if no
   * keymaps are available.
   */
  public abstract Keymap[] getAllKeymaps();

  public abstract void setActiveKeymap(Keymap activeKeymap);

  public abstract void bindShortcuts(String sourceActionId, String targetActionId);
  public abstract Set<String> getBoundActions();
  public abstract String getActionBinding(String actionId);

  public abstract SchemesManager<Keymap, KeymapImpl> getSchemesManager();

  public abstract void addWeakListener(@NotNull KeymapManagerListener listener);

  public abstract void removeWeakListener(@NotNull KeymapManagerListener listenerToRemove);
}
