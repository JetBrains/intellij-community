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

import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.deepEquals;

/**
 * @author Sergey.Malenkov
 */
final class KeymapScheme implements Scheme {
  private final KeymapImpl original;
  private KeymapImpl mutable;

  KeymapScheme(@NotNull Keymap keymap) {
    original = (KeymapImpl)keymap;
  }

  @NotNull
  @Override
  public String getName() {
    return getCurrent().getPresentableName();
  }

  void setName(@NotNull String name) {
    getMutable().setName(name);
  }

  Keymap getParent() {
    return !isMutable() ? null : original.getParent();
  }

  @NotNull
  Keymap getOriginal() {
    return original;
  }

  @NotNull
  KeymapImpl getCurrent() {
    return mutable != null ? mutable : original;
  }

  @NotNull
  KeymapImpl getMutable() {
    if (mutable != null) return mutable;
    assert isMutable() : "create a mutable copy for immutable keymap";
    mutable = original.copyTo(new KeymapImpl());
    return mutable;
  }

  boolean isMutable() {
    return original.canModify();
  }

  boolean contains(@NotNull Keymap keymap) {
    return keymap == original || keymap == mutable;
  }

  private static boolean contains(@NotNull Keymap keymap, @NotNull String name) {
    return name.equals(keymap.getName()) || name.equals(keymap.getPresentableName());
  }

  boolean contains(@NotNull String name) {
    return contains(original, name) || mutable != null && contains(mutable, name);
  }

  boolean canReset() {
    return getCurrent().getOwnActionIds().length > 0;
  }

  void reset() {
    assert canReset() : "reset all modified shortcuts unexpectedly";
    getMutable().clearOwnActionsIds();
  }

  boolean canReset(@NotNull String actionId) {
    return getCurrent().hasOwnActionId(actionId);
  }

  void reset(@NotNull String actionId) {
    assert canReset(actionId) : "reset modified action shortcuts unexpectedly";
    getMutable().clearOwnActionsId(actionId);
  }

  @NotNull
  Keymap apply() {
    if (mutable != null) mutable.copyTo(original);
    mutable = null;
    return original;
  }

  @NotNull
  KeymapScheme copy(@NotNull String name) {
    return new KeymapScheme(getCurrent().deriveKeymap(name));
  }
}
