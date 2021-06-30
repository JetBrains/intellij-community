// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public class EditorScrollingPositionKeeperProvider {
  public EditorScrollingPositionKeeper createEditorScrollingPositionKeeper(@NotNull Editor editor) {
    return new EditorScrollingPositionKeeper(editor);
  }
}
