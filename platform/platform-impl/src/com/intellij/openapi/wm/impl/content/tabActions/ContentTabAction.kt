// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content.tabActions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.ui.popup.ActiveIcon
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.content.Content

abstract class ContentTabAction(val icon: ActiveIcon) {
  /**
   * Whether the icon is painted before or after the tab text
   */
  open val afterText: Boolean = true

  /**
   * Whether the action is visible on a tab label
   */
  abstract val available: Boolean
  abstract fun runAction()

  @get:NlsContexts.Tooltip
  open val tooltip: String? = null
}

/**
 * Allows to add clickable icons to the tab labels of Tool Window contents
 */
interface ContentTabActionProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<ContentTabActionProvider> = ExtensionPointName.create("com.intellij.contentTabActionProvider")
  }

  fun createTabActions(content: Content): List<ContentTabAction>
}
