// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.diff;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FlyweightCapableTreeStructure<T> {
  @NotNull
  T getRoot();

  @Nullable
  T getParent(@NotNull T node);

  int getChildren(@NotNull T parent, @NotNull Ref<T[]> into);

  void disposeChildren(T[] nodes, int count);

  @NotNull
  CharSequence toString(@NotNull T node);

  int getStartOffset(@NotNull T node);
  int getEndOffset(@NotNull T node);

  /**
   * Returns the length of the text of {@code node}.
   * Override it if an offset is more expensive than a length, for example if an offset needs a walk up to the root.
   */
  default int getTextLength(@NotNull T node) {
    return getEndOffset(node) - getStartOffset(node);
  }
}
