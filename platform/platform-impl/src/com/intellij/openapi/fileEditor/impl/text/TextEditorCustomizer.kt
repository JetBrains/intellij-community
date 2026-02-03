// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.fileEditor.TextEditor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.OverrideOnly

@ApiStatus.Internal
@OverrideOnly
interface TextEditorCustomizer {
  /**
   * Use to customize editor after it was created.
   * Executed inside a coroutine scope spanning from editor opening to editor closing (or plugin unloading).
   */
  suspend fun execute(textEditor: TextEditor) {
    @Suppress("DEPRECATION")
    customize(textEditor)
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Override execute(textEditor)")
  fun customize(textEditor: TextEditor) {
    throw AbstractMethodError("Implement customize(textEditor, coroutineScope)")
  }
}