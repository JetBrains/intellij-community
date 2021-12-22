// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments;

import com.intellij.diff.util.ThreeSide;
import org.jetbrains.annotations.NotNull;

public interface MergeWordFragment {
  int getStartOffset(@NotNull ThreeSide side);

  int getEndOffset(@NotNull ThreeSide side);
}
