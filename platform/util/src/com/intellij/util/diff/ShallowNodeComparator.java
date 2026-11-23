// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.diff;

import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public interface ShallowNodeComparator<OldNode, NewNode> {
  /**
   * Compares the old node and the new node, and their texts too.
   * The texts can be equal or different, because the caller does not know.
   */
  @NotNull ThreeState deepEqual(@NotNull OldNode oldNode, @NotNull NewNode newNode);

  /**
   * Same as {@link #deepEqual}, but the caller knows that the old node and the new node have the same text.
   * {@link DiffTree} calls it for a child whose range is equal in the old text and in the new text.
   * An implementation can then skip the compare of the texts, for example for a leaf.
   */
  default @NotNull ThreeState deepEqualWithEqualText(@NotNull OldNode oldNode, @NotNull NewNode newNode) {
    return deepEqual(oldNode, newNode);
  }

  boolean typesEqual(@NotNull OldNode oldNode, @NotNull NewNode newNode);

  boolean hashCodesEqual(@NotNull OldNode oldNode, @NotNull NewNode newNode);
}
