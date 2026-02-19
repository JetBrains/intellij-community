// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus
import java.awt.Paint
import java.awt.Rectangle
import javax.swing.JComponent

@ApiStatus.Internal
open class AiInternalUiComponentMarker {
  companion object {
    @JvmStatic
    protected val AI_COMPONENT: Key<Boolean> = Key.create("AI_COMPONENT")
  }

  open fun isAiComponent(c: JComponent): Boolean = false

  open fun markAiComponent(c: JComponent, isAI: Boolean) {}
  open fun markAiBalloonComponent(content: JComponent) {}
  open fun isAiBalloonComponent(content: JComponent): Boolean = false

  open fun markAiComponent(dataHolder: UserDataHolder) {
    dataHolder.putUserData(AI_COMPONENT, true)
  }

  open fun markAiComponent(c: JComponent, dataHolder: UserDataHolder?) {
    markAiComponent(c, dataHolder?.getUserData(AI_COMPONENT) ?: false)
  }

  open fun markAiContainerFor(c: JComponent): Unit = Unit

  open fun unmarkAiContainerFor(c: JComponent): Unit = Unit

  open fun getCustomDefaultButtonFillPaint(c: JComponent, r: Rectangle, defaultPaint: Paint?): Paint? = defaultPaint
  open fun getCustomDefaultBorderPaint(c: JComponent, r: Rectangle, defaultPaint: Paint?): Paint? = defaultPaint
  open fun getCustomFocusPaint(c: JComponent, r: Rectangle, defaultPaint: Paint?): Paint? = defaultPaint
  open fun getCustomButtonFillPaint(c: JComponent, r: Rectangle, defaultPaint: Paint?): Paint? = defaultPaint
  open fun getCustomButtonBorderPaint(c: JComponent, r: Rectangle, defaultPaint: Paint?): Paint? = defaultPaint

  @ApiStatus.Experimental
  @ApiStatus.Internal
  interface AIAction
}