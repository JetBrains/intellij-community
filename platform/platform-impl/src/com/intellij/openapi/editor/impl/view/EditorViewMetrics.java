// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.ApiStatus;

/**
 * The font-derived metrics of an editor view.
 *
 * @param editorFontSize the editor font size of the colors scheme that the metrics were measured with
 */
@ApiStatus.Internal
public record EditorViewMetrics(
  int editorFontSize,
  float plainSpaceWidth,
  int lineHeight,
  int descent,
  int ascent,
  int charHeight,
  float maxCharWidth,
  int capHeight,
  int topOverhang,
  int bottomOverhang,
  int caretHeight,
  int caretTopOverhang
) {
  public static final EditorViewMetrics UNINITIALIZED = new EditorViewMetrics(
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
  );
}
