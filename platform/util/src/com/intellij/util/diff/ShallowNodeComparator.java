// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.diff;

import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public interface ShallowNodeComparator<OldNode, NewNode> {
  @NotNull ThreeState deepEqual(@NotNull OldNode oldNode, @NotNull NewNode newNode);

  boolean typesEqual(@NotNull OldNode oldNode, @NotNull NewNode newNode);

  boolean hashCodesEqual(@NotNull OldNode oldNode, @NotNull NewNode newNode);
}
