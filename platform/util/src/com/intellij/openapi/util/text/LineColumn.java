// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.NotNull;

public final class LineColumn {
  public final int line;
  public final int column;

  private LineColumn(int line, int column) {
    this.line = line;
    this.column = column;
  }

  @NotNull
  public static LineColumn of(int line, int column) {
    return new LineColumn(line, column);
  }
}
