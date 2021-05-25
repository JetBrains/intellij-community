// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.kotlin.dsl;

import org.gradle.tooling.model.kotlin.dsl.EditorPosition;

import java.io.Serializable;

public class InternalEditorPosition implements EditorPosition, Serializable {
  private final int myLine;
  private final int myColumn;

  public InternalEditorPosition(int line, int column) {
    myLine = line;
    myColumn = column;
  }

  @Override
  public int getLine() {
    return myLine;
  }

  @Override
  public int getColumn() {
    return myColumn;
  }
}
