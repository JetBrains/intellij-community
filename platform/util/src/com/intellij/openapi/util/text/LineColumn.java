// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import org.jetbrains.annotations.NotNull;

public final class LineColumn {
  public final int line;
  public final int column;

  private LineColumn(int line, int column) {
    this.line = line;
    this.column = column;
  }

  public static @NotNull LineColumn of(int line, int column) {
    return new LineColumn(line, column);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LineColumn other = (LineColumn)o;

    if (line != other.line) return false;
    if (column != other.column) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = line;
    result = 31 * result + column;
    return result;
  }

  @Override
  public String toString() {
    return "LineColumn{line=" + line + ", column=" + column + '}';
  }
}
