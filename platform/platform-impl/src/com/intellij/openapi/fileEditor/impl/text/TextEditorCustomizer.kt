// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.fileEditor.TextEditor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TextEditorCustomizer {
  /**
   * Use to customize editor after it was created
   */
  fun customize(textEditor: TextEditor)
}