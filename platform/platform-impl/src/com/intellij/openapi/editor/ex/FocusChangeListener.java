// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.awt.event.FocusEvent;
import java.util.EventListener;

public interface FocusChangeListener extends EventListener {
  default void focusGained(@NotNull Editor editor) { }
  default void focusLost(@NotNull Editor editor) { }

  default void focusLost(@NotNull Editor editor, @NotNull FocusEvent event) {
    focusLost(editor);
  }
  default void focusGained(@NotNull Editor editor, @NotNull FocusEvent event) {
    focusGained(editor);
  }
}