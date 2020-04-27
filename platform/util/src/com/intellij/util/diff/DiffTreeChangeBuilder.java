// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.diff;

import org.jetbrains.annotations.NotNull;

public interface DiffTreeChangeBuilder<OT, NT> {
  void nodeReplaced(@NotNull OT oldChild, @NotNull NT newChild);
  void nodeDeleted(@NotNull OT oldParent, @NotNull OT oldNode);
  void nodeInserted(@NotNull OT oldParent, @NotNull NT newNode, int pos);
}
