// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ChangeEditorFontSizeStrategy {
  companion object {
    @JvmField val KEY: Key<ChangeEditorFontSizeStrategy> = Key.create("ChangeEditorFontSizeStrategy")
  }
  var fontSize: Float
  val defaultFontSize: Float
  @get:NlsActions.ActionText val defaultFontSizeText: String
  val overridesChangeFontSizeActions: Boolean
}
