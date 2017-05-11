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

import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.openapi.keymap.KeyMapBundle.message;
import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.util.text.StringUtil.naturalCompare;
import static java.util.stream.Collectors.toList;

/**
 * This class operates with the KeymapManager.
 *
 * @author Sergey.Malenkov
 */
final class KeymapSchemeManager extends AbstractSchemeActions<KeymapScheme> implements SchemesModel<KeymapScheme> {
  private static final Condition<Keymap> FILTER = keymap -> !isMac || !KeymapManager.DEFAULT_IDEA_KEYMAP.equals(keymap.getName());
  private final ArrayList<KeymapScheme> list = new ArrayList<>();
  private final KeymapSelector selector;

  KeymapSchemeManager(KeymapSelector selector) {
    super(selector);
    this.selector = selector;
  }

  Keymap getSelectedKeymap() {
    KeymapScheme scheme = selector.getSelectedScheme();
    return scheme == null ? null : scheme.getCurrent();
  }

  Keymap getMutableKeymap(Keymap keymap) {
    KeymapScheme scheme = find(keymap);
    if (scheme == null) return null;
    if (scheme.isMutable()) return scheme.getMutable();

    String name = message("new.keymap.name", keymap.getPresentableName());
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int i = 1; containsScheme(name, false); i++) {
      name = message("new.indexed.keymap.name", keymap.getPresentableName(), i);
    }
    return copyScheme(scheme, name).getMutable();
  }

  void visitMutableKeymaps(Consumer<Keymap> consumer) {
    for (KeymapScheme scheme : list) {
      if (scheme.isMutable()) {
        consumer.accept(scheme.getMutable());
      }
    }
  }

  @Override
  protected Class<KeymapScheme> getSchemeType() {
    return KeymapScheme.class;
  }

  @Override
  protected void onSchemeChanged(@Nullable KeymapScheme scheme) {
    selector.notifyConsumer(scheme);
  }

  @Override
  public boolean isProjectScheme(@NotNull KeymapScheme scheme) {
    return false;
  }

  @Override
  public boolean canDuplicateScheme(@NotNull KeymapScheme scheme) {
    return true;
  }

  @Override
  protected void duplicateScheme(@NotNull KeymapScheme parent, @NotNull String name) {
    copyScheme(parent, name);
  }

  @NotNull
  private KeymapScheme copyScheme(@NotNull KeymapScheme parent, @NotNull String name) {
    KeymapScheme scheme = parent.copy(name);
    list.add(scheme);
    selector.selectKeymap(scheme, true);
    return scheme;
  }

  @Override
  public boolean canDeleteScheme(@NotNull KeymapScheme scheme) {
    return scheme.isMutable();
  }

  @Override
  public void removeScheme(@NotNull KeymapScheme scheme) {
    list.remove(scheme);
    selector.selectKeymap(getSchemeToSelect(scheme.getParent()), true);
  }

  @Override
  public boolean canRenameScheme(@NotNull KeymapScheme scheme) {
    return scheme.isMutable();
  }

  @Override
  protected void renameScheme(@NotNull KeymapScheme scheme, @NotNull String name) {
    scheme.setName(name);
    selector.selectKeymap(scheme, true);
  }

  @Override
  public boolean containsScheme(@NotNull String name, boolean projectScheme) {
    return null != find(scheme -> scheme.contains(name));
  }

  @Override
  public boolean differsFromDefault(@NotNull KeymapScheme scheme) {
    return scheme.canReset();
  }

  @Override
  public boolean canResetScheme(@NotNull KeymapScheme scheme) {
    return scheme.isMutable();
  }

  @Override
  protected void resetScheme(@NotNull KeymapScheme scheme) {
    scheme.reset();
    selector.selectKeymap(scheme, true);
  }

  boolean canResetActionInKeymap(Keymap mutable, String actionId) {
    KeymapScheme scheme = find(mutable);
    return scheme != null && scheme.canReset(actionId);
  }

  void resetActionInKeymap(Keymap mutable, String actionId) {
    KeymapScheme scheme = find(mutable);
    if (scheme == null) return;
    scheme.reset(actionId);
    selector.selectKeymap(scheme, false);
  }

  private KeymapScheme find(Keymap keymap) {
    return keymap == null ? null : find(scheme -> scheme.contains(keymap));
  }

  /**
   * @param predicate a predicate to test a scheme
   * @return a first scheme that belongs to the specified predicate, or {@code null}
   */
  private KeymapScheme find(@NotNull Predicate<KeymapScheme> predicate) {
    for (KeymapScheme scheme : list) {
      if (predicate.test(scheme)) return scheme;
    }
    return null;
  }

  /**
   * Initializes a list of schemes from loaded keymaps.
   *
   * @see KeymapPanel#reset()
   */
  void reset() {
    list.clear();
    getKeymaps().forEach(keymap -> list.add(new KeymapScheme(keymap)));
    selector.selectKeymap(getSchemeToSelect(null), true);
  }

  /**
   * Applies a changes in the internal list of schemes.
   *
   * @return the error message if changes cannot be applied
   * @see KeymapPanel#apply()
   */
  String apply() {
    HashSet<String> set = new HashSet<>();
    for (KeymapScheme scheme : list) {
      String name = scheme.getName();
      if (isEmptyOrSpaces(name)) {
        return message("configuration.all.keymaps.should.have.non.empty.names.error.message");
      }
      if (!set.add(name)) {
        return message("configuration.all.keymaps.should.have.unique.names.error.message");
      }
    }
    KeymapScheme selected = selector.getSelectedScheme();
    Keymap active = selected == null ? null : selected.getOriginal();
    List<Keymap> keymaps = list.stream().map(scheme -> scheme.apply()).collect(toList());
    KeymapManagerImpl manager = (KeymapManagerImpl)KeymapManager.getInstance();
    manager.setKeymaps(keymaps, active, FILTER);
    selector.notifyConsumer(selected);
    return null;
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
   * @return {@code true} if the current list of schemes differs from the list of loaded keymaps
   * @see KeymapPanel#isModified()
   */
  boolean isModified() {
    KeymapScheme selected = selector.getSelectedScheme();
    Keymap active = selected == null ? null : selected.getOriginal();
    if (!Objects.equals(active, KeymapManager.getInstance().getActiveKeymap())) return true;

    Iterator<Keymap> keymaps = getKeymaps().stream().sorted(KEYMAP_COMPARATOR).iterator();
    Iterator<KeymapScheme> schemes = this.list.iterator();
    while (keymaps.hasNext() && schemes.hasNext()) {
      if (!Objects.equals(keymaps.next(), schemes.next().getCurrent())) return true;
    }
    return keymaps.hasNext() || schemes.hasNext();
  }

  List<KeymapScheme> getSchemes() {
    list.sort(SCHEME_COMPARATOR);
    return list;
  }

  private static final Comparator<Keymap> KEYMAP_COMPARATOR = (keymap1, keymap2) -> {
    if (keymap1 == keymap2) return 0;
    if (keymap1 == null) return -1;
    if (keymap2 == null) return 1;

    Keymap parent1 = !keymap1.canModify() ? null : keymap1.getParent();
    Keymap parent2 = !keymap2.canModify() ? null : keymap2.getParent();

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

  private static final Comparator<KeymapScheme> SCHEME_COMPARATOR = (scheme1, scheme2) -> {
    if (scheme1 == scheme2) return 0;
    if (scheme1 == null) return -1;
    if (scheme2 == null) return 1;
    return KEYMAP_COMPARATOR.compare(scheme1.getCurrent(), scheme2.getCurrent());
  };
}
