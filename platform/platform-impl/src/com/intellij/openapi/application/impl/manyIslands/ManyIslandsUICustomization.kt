// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.manyIslands

import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.impl.ToolWindowUIDecorator
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.toolWindow.xNext.island.XNextIslandHolder
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.util.ui.JBSwingUtilities
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import javax.swing.JComponent

internal class ManyIslandsUICustomization : InternalUICustomization() {
  private val isManyIslandsEnabled: Boolean = Registry.`is`("idea.many.islands.enabled", false)
  private val isManyIslandsGradientEnabled: Boolean = Registry.`is`("idea.many.islands.gradient.enabled", false)

  override val isDefaultCustomization: Boolean = !isManyIslandsEnabled

  override val isProjectCustomDecorationGradientPaint: Boolean = !isManyIslandsEnabled || !isManyIslandsGradientEnabled

  override val shouldPaintEditorFadeout: Boolean = !isManyIslandsEnabled

  private val toolWindowDecorator = object : ToolWindowUIDecorator() {
    override fun decorateAndReturnHolder(divider: JComponent, child: JComponent): JComponent? {
      return XNextIslandHolder().apply {
        layout = BorderLayout()
        add(divider, BorderLayout.NORTH)
        add(child, BorderLayout.CENTER)

        ManyIslandsRoundedBorder.createToolWindowBorder(this, child)
        child.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
      }
    }
  }

  private val tabPainterAdapter = ManyIslandsTabPainterAdapter()

  override val toolWindowUIDecorator: ToolWindowUIDecorator
    get() {
      if (isManyIslandsEnabled) {
        return toolWindowDecorator
      }
      return super.toolWindowUIDecorator
    }

  override fun createCustomDivider(isVertical: Boolean, splitter: Splittable): Divider? {
    if (isManyIslandsEnabled) {
      return object : OnePixelDivider(isVertical, splitter) {
        override fun paint(g: Graphics) {
        }
      }
    }
    return null
  }

  override fun installEditorBackground(component: JComponent) {
    if (isManyIslandsEnabled) {
      ClientProperty.putRecursive(component, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  override fun configureEditorsSplitters(component: EditorsSplitters) {
    if (isManyIslandsEnabled) {
      ManyIslandsRoundedBorder.createEditorBorder(component)
    }
  }

  override val editorTabPainterAdapter: TabPainterAdapter
    get() {
      if (isManyIslandsEnabled) {
        return tabPainterAdapter
      }
      return super.editorTabPainterAdapter
    }

  override fun getCustomMainBackgroundColor(): Color? {
    if (isManyIslandsEnabled) {
      return JBColor.namedColor("MainWindow.background", JBColor.PanelBackground)
    }
    return super.getCustomMainBackgroundColor()
  }

  override fun attachIdeFrameBackgroundPainter(frame: IdeFrame, glassPane: IdeGlassPane) {
    if (isManyIslandsEnabled && isManyIslandsGradientEnabled && glassPane is IdeGlassPaneImpl) {
      glassPane.addFallbackBackgroundPainter(ManyIslandsGradientPainter(frame, getCustomMainBackgroundColor()!!))
    }
  }

  override fun configureButtonLook(look: ActionButtonLook, g: Graphics): Graphics? {
    if (isManyIslandsEnabled && isManyIslandsGradientEnabled && look is SquareStripeButtonLook) {
      return IdeBackgroundUtil.getOriginalGraphics(g)
    }
    return null
  }

  override fun transformGraphics(component: JComponent, graphics: Graphics): Graphics {
    if (isManyIslandsEnabled && isManyIslandsGradientEnabled) {
      return JBSwingUtilities.runGlobalCGTransform(component, graphics)
    }
    return graphics
  }

  override fun transformButtonGraphics(graphics: Graphics): Graphics {
    return preserveGraphics(graphics)
  }

  override fun preserveGraphics(graphics: Graphics): Graphics {
    if (isManyIslandsEnabled && isManyIslandsGradientEnabled) {
      return IdeBackgroundUtil.getOriginalGraphics(graphics)
    }
    return graphics
  }
}