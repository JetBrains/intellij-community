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
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.ui.MessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.function.Consumer;

import static com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.updateAllToolbarsImmediately;
import static com.intellij.openapi.keymap.KeyMapBundle.message;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

/**
 * @author Sergey.Malenkov
 */
final class KeymapSelector extends SimpleSchemesPanel<KeymapScheme> implements SchemesModel<KeymapScheme> {
  private final KeymapSchemeManager manager = new KeymapSchemeManager();
  private final Consumer<Keymap> consumer;
  private String messageReplacement;
  private boolean messageShown;
  private boolean internal;

  KeymapSelector(Consumer<Keymap> consumer) {
    this.consumer = consumer;
  }

  /**
   * @see KeymapPanel#reset()
   */
  void reset() {
    selectKeymap(manager.reset(), true);
  }

  /**
   * @see KeymapPanel#apply()
   */
  String apply() {
    HashSet<String> set = new HashSet<>();
    for (KeymapScheme scheme : manager.getSchemes(false)) {
      String name = scheme.getName();
      if (isEmptyOrSpaces(name)) {
        return message("configuration.all.keymaps.should.have.non.empty.names.error.message");
      }
      if (!set.add(name)) {
        return message("configuration.all.keymaps.should.have.unique.names.error.message");
      }
    }
    notifyConsumer(manager.apply(getSelectedScheme()));
    updateAllToolbarsImmediately();
    return null;
  }

  /**
   * @see KeymapPanel#isModified()
   */
  boolean isModified() {
    return manager.isModified(getSelectedScheme());
  }

  void visitMutableKeymaps(Consumer<Keymap> consumer) {
    for (KeymapScheme scheme : manager.getSchemes(false)) {
      if (scheme.isMutable()) {
        consumer.accept(scheme.getMutable());
      }
    }
  }

  Keymap getSelectedKeymap() {
    KeymapScheme scheme = getSelectedScheme();
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
    KeymapScheme copy = manager.add(scheme.copy(name));
    selectKeymap(copy, true);
    return copy.getMutable();
  }

  boolean canResetActionInKeymap(Keymap mutable, String actionId) {
    KeymapScheme scheme = find(mutable);
    return scheme != null && scheme.canReset(actionId);
  }

  void resetActionInKeymap(Keymap mutable, String actionId) {
    KeymapScheme scheme = find(mutable);
    if (scheme == null) return;
    scheme.reset(actionId);
    selectKeymap(scheme, false);
  }

  private KeymapScheme find(Keymap keymap) {
    return keymap == null ? null : manager.find(scheme -> scheme.contains(keymap));
  }

  @NotNull
  @Override
  public SchemesModel<KeymapScheme> getModel() {
    return this;
  }

  @Override
  protected String getSchemeTypeName() {
    return "Keymap";
  }

  @Override
  protected AbstractSchemeActions<KeymapScheme> createSchemeActions() {
    return new AbstractSchemeActions<KeymapScheme>(this) {
      @Override
      protected Class<KeymapScheme> getSchemeType() {
        return KeymapScheme.class;
      }

      @Override
      protected void onSchemeChanged(@Nullable KeymapScheme scheme) {
        if (!internal) notifyConsumer(scheme);
      }

      @Override
      protected void resetScheme(@NotNull KeymapScheme scheme) {
        scheme.reset();
        selectKeymap(scheme, true);
      }

      @Override
      protected void renameScheme(@NotNull KeymapScheme scheme, @NotNull String name) {
        scheme.setName(name);
        selectKeymap(scheme, true);
      }

      @Override
      protected void duplicateScheme(@NotNull KeymapScheme parent, @NotNull String name) {
        selectKeymap(manager.add(parent.copy(name)), true);
      }
    };
  }

  @Override
  public void removeScheme(@NotNull KeymapScheme scheme) {
    selectKeymap(manager.remove(scheme), true);
  }

  @Override
  public boolean canResetScheme(@NotNull KeymapScheme scheme) {
    return scheme.isMutable();
  }

  @Override
  public boolean canRenameScheme(@NotNull KeymapScheme scheme) {
    return scheme.isMutable();
  }

  @Override
  public boolean canDuplicateScheme(@NotNull KeymapScheme scheme) {
    return true;
  }

  @Override
  public boolean canDeleteScheme(@NotNull KeymapScheme scheme) {
    return scheme.isMutable();
  }

  @Override
  public boolean containsScheme(@NotNull String name, boolean isProjectScheme) {
    return null != manager.find(scheme -> scheme.contains(name));
  }

  @Override
  public boolean isProjectScheme(@NotNull KeymapScheme scheme) {
    return false;
  }

  @Override
  public int getIndent(@NotNull KeymapScheme scheme) {
    return scheme.isMutable() ? 1 : 0;
  }

  @Override
  protected boolean supportsProjectSchemes() {
    return false;
  }

  @Override
  protected boolean highlightNonDefaultSchemes() {
    return false;
  }

  @Override
  public boolean useBoldForNonRemovableSchemes() {
    return true;
  }

  @Override
  public boolean differsFromDefault(@NotNull KeymapScheme scheme) {
    return scheme.canReset();
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

  private void notifyConsumer(KeymapScheme scheme) {
    Keymap keymap = scheme == null ? null : scheme.getParent();
    messageReplacement = keymap == null ? null : message("based.on.keymap.label", keymap.getPresentableName());
    if (!messageShown) clearMessage();

    consumer.accept(scheme == null ? null : scheme.getCurrent());
  }

  private void selectKeymap(KeymapScheme scheme, boolean reset) {
    try {
      internal = true;
      if (reset) resetSchemes(manager.getSchemes(true));
      if (scheme != null) selectScheme(scheme);
    }
    finally {
      internal = false;
      notifyConsumer(getSelectedScheme());
    }
  }
}
