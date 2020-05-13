// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.LineNumberConverter;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter that should help in migration from {@link EditorGutterComponentEx#setLineNumberConvertor(TIntFunction)} and
 * {@link EditorGutterComponentEx#setLineNumberConvertor(TIntFunction, TIntFunction)} to
 * {@link EditorGutter#setLineNumberConverter(LineNumberConverter)} and
 * {@link EditorGutter#setLineNumberConverter(LineNumberConverter, LineNumberConverter)}
 */
@SuppressWarnings("deprecation")
public class LineNumberConverterAdapter implements LineNumberConverter {
  private final TIntFunction myFunction;

  public LineNumberConverterAdapter(@NotNull TIntFunction function) {
    myFunction = function;
  }

  @Override
  public Integer convert(@NotNull Editor editor, int lineNumber) {
    int result = myFunction.execute(lineNumber - 1);
    return result < 0 ? null : result + 1;
  }

  @Override
  public Integer getMaxLineNumber(@NotNull Editor editor) {
    for (int i = editor.getDocument().getLineCount(); i > 0; i--) {
      int number = myFunction.execute(i - 1);
      if (number >= 0) {
        return number + 1;
      }
    }
    return 0;
  }
}
