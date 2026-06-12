// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

@ApiStatus.Internal
interface EditorEmptyTextPromotedActionProvider {
  fun getPromotedAction(splitters: JComponent): PromotedAction?

  data class PromotedAction(
    val actionId: String,
    @get:Nls val text: String,
  )

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorEmptyTextPromotedActionProvider> =
      ExtensionPointName.create("com.intellij.editorEmptyTextPromotedActionProvider")
  }
}
