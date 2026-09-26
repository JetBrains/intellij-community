// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset

import com.intellij.openapi.extensions.ExtensionPointName
import java.net.URL

/**
 * Extension point for picking vector icons (e.g., Material Design Icons).
 */
interface BaseVectorIconPicker {
  data class Result(val name: String, val url: URL)

  fun pickIcon(): Result?
  fun getDefaultIconUrl(): URL?

  companion object {
    val EP_NAME: ExtensionPointName<BaseVectorIconPicker> =
      ExtensionPointName.create("com.intellij.compose.ide.plugin.resources.vectorIconPicker")

    fun getInstance(): BaseVectorIconPicker? = EP_NAME.extensionList.firstOrNull()
  }
}