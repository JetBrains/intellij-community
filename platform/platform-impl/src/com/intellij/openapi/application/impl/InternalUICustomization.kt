// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.fileEditor.impl.EditorTabPainterAdapter
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Splittable
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
open class InternalUICustomization {
  companion object{
    @JvmStatic
    fun getInstance(): InternalUICustomization = service()
    @JvmStatic
    fun getInstanceOrNull(): InternalUICustomization? = serviceOrNull()
  }

  open val componentMarker: InternalUiComponentMarker = InternalUiComponentMarker()

  open val editorTabPainterAdapter: TabPainterAdapter = EditorTabPainterAdapter()

  open val isProjectCustomDecorationActive: Boolean = true

  /**
   * TODO
   * in the case of singleStripe, it is necessary to remove or recycle all actions related to the statusbar.
   * in the menu - appearance too
   */
  open fun isSingleStripe(): Boolean = false

  internal val internalCustomizer: StripesUxCustomizer = if(isSingleStripe())
    XNextStripesUxCustomizer()
  else
    StripesUxCustomizer ()

  open fun frameHeaderBackgroundConverter(color: Color?): Color? = color

  open fun transformGraphics(component: JComponent, graphics: Graphics): Graphics = graphics

  open fun createCustomDivider(isVertical: Boolean, splitter: Splittable): Divider? = null

  open fun attachBackgroundGradient(component: JComponent, disposable: Disposable): Unit = Unit

  open fun getToolWindowsPaneThreeSplitterBackground(): Color = JBColor.GRAY

  open fun getCustomDefaultFillPaint(c: JComponent, r: Rectangle): Paint? {
    return componentMarker.getCustomDefaultFillPaint(c, r)
  }

  open fun getMainToolbarBackground(active: Boolean): Color {
    return JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(active)
  }

  open fun getCustomMainBackgroundColor(): Color? = null

  fun statusBarRequired(): Boolean = !isSingleStripe()
}