// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.TextEditor;
import org.jetbrains.annotations.NotNull;

public interface TextEditorCustomizer {
  ExtensionPointName<TextEditorCustomizer> EP =
    new ExtensionPointName<>("com.intellij.textEditorCustomizer");

  /**
   * Use to customize editor after it was created
   */
  void customize(@NotNull TextEditor textEditor);
}