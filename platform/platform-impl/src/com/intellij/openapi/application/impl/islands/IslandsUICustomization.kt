// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.impl.ToolWindowUIDecorator
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.toolWindow.FrameLayeredPane
import com.intellij.toolWindow.xNext.island.XNextIslandHolder
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.geom.RoundRectangle2D
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLayeredPane
import javax.swing.UIManager

internal class IslandsUICustomization : InternalUICustomization() {
  companion object {
    val isIslandsAvailable: Boolean = Registry.`is`("idea.islands.enabled", false) && !Registry.`is`("llm.riderNext.enabled", false)

    val isIslandsEnabled: Boolean = isIslandsAvailable && getIslandsType() != "default"

    val isOneIslandEnabled: Boolean = isIslandsAvailable && getIslandsType() == "island"

    val isManyIslandEnabled: Boolean = isIslandsAvailable && getIslandsType() == "islands"

    fun getIslandsType(): String = PropertiesComponent.getInstance().getValue("idea.islands.type", "default")

    fun setIslandsType(type: String) {
      if (type == getIslandsType()) {
        return
      }

      PropertiesComponent.getInstance().setValue("idea.islands.type", type)

      val uiThemeManager = UiThemeProviderListManager.getInstance()
      val isLight = JBColor.isBright()

      val newTheme = when (type) {
        "island" -> {
          uiThemeManager.findThemeById(if (isLight) "One Island Light" else "One Island Darker")
        }
        "islands" -> {
          uiThemeManager.findThemeById(if (isLight) "Many Islands Light" else "Many Islands Darker")
        }
        else -> {
          uiThemeManager.findThemeById(if (isLight) "ExperimentalLight" else "ExperimentalDark")
        }
      }

      val lafManager = LafManager.getInstance()

      if (newTheme == null) {
        lafManager.setCurrentLookAndFeel((if (isLight) lafManager.defaultLightLaf else lafManager.defaultDarkLaf)!!, true)
      }
      else {
        lafManager.setCurrentLookAndFeel(newTheme, true)
      }

      if (PluginManagerConfigurable.showRestartDialog(IdeBundle.message("dialog.title.restart.required"), Function {
          IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                            ApplicationNamesInfo.getInstance().fullProductName)
        }) == Messages.YES) {
        ApplicationManagerEx.getApplicationEx().restart(true)
      }
    }
  }

  private val isIslandsGradientEnabled: Boolean = Registry.`is`("idea.islands.gradient.enabled", true)

  override val isProjectCustomDecorationGradientPaint: Boolean = !isIslandsEnabled || !isIslandsGradientEnabled

  override val shouldPaintEditorFadeout: Boolean = !isIslandsEnabled

  private val toolWindowDecorator = object : ToolWindowUIDecorator() {
    override fun decorateAndReturnHolder(divider: JComponent, child: JComponent): JComponent? {
      return XNextIslandHolder().apply {
        layout = BorderLayout()
        add(divider, BorderLayout.NORTH)
        add(child, BorderLayout.CENTER)

        IslandsRoundedBorder.createToolWindowBorder(this, child)
        child.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
      }
    }
  }

  private val tabPainterAdapter = ManyIslandsTabPainterAdapter()

  override val toolWindowUIDecorator: ToolWindowUIDecorator
    get() {
      if (isManyIslandEnabled) {
        return toolWindowDecorator
      }
      return super.toolWindowUIDecorator
    }

  override fun createToolWindowPaneLayered(splitter: JComponent, frame: JFrame): JLayeredPane? {
    if (isOneIslandEnabled) {
      return object : FrameLayeredPane(splitter, frame) {
        init {
          ClientProperty.putRecursive(this, IdeBackgroundUtil.NO_BACKGROUND, true)
        }

        @Suppress("GraphicsSetClipInspection")
        override fun paintChildren(g: Graphics) {
          val cornerRadius = JBUI.getInt("Island.arc", 0)

          if (isIslandsGradientEnabled) {
            putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, null)
            val gg = IdeBackgroundUtil.withFrameBackground(g, this)
            gg.color = parent.background
            gg.fillRect(0, 0, width, height)
            putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
          }

          val clip = g.clip
          g.clip = null
          val cornerRadiusF = cornerRadius.toFloat()
          g.clip = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), cornerRadiusF, cornerRadiusF)

          super.paintChildren(g)

          g.clip = null
          g.clip = clip

          val color = UIManager.get("Island.borderColor")

          if (color is Color) {
            val config = GraphicsUtil.setupRoundedBorderAntialiasing(g)
            g.color = color
            g.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
            config.restore()
          }
        }

        override fun isPaintingOrigin(): Boolean = true
      }
    }
    return null
  }

  override fun createCustomDivider(isVertical: Boolean, splitter: Splittable): Divider? {
    if (isManyIslandEnabled) {
      return object : OnePixelDivider(isVertical, splitter) {
        override fun paint(g: Graphics) {
        }
      }
    }
    return null
  }

  override fun installEditorBackground(component: JComponent) {
    if (isManyIslandEnabled) {
      ClientProperty.putRecursive(component, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  override fun configureEditorsSplitters(component: EditorsSplitters) {
    if (isManyIslandEnabled) {
      IslandsRoundedBorder.createEditorBorder(component)
    }
  }

  override val editorTabPainterAdapter: TabPainterAdapter
    get() {
      if (isIslandsEnabled) {
        return tabPainterAdapter
      }
      return super.editorTabPainterAdapter
    }

  override fun getCustomMainBackgroundColor(): Color? {
    if (isIslandsEnabled) {
      return JBColor.namedColor("MainWindow.background", JBColor.PanelBackground)
    }
    return super.getCustomMainBackgroundColor()
  }

  override fun attachIdeFrameBackgroundPainter(frame: IdeFrame, glassPane: IdeGlassPane) {
    if (isIslandsEnabled && isIslandsGradientEnabled && glassPane is IdeGlassPaneImpl) {
      glassPane.addFallbackBackgroundPainter(IslandsGradientPainter(frame, getCustomMainBackgroundColor()!!))
    }
  }

  override fun configureButtonLook(look: ActionButtonLook, g: Graphics): Graphics? {
    if (isIslandsEnabled && isIslandsGradientEnabled && look is SquareStripeButtonLook) {
      return IdeBackgroundUtil.getOriginalGraphics(g)
    }
    return null
  }

  override fun transformGraphics(component: JComponent, graphics: Graphics): Graphics {
    if (isIslandsEnabled && isIslandsGradientEnabled) {
      return JBSwingUtilities.runGlobalCGTransform(component, graphics)
    }
    return graphics
  }

  override fun transformButtonGraphics(graphics: Graphics): Graphics {
    return preserveGraphics(graphics)
  }

  override fun preserveGraphics(graphics: Graphics): Graphics {
    if (isIslandsEnabled && isIslandsGradientEnabled) {
      return IdeBackgroundUtil.getOriginalGraphics(graphics)
    }
    return graphics
  }
}