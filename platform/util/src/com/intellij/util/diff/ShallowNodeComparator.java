// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.diff;

import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public interface ShallowNodeComparator<OT, NT> {
  @NotNull
  ThreeState deepEqual(@NotNull OT oldNode, @NotNull NT newNode);
  boolean typesEqual(@NotNull OT oldNode, @NotNull NT newNode);
  boolean hashCodesEqual(@NotNull OT oldNode, @NotNull NT newNode);
}
