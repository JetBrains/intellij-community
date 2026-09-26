// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.fileEditor.TextEditor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.OverrideOnly

@ApiStatus.Internal
@OverrideOnly
interface TextEditorCustomizer {
  /**
   * Use to customize editor after it was created.
   *
   * Must return quickly. Use [coroutineScope] for asynchronous work that should live until editor closing or plugin unloading.
   */
  fun customize(textEditor: TextEditor, coroutineScope: CoroutineScope)
}