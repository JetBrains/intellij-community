// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.extendedToolWindowsUi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.toolWindow.ToolWindowToolbar
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension

@ApiStatus.Experimental
interface ToolWindowStripeExtension {

  companion object {
    val EP_NAME: ExtensionPointName<ToolWindowStripeExtension> = ExtensionPointName.create("com.intellij.toolWindowStripeExtension")

    const val ICON_UNSCALED_SIZE: Int = 16

    @JvmStatic
    fun getInstance(): ToolWindowStripeExtension? {
      return if (ExperimentalUI.isNewUI()) EP_NAME.extensionList.firstOrNull() else null
    }

    /**
     * The extension requires restart, so cache the value
     */
    @JvmStatic
    @get:JvmName("exists")
    val exists: Boolean by lazy { getInstance() != null }

    internal fun createSquareStripeButtonLook(button: SquareStripeButton): SquareStripeButtonLook {
      return SquareStripeButtonLookVerticalText(button)
    }

    internal fun createTopToolWindowToolbar(paneId: String, isPrimary: Boolean): ToolWindowToolbar {
      return ToolWindowHorizontalToolbar(paneId, ToolWindowAnchor.TOP, isPrimary)
    }

    internal fun createBottomToolWindowToolbar(paneId: String, isPrimary: Boolean): ToolWindowToolbar {
      return ToolWindowHorizontalToolbar(paneId, ToolWindowAnchor.BOTTOM, isPrimary)
    }
  }

  fun isStripeResizable(): Boolean

  fun isToolWindowNameVisible(): Boolean

  fun getStripeIconUnscaledSize(): Int

  fun getIconPadding(toolbarAnchor: ToolWindowAnchor): JBInsets

  fun getButtonMinSize(moreButton: Boolean): Dimension

}
