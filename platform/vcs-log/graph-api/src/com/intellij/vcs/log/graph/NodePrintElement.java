// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface NodePrintElement extends PrintElement {
  @ApiStatus.Experimental
  default @NotNull NodePrintElement.Type getNodeType() {
    return Type.FILL;
  }

  @ApiStatus.Experimental
  enum Type {
    FILL, OUTLINE, OUTLINE_AND_FILL
  }
}
