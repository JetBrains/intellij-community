// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.view.EditorView;
import org.jetbrains.annotations.NotNull;

public final class EditorViewAccessor {
  @NotNull
  public static EditorView getView(@NotNull Editor editor) {
    return ((EditorImpl)editor).getView();
  }
}
