// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

final class UserDataBackedStacks<T extends UserDataHolder> {
  // Intentionally non-static. Each UserDataBackedStacks instance belongs to one UndoRedoStacksHolder and one holder kind.
  // Undo/redo holders and different project holders can share the same Document or VirtualFile, so they must not alias stacks.
  private final @NotNull Key<UndoRedoList<UndoableGroup>> stackInHolderKey = Key.create("STACK_IN_USER_DATA_HOLDER");
  private final @NotNull Collection<T> holders = new WeakList<>();

  @NotNull UndoRedoList<UndoableGroup> computeIfAbsentWeaklyTrackedStack(@NotNull T holder) {
    UndoRedoList<UndoableGroup> result = holder.getUserData(stackInHolderKey);
    if (result == null) {
      result = new UndoRedoList<>();
      holder.putUserData(stackInHolderKey, result);
      holders.add(holder);
    }
    return result;
  }

  void removeHolder(@NotNull T holder) {
    holders.remove(holder);
  }

  void clearStacks() {
    for (T holder : holders) {
      holder.putUserData(stackInHolderKey, null);
    }
    holders.clear();
  }

  void removeEmptyStacks() {
    Set<T> holdersToDrop = new HashSet<>();
    for (T holder : holders) {
      Collection<UndoableGroup> stack = holder.getUserData(stackInHolderKey);
      if (stack != null && stack.isEmpty()) {
        holder.putUserData(stackInHolderKey, null);
        holdersToDrop.add(holder);
      }
    }
    holders.removeAll(holdersToDrop);
  }

  @NotNull Collection<T> getHolders() {
    return holders;
  }
}
