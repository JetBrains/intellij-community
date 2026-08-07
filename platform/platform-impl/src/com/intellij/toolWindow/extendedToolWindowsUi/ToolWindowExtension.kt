// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.extendedToolWindowsUi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.openapi.wm.impl.ToolWindowAnchorEnum
import com.intellij.toolWindow.ToolWindowToolbar
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Insets

@ApiStatus.Internal
interface ToolWindowExtension {

  companion object {
    val EP_NAME: ExtensionPointName<ToolWindowExtension> = ExtensionPointName.create("com.intellij.toolWindowExtension")

    @JvmStatic
    fun getInstance(): ToolWindowExtension? {
      return if (ExperimentalUI.isNewUI()) EP_NAME.extensionList.firstOrNull() else null
    }

    /**
     * The extension requires restart, so cache the value
     */
    @JvmStatic
    @get:JvmName("exists")
    val exists: Boolean by lazy { getInstance() != null }
  }

  fun isStripeResizable(): Boolean

  fun isToolWindowNameVisible(): Boolean

  fun getStripeIconUnscaledSize(): Int

  fun createSquareStripeButtonLook(button: SquareStripeButton): SquareStripeButtonLook

  fun getIconPadding(toolbarAnchor: ToolWindowAnchorEnum): Insets

  fun getButtonMinSize(moreButton: Boolean): Dimension

  fun createTopToolWindowToolbar(paneId: String, isPrimary: Boolean): ToolWindowToolbar? {
    return ToolWindowHorizontalToolbar(paneId, ToolWindowAnchor.TOP, isPrimary)
  }

  fun createBottomToolWindowToolbar(paneId: String, isPrimary: Boolean): ToolWindowToolbar? {
    return ToolWindowHorizontalToolbar(paneId, ToolWindowAnchor.BOTTOM, isPrimary)
  }
}
