// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import org.jetbrains.annotations.NotNull;

public final class DiffUtil {
  private DiffUtil() {
  }

  public static boolean isDiffEditor(@NotNull Editor editor) {
    return editor.getEditorKind() == EditorKind.DIFF;
  }
}
