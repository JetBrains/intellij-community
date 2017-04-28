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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.openapi.util.text.StringUtil.naturalCompare;
import static java.util.stream.Collectors.toList;

/**
 * This class operates with the KeymapManager.
 *
 * @author Sergey.Malenkov
 */
final class KeymapSchemeManager {
  private static final Condition<Keymap> FILTER = keymap -> !isMac || !KeymapManager.DEFAULT_IDEA_KEYMAP.equals(keymap.getName());
  private final ArrayList<KeymapScheme> list = new ArrayList<>();

  /**
   * @param predicate a predicate to test a scheme
   * @return a first scheme that belongs to the specified predicate, or {@code null}
   */
  KeymapScheme find(@NotNull Predicate<KeymapScheme> predicate) {
    for (KeymapScheme scheme : list) {
      if (predicate.test(scheme)) return scheme;
    }
    return null;
  }

  /**
   * @param scheme a scheme to add into the list of schemes
   * @return the same scheme to select
   */
  KeymapScheme add(@NotNull KeymapScheme scheme) {
    list.add(scheme);
    return scheme;
  }

  /**
   * @param scheme a scheme to remove from the list of schemes
   * @return a scheme to select
   */
  KeymapScheme remove(@NotNull KeymapScheme scheme) {
    list.remove(scheme);
    return getSchemeToSelect(scheme.getParent());
  }

  /**
   * Initializes a list of schemes from loaded keymaps.
   *
   * @return a scheme to select
   * @see KeymapSelector#reset()
   */
  KeymapScheme reset() {
    list.clear();
    getKeymaps().forEach(keymap -> list.add(new KeymapScheme(keymap)));
    return getSchemeToSelect(null);
  }

  /**
   * @param selected a selected scheme to activate corresponding keymap
   * @return the same scheme to select
   * @see KeymapSelector#apply()
   */
  KeymapScheme apply(KeymapScheme selected) {
    Keymap active = selected == null ? null : selected.getOriginal();
    List<Keymap> keymaps = list.stream().map(scheme -> scheme.apply()).collect(toList());
    KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    manager.setKeymaps(keymaps, active, FILTER);
    return selected;
  }

  /**
   * @return a list of loaded keymaps
   */
  @NotNull
  private static List<Keymap> getKeymaps() {
    KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    return manager.getKeymaps(FILTER);
  }

  /**
   * @param active a keymap or {@code null} if the current active keymap should be used
   * @return a scheme to select according to the specified keymap
   */
  private KeymapScheme getSchemeToSelect(Keymap active) {
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

  /**
   * @param selected a selected scheme to test
   * @return {@code true} if the current list of schemes differs from the list of loaded keymaps
   * @see KeymapSelector#isModified()
   */
  boolean isModified(KeymapScheme selected) {
    Keymap active = selected == null ? null : selected.getOriginal();
    if (!Objects.equals(active, KeymapManager.getInstance().getActiveKeymap())) return true;

    Iterator<Keymap> keymaps = getKeymaps().iterator();
    Iterator<KeymapScheme> schemes = this.list.iterator();
    while (keymaps.hasNext() && schemes.hasNext()) {
      if (!Objects.equals(keymaps.next(), schemes.next().getCurrent())) return true;
    }
    return keymaps.hasNext() || schemes.hasNext();
  }

  List<KeymapScheme> getSchemes(boolean sorted) {
    if (sorted) list.sort(COMPARATOR);
    return list;
  }

  static final Comparator<KeymapScheme> COMPARATOR = (scheme1, scheme2) -> {
    if (scheme1 == scheme2) return 0;
    if (scheme1 == null) return -1;
    if (scheme2 == null) return 1;

    Keymap keymap1 = scheme1.getCurrent();
    Keymap keymap2 = scheme2.getCurrent();

    Keymap parent1 = scheme1.getParent();
    Keymap parent2 = scheme2.getParent();

    if (parent1 == null) parent1 = keymap1;
    if (parent2 == null) parent2 = keymap2;

    if (parent1 == parent2) {
      if (!keymap1.canModify()) return -1;
      if (!keymap2.canModify()) return 1;

      return naturalCompare(keymap1.getPresentableName(), keymap2.getPresentableName());
    }
    else {
      return naturalCompare(parent1.getPresentableName(), parent2.getPresentableName());
    }
  };
}
