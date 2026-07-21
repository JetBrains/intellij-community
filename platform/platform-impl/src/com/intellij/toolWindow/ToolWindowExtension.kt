// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ToolWindowExtension {

  companion object {
    val EP_NAME: ExtensionPointName<ToolWindowExtension> = ExtensionPointName.create("com.intellij.toolWindowExtension")

    @JvmStatic
    fun getInstance(): ToolWindowExtension? {
      return if (ExperimentalUI.isNewUI()) EP_NAME.extensionList.firstOrNull() else null
    }
  }

  fun isStripeResizable(): Boolean

  fun isToolWindowNameVisible(): Boolean

  fun getStripeIconUnscaledSize(): Int

  fun getStripeButtonUnscaledSize(): Int

  fun createSquareStripeButtonLook(button: SquareStripeButton): SquareStripeButtonLook
}
