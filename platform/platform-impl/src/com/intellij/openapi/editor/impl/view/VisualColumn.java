// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class VisualColumn {
  public int column; // visual column
  public final boolean leansRight; // true if target location is closer to larger columns and false otherwise

  public VisualColumn(int column, boolean leansRight) {
    this.column = column;
    this.leansRight = leansRight;
  }
}
