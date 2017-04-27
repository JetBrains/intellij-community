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
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static com.intellij.openapi.util.SystemInfo.isMac;
import static java.util.stream.Collectors.toList;

/**
 * @author Sergey.Malenkov
 */
final class Keymaps {
  private static final Condition<Keymap> FILTER = keymap -> !isMac || !KeymapManager.DEFAULT_IDEA_KEYMAP.equals(keymap.getName());

  static KeymapScheme find(@NotNull List<KeymapScheme> list, @NotNull Predicate<KeymapScheme> predicate) {
    for (KeymapScheme scheme : list) {
      if (predicate.test(scheme)) return scheme;
    }
    return null;
  }

  static KeymapScheme add(@NotNull List<KeymapScheme> list, @NotNull KeymapScheme scheme) {
    list.add(scheme);
    return scheme;
  }

  static KeymapScheme remove(@NotNull List<KeymapScheme> list, @NotNull KeymapScheme scheme) {
    list.remove(scheme);
    return getSchemeToSelect(list, scheme.getParent());
  }

  static KeymapScheme reset(@NotNull List<KeymapScheme> list) {
    list.clear();
    getKeymaps().forEach(keymap -> add(list, new KeymapScheme(keymap)));
    return getSchemeToSelect(list, null);
  }

  static KeymapScheme apply(@NotNull List<KeymapScheme> list, KeymapScheme selected) {
    Keymap active = selected == null ? null : selected.getOriginal();
    List<Keymap> keymaps = list.stream().map(scheme -> scheme.apply()).collect(toList());
    KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    manager.setKeymaps(keymaps, active, FILTER);
    return selected;
  }

  @NotNull
  private static List<Keymap> getKeymaps() {
    KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    return manager.getKeymaps(FILTER);
  }

  private static KeymapScheme getSchemeToSelect(@NotNull List<KeymapScheme> list, Keymap active) {
    if (active == null) active = KeymapManager.getInstance().getActiveKeymap();
    KeymapScheme found = null;
    for (KeymapScheme scheme : list) {
      Keymap keymap = scheme.getOriginal();
      if (keymap == active) return scheme; // return active keymap if it is present
      if (found == null || KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP.equals(keymap.getName())) {
        // select MacOS X keymap if default keymap is filtered out
        // select first keymap if MacOS X keymap is not present
        found = scheme;
      }
    }
    return found;
  }

  static boolean isModified(@NotNull List<KeymapScheme> list, KeymapScheme selected) {
    Keymap active = selected == null ? null : selected.getOriginal();
    if (!Objects.equals(active, KeymapManager.getInstance().getActiveKeymap())) return true;

    Iterator<Keymap> keymaps = getKeymaps().iterator();
    Iterator<KeymapScheme> schemes = list.iterator();
    while (keymaps.hasNext() && schemes.hasNext()) {
      if (!Objects.equals(keymaps.next(), schemes.next().getCurrent())) return true;
    }
    return keymaps.hasNext() || schemes.hasNext();
  }
}
