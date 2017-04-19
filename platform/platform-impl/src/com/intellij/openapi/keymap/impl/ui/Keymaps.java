/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.util.Condition;

import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.SystemInfo.isMac;

/**
 * @author Sergey.Malenkov
 */
final class Keymaps {
  private static final Condition<Keymap> FILTER = keymap -> !isMac || !KeymapManager.DEFAULT_IDEA_KEYMAP.equals(keymap.getName());

  static void apply(List<Keymap> all, Keymap active) {
    KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    manager.setKeymaps(all, active, FILTER);
  }

  static List<Keymap> getAll() {
    KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    return manager.getKeymaps(FILTER);
  }

  static boolean isActive(Keymap keymap) {
    return Objects.equals(keymap, KeymapManager.getInstance().getActiveKeymap());
  }

  static Keymap getActive() {
    return KeymapManager.getInstance().getActiveKeymap();
  }

  static Keymap getActiveFrom(List<Keymap> all) {
    Keymap active = getActive();
    Keymap found = null;
    for (Keymap keymap : all) {
      if (keymap == active) return active; // return active keymap if it is present
      if (found == null || KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP.equals(keymap.getName())) {
        // select MacOS X keymap if default keymap is filtered out
        // select first keymap if MacOS X keymap is not present
        found = keymap;
      }
    }
    return found;
  }
}
