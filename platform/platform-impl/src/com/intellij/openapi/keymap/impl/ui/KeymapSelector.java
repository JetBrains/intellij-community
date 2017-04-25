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
import com.intellij.application.options.schemes.SimpleSchemesPanel;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

import static com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.updateAllToolbarsImmediately;
import static com.intellij.openapi.keymap.KeyMapBundle.message;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

/**
 * @author Sergey.Malenkov
 */
final class KeymapSelector extends SimpleSchemesPanel<Keymap> implements SchemesModel<Keymap> {
  private final IdentityHashMap<Keymap, Keymap> mapMutableToKeymap = new IdentityHashMap<>();
  private final IdentityHashMap<Keymap, Keymap> mapKeymapToMutable = new IdentityHashMap<>();
  private final ArrayList<Keymap> keymaps = new ArrayList<>();
  private final Consumer<Keymap> consumer;
  private String messageReplacement;
  private boolean messageShown;
  private boolean internal;

  KeymapSelector(Consumer<Keymap> consumer) {
    this.consumer = consumer;
  }

  void reset() {
    mapMutableToKeymap.clear();
    mapKeymapToMutable.clear();
    keymaps.clear();
    keymaps.addAll(Keymaps.getAll());
    selectKeymap(Keymaps.getActiveFrom(keymaps), true);
  }

  String apply() {
    HashSet<String> set = new HashSet<>(keymaps.size());
    for (Keymap keymap : keymaps) {
      String name = keymap.getName();
      if (isEmptyOrSpaces(name)) {
        return message("configuration.all.keymaps.should.have.non.empty.names.error.message");
      }
      if (!set.add(name)) {
        return message("configuration.all.keymaps.should.have.unique.names.error.message");
      }
    }
    mapMutableToKeymap.forEach((mutable, keymap) -> ((KeymapImpl)mutable).copyTo((KeymapImpl)keymap));
    Keymaps.apply(keymaps, getSelectedScheme());
    notifyConsumer(getSelectedKeymap());
    updateAllToolbarsImmediately();
    return null;
  }

  boolean isModified() {
    if (!Keymaps.isActive(getSelectedScheme())) return true;

    Iterator<Keymap> actual = keymaps.iterator();
    Iterator<Keymap> expected = Keymaps.getAll().iterator();
    while (actual.hasNext() && expected.hasNext()) {
      Keymap keymap = getActualKeymap(actual.next(), false);
      if (!Objects.equals(keymap, expected.next())) return true;
    }
    return actual.hasNext() || expected.hasNext();
  }

  void visitMutableKeymaps(Consumer<Keymap> consumer) {
    for (Keymap keymap : keymaps) {
      if (keymap.canModify()) {
        consumer.accept(getActualKeymap(keymap, true));
      }
    }
  }

  Keymap getSelectedKeymap() {
    Keymap keymap = getSelectedScheme();
    if (keymap == null) return null;
    return getActualKeymap(keymap, false);
  }

  Keymap getOriginalKeymap(Keymap mutable) {
    return mapMutableToKeymap.get(mutable);
  }

  Keymap getMutableKeymap(Keymap keymap) {
    if (keymap == null) return null;
    if (keymap.canModify()) return getActualKeymap(keymap, true);

    String name = message("new.keymap.name", keymap.getPresentableName());
    //noinspection ForLoopThatDoesntUseLoopVariable
    for (int i = 1; containsScheme(name, false); i++) {
      name = message("new.indexed.keymap.name", keymap.getPresentableName(), i);
    }
    return getActualKeymap(addScheme(keymap, name), true);
  }

  boolean canResetActionInKeymap(Keymap mutable, String actionId) {
    Keymap keymap = mapMutableToKeymap.get(mutable);
    if (keymap == null) return false;

    Shortcut[] original = keymap.getShortcuts(actionId);
    Shortcut[] current = mutable.getShortcuts(actionId);
    return !Objects.deepEquals(original, current);
  }

  void resetActionInKeymap(Keymap mutable, String actionId) {
    Keymap keymap = mapMutableToKeymap.get(mutable);
    if (keymap == null) return;

    mutable.removeAllActionShortcuts(actionId);
    for (Shortcut shortcut : keymap.getShortcuts(actionId)) {
      mutable.addShortcut(actionId, shortcut);
    }
  }

  private Keymap getActualKeymap(Keymap keymap, boolean create) {
    if (mapMutableToKeymap.containsKey(keymap)) return keymap;

    Keymap mutable = mapKeymapToMutable.get(keymap);
    if (mutable != null) return mutable;
    if (!create) return keymap;

    mutable = ((KeymapImpl)keymap).copyTo(new KeymapImpl());
    mapKeymapToMutable.put(keymap, mutable);
    mapMutableToKeymap.put(mutable, keymap);
    return mutable;
  }

  @NotNull
  @Override
  public SchemesModel<Keymap> getModel() {
    return this;
  }

  @Override
  protected String getSchemeTypeName() {
    return "Keymap";
  }

  @Override
  protected AbstractSchemeActions<Keymap> createSchemeActions() {
    return new AbstractSchemeActions<Keymap>(this) {
      @Override
      protected Class<Keymap> getSchemeType() {
        return Keymap.class;
      }

      @Override
      protected void onSchemeChanged(@Nullable Keymap keymap) {
        if (!internal) notifyConsumer(keymap);
      }

      @Override
      protected void resetScheme(@NotNull Keymap keymap) {
        Keymap mutable = mapKeymapToMutable.remove(keymap);
        if (mutable != null) mapMutableToKeymap.remove(mutable);
        selectKeymap(keymap, false);
      }

      @Override
      protected void renameScheme(@NotNull Keymap keymap, @NotNull String name) {
        setKeymapName(name, keymap);
        setKeymapName(name, mapKeymapToMutable.get(keymap));
        selectKeymap(keymap, false);
      }

      @Override
      protected void duplicateScheme(@NotNull Keymap parent, @NotNull String name) {
        addScheme(parent, name);
      }
    };
  }

  @Override
  public void removeScheme(@NotNull Keymap keymap) {
    Keymap mutable = mapKeymapToMutable.remove(keymap);
    if (mutable != null) mapMutableToKeymap.remove(mutable);
    keymaps.remove(keymap);
    Keymap parent = keymap.getParent(); // choose a parent keymap or an active one
    selectKeymap(parent != null ? parent : Keymaps.getActiveFrom(keymaps), true);
  }

  private Keymap addScheme(@NotNull Keymap parent, @NotNull String name) {
    Keymap keymap = parent.deriveKeymap(name);
    keymaps.add(keymap);
    selectKeymap(keymap, true);
    return keymap;
  }

  @Override
  public boolean canResetScheme(@NotNull Keymap keymap) {
    return keymap.canModify();
  }

  @Override
  public boolean canRenameScheme(@NotNull Keymap keymap) {
    return keymap.canModify();
  }

  @Override
  public boolean canDuplicateScheme(@NotNull Keymap keymap) {
    return true;
  }

  @Override
  public boolean canDeleteScheme(@NotNull Keymap keymap) {
    return keymap.canModify();
  }

  @Override
  public boolean containsScheme(@NotNull String name, boolean isProjectScheme) {
    return keymaps.stream().anyMatch(keymap -> keymap.getName().equals(name));
  }

  @Override
  public boolean isProjectScheme(@NotNull Keymap keymap) {
    return false;
  }

  @Override
  protected boolean supportsProjectSchemes() {
    return false;
  }

  @Override
  protected boolean highlightNonDefaultSchemes() {
    return true;
  }

  @Override
  public boolean useBoldForNonRemovableSchemes() {
    return true;
  }

  @Override
  public boolean differsFromDefault(@NotNull Keymap keymap) {
    Keymap mutable = mapKeymapToMutable.get(keymap);
    return mutable != null && !mutable.equals(keymap);
  }

  @Override
  public void showMessage(@Nullable String message, @NotNull MessageType messageType) {
    messageShown = true;
    super.showMessage(message, messageType);
  }

  @Override
  public void clearMessage() {
    messageShown = false;
    super.showMessage(messageReplacement, MessageType.INFO);
  }

  private static void setKeymapName(String name, Keymap keymap) {
    ((KeymapImpl)keymap).setName(name);
  }

  private static String getDescription(Keymap keymap) {
    if (keymap == null || !keymap.canModify()) return null;
    Keymap parent = keymap.getParent();
    return parent == null ? null : message("based.on.keymap.label", parent.getPresentableName());
  }

  private void notifyConsumer(Keymap keymap) {
    messageReplacement = getDescription(keymap);
    if (!messageShown) clearMessage();

    consumer.accept(getActualKeymap(keymap, false));
  }

  private void selectKeymap(Keymap keymap, boolean reset) {
    try {
      internal = true;
      if (reset) resetSchemes(keymaps);
      if (keymap != null) selectScheme(keymap);
    }
    finally {
      internal = false;
      notifyConsumer(getSelectedScheme());
    }
  }
}
