// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.softwrap.SoftWrapEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.Nullable;


interface LineFragmentPainter {

  void paintBeforeLineStart(
    IterationState iterationState,
    TextAttributes attributes,
    @Nullable SoftWrapEx softWrap,
    int columnEnd,
    float xEnd,
    int y
  );

  void paint(
    VisualLineFragmentsIterator.Fragment fragment,
    int start,
    int end,
    TextAttributes attributes,
    boolean isSelection,
    float xStart,
    float xEnd,
    int y
  );

  void paintAfterLineEnd(
    IterationState iterationState,
    int columnStart,
    float x,
    int y
  );
}
