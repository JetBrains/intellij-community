// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.toolWindow.EnableStripeGroup
import com.intellij.toolWindow.StripesUxCustomizer
import com.intellij.toolWindow.xNext.XNextStripesUxCustomizer
import com.intellij.ui.JBColor
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Paint
import java.awt.Rectangle
import javax.swing.JComponent

@ApiStatus.Experimental
@ApiStatus.Internal
open class DesignProcessor {
  companion object{
    @JvmStatic
    fun getInstance(): DesignProcessor = ApplicationManager.getApplication().getService(DesignProcessor::class.java)
    @JvmStatic
    protected val AI_COMPONENT: Key<Boolean> = Key.create("AI_COMPONENT")
  }
  init {
    EnableStripeGroup.Companion.setSingleStripeEnabled(true)
  }

  open fun isAIComponent(c: JComponent): Boolean = false

  open fun markAIComponent(c: JComponent, isAI: Boolean) {}
  open fun markAIComponent(c: JComponent, dataHolder: UserDataHolder?) {
    markAIComponent(c, dataHolder?.getUserData(AI_COMPONENT) ?: false)
  }

  open fun markAiContainerFor(c: JComponent): Unit = Unit

  open fun unmarkAiContainerFor(c: JComponent): Unit = Unit

  open fun createEditorTabPainterAdapter(): TabPainterAdapter = EditorTabPainterAdapter()

  open fun isSingleStripe(): Boolean = false

  internal val internalCustomizer: StripesUxCustomizer = if(isSingleStripe())
    XNextStripesUxCustomizer()
  else
    StripesUxCustomizer ()

  open fun frameHeaderBackgroundConverter(color: Color?): Color? = color

  open fun transformGraphics(component: JComponent, graphics: Graphics): Graphics = graphics

  open fun createCustomDivider(isVertical: Boolean, splitter: Splittable): Divider? = null


  open fun getToolWindowsPaneThreeSplitterBackground(): Color = JBColor.GRAY

  open fun getCustomDefaultButtonPaint(c: JComponent, r: Rectangle): Paint? = null

  open fun getMainToolbarBackground(active: Boolean): Color {
    return JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(active)
  }

  open fun getCustomMainBackgroundColor(): Color? = null

  fun statusBarRequired(): Boolean = !isSingleStripe()
}