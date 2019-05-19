// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import org.jetbrains.annotations.NotNull;

public class FocusRegion extends RangeMarkerImpl {
  private final Editor myEditor;

  FocusRegion(@NotNull Editor editor, int start, int end) {
    super((DocumentEx)editor.getDocument(), start, end, false, false);
    this.myEditor = editor;
  }

  public Editor getEditor() {
    return myEditor;
  }
}
