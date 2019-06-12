// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.keymap.impl.KeymapManagerImplKt;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

/**
 * This class operates with the KeymapManager.
 *
 * @author Sergey.Malenkov
 */
public final class KeymapSchemeManager extends AbstractSchemeActions<KeymapScheme> implements SchemesModel<KeymapScheme> {
  public static final Predicate<Keymap> FILTER = keymap -> !SystemInfo.isMac || !KeymapManager.DEFAULT_IDEA_KEYMAP.equals(keymap.getName());

  private final List<KeymapScheme> list = new ArrayList<>();
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

    String name = KeyMapBundle.message("new.keymap.name", keymap.getPresentableName());
    for (int i = 1; containsScheme(name, false); i++) {
      name = KeyMapBundle.message("new.indexed.keymap.name", keymap.getPresentableName(), i);
    }
    return copyScheme(scheme, name).getMutable();
  }

  void visitMutableKeymaps(Consumer<? super Keymap> consumer) {
    for (KeymapScheme scheme : list) {
      if (scheme.isMutable()) {
        consumer.accept(scheme.getMutable());
      }
    }
  }

  @NotNull
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
  private KeymapScheme find(@NotNull Predicate<? super KeymapScheme> predicate) {
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
        return KeyMapBundle.message("configuration.all.keymaps.should.have.non.empty.names.error.message");
      }
      if (!set.add(name)) {
        return KeyMapBundle.message("configuration.all.keymaps.should.have.unique.names.error.message");
      }
    }
    KeymapScheme selected = selector.getSelectedScheme();
    Keymap active = selected == null ? null : selected.getOriginal();
    List<Keymap> keymaps = ContainerUtil.map(list, scheme -> scheme.apply());
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
    return ((KeymapManagerImpl)KeymapManager.getInstance()).getKeymaps(FILTER);
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

    Iterator<Keymap> keymaps = getKeymaps().stream().sorted(KeymapManagerImplKt.getKeymapComparator()).iterator();
    Iterator<KeymapScheme> schemes = list.iterator();
    while (keymaps.hasNext() && schemes.hasNext()) {
      if (!Objects.equals(keymaps.next(), schemes.next().getCurrent())) return true;
    }
    return keymaps.hasNext() || schemes.hasNext();
  }

  List<KeymapScheme> getSchemes() {
    list.sort(SCHEME_COMPARATOR);
    return list;
  }

  private static final Comparator<KeymapScheme> SCHEME_COMPARATOR = (scheme1, scheme2) -> {
    if (scheme1 == scheme2) return 0;
    if (scheme1 == null) return -1;
    if (scheme2 == null) return 1;
    return KeymapManagerImplKt.getKeymapComparator().compare(scheme1.getCurrent(), scheme2.getCurrent());
  };
}
