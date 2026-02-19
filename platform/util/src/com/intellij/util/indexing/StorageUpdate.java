// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@FunctionalInterface
public interface StorageUpdate {

  /** @return true if an update was successfully applied, false otherwise */
  boolean update();


  // ================ helpers: =====================================

  /** Nothing to update, just return true (success) */
  StorageUpdate NOOP = new StorageUpdate() {
    @Override
    public boolean update() {
      return true;
    }

    @Override
    public String toString() {
      return "NO_OP";
    }
  };

  /**
   * Combines few updates into one.
   * <b>Order of updates is important</b>: updates are applied in that order, and also short-circuit evaluation stops on first
   * update that is failed (=returns false)
   *
   * @see CombinedStorageUpdate
   */
  static StorageUpdate combine(@NotNull StorageUpdate @NotNull ... updates) {
    return new CombinedStorageUpdate(updates);
  }

  /**
   * Combines few updates into one.<p>
   * Applies all the updates one-by-one, and return true if all them applied successfully.<p>
   * If one of updates is failed to apply (=returns false) => the process stops, no updates are applied after the failed one,
   * (=short-circuited evaluation) and combined update returns false from its {@link #update()} method.<p>
   * Above means that <b>order of updates is important</b>
   */
  @ApiStatus.Internal
  class CombinedStorageUpdate implements StorageUpdate {
    private final @NotNull StorageUpdate @NotNull [] updates;

    public CombinedStorageUpdate(@NotNull StorageUpdate @NotNull ... updates) {
      if (updates.length == 0) {
        throw new IllegalArgumentException("updates must not be empty");
      }
      this.updates = updates;
    }

    /** @return true if all updates applied successfully, false if any of updates failed (=returns false itself) */
    @Override
    public boolean update() {
      for (StorageUpdate update : updates) {
        boolean successful = update.update();
        if (!successful) {
          return false;// don't apply updates after the first update that is failed
        }
      }
      return true;
    }

    @Override
    public String toString() {
      return "CombinedUpdate" + Arrays.toString(updates);
    }
  }
}
