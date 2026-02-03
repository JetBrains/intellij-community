// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.options.Scheme;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents a keymap as a mutable or immutable scheme.
 */
final class KeymapScheme implements Scheme {
  private final KeymapImpl original;
  private KeymapImpl mutable;

  /**
   * @param keymap a loaded keymap
   */
  KeymapScheme(@NotNull Keymap keymap) {
    original = (KeymapImpl)keymap;
  }

  /**
   * @return a current presentable name of a keymap
   */
  @Override
  public @NotNull String getName() {
    return getCurrent().getPresentableName();
  }

  /**
   * Renames a mutable copy, which is created on demand.
   *
   * @param name a new name to set
   */
  void setName(@NotNull String name) {
    getMutable().setName(name);
  }

  /**
   * @return a parent of the original keymap if it is mutable
   */
  Keymap getParent() {
    return !isMutable() ? null : original.getParent();
  }

  /**
   * @return a keymap to which this scheme belongs
   */
  @NotNull
  Keymap getOriginal() {
    return original;
  }

  /**
   * @return a current state of this scheme
   */
  @NotNull
  KeymapImpl getCurrent() {
    return mutable != null ? mutable : original;
  }

  /**
   * @return a mutable copy, which is created on demand
   */
  @NotNull KeymapImpl getMutable() {
    if (mutable != null) {
      return mutable;
    }
    assert isMutable() : "create a mutable copy for immutable keymap";
    mutable = original.copyTo(new KeymapImpl());
    return mutable;
  }

  /**
   * @return {@code true} if the original keymap can be modified
   */
  boolean isMutable() {
    return original.canModify();
  }

  /**
   * @param keymap a keymap to test
   * @return {@code true} if the specified keymap belongs to this scheme
   */
  boolean contains(@NotNull Keymap keymap) {
    return keymap == original || keymap == mutable;
  }

  private static boolean contains(@NotNull Keymap keymap, @NotNull String name) {
    return name.equals(keymap.getName()) || name.equals(keymap.getPresentableName());
  }

  /**
   * @param name a name to test
   * @return {@code true} if the specified name is used by this scheme
   */
  boolean contains(@NotNull String name) {
    return contains(original, name) || mutable != null && contains(mutable, name);
  }

  /**
   * @return {@code true} if the current state of this scheme differs from the parent keymap
   * @see #reset()
   */
  boolean canReset() {
    return isMutable() && getCurrent().getOwnActionIds().length > 0;
  }

  /**
   * Removes from the mutable copy all differences from the parent keymap.
   *
   * @see #canReset()
   */
  void reset() {
    assert canReset() : "reset all modified shortcuts unexpectedly";
    getMutable().clearOwnActionsIds();
  }

  /**
   * @param actionId an action identifier to test
   * @return {@code true} if the specified action differs from the same action in the parent keymap
   * @see #reset(String)
   */
  boolean canReset(@NotNull String actionId) {
    return isMutable() && getCurrent().hasOwnActionId(actionId);
  }

  /**
   * Removes from the mutable copy all differences for the specified action.
   *
   * @param actionId an action identifier to reset
   * @see #canReset(String)
   */
  void reset(@NotNull String actionId) {
    assert canReset(actionId) : "reset modified action shortcuts unexpectedly";
    getMutable().clearOwnActionsId(actionId);
  }

  /**
   * Applies all changes from the mutable copy to the original one.
   *
   * @return a keymap to which this scheme belongs
   */
  @NotNull
  Keymap apply() {
    if (mutable != null) {
      mutable.copyTo(original);
    }
    return original;
  }

  /**
   * @param name a new name to use
   * @return a new scheme created from this one
   */
  @NotNull
  KeymapScheme copy(@NotNull String name) {
    KeymapImpl keymap = original.deriveKeymap(name);
    if (mutable != null) {
      mutable.copyTo(keymap);
    }
    return new KeymapScheme(keymap);
  }
}
