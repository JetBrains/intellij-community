// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.LineNumberConverter;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntUnaryOperator;

/**
 * Adapter for migrating from obsolete removed API
 * {@code EditorGutterComponentEx#setLineNumberConvertor(TIntFunction lineNumberConvertor)} to
 * {@link EditorGutter#setLineNumberConverter(LineNumberConverter)} and
 * {@link EditorGutter#setLineNumberConverter(LineNumberConverter, LineNumberConverter)}
 */
public final class LineNumberConverterAdapter implements LineNumberConverter {
  private final IntUnaryOperator myFunction;

  public LineNumberConverterAdapter(@NotNull IntUnaryOperator function) {
    myFunction = function;
  }

  @Override
  public Integer convert(@NotNull Editor editor, int lineNumber) {
    int result = myFunction.applyAsInt(lineNumber - 1);
    return result < 0 ? null : result + 1;
  }

  @Override
  public Integer getMaxLineNumber(@NotNull Editor editor) {
    for (int i = editor.getDocument().getLineCount(); i > 0; i--) {
      int number = myFunction.applyAsInt(i - 1);
      if (number >= 0) {
        return number + 1;
      }
    }
    return 0;
  }
}
