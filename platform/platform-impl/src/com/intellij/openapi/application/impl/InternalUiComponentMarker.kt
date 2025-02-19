// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus
import java.awt.Paint
import java.awt.Rectangle
import javax.swing.JComponent

@ApiStatus.Internal
open class InternalUiComponentMarker {
  companion object {
    @JvmStatic
    protected val AI_COMPONENT: Key<Boolean> = Key.create("AI_COMPONENT")
  }

  open fun isAIComponent(c: JComponent): Boolean = false

  open fun markAIComponent(c: JComponent, isAI: Boolean) {}

  open fun markAIComponent(dataHolder: UserDataHolder) {
    dataHolder.putUserData(AI_COMPONENT, true)
  }

  open fun markAIComponent(c: JComponent, dataHolder: UserDataHolder?) {
    markAIComponent(c, dataHolder?.getUserData(AI_COMPONENT) ?: false)
  }

  open fun markAiContainerFor(c: JComponent): Unit = Unit

  open fun unmarkAiContainerFor(c: JComponent): Unit = Unit

  open fun getCustomDefaultFillPaint(c: JComponent, r: Rectangle): Paint? = null
  open fun getCustomDefaultBorderPaint(c: JComponent, r: Rectangle): Paint? = null
  open fun getCustomFocusPaint(c: JComponent, r: Rectangle): Paint? = null
  open fun getCustomButtonFillPaint(c: JComponent, r: Rectangle): Paint? = null
  open fun getCustomButtonBorderPaint(c: JComponent, r: Rectangle): Paint? = null
}