// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.impl.ToolWindowUIDecorator
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.toolWindow.FrameLayeredPane
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.xNext.island.XNextIslandHolder
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLayeredPane
import javax.swing.UIManager

internal class IslandsUICustomization : InternalUICustomization() {
  private val isIslandsAvailable = !Registry.`is`("llm.riderNext.enabled", false) && ExperimentalUI.isNewUI()

  private val isOneIslandEnabled: Boolean
    get() {
      return isIslandsAvailable && JBUI.getInt("Island", 0) == 1
    }

  private val isManyIslandEnabled: Boolean
    get() {
      return isIslandsAvailable && JBUI.getInt("Islands", 0) == 1
    }

  private val isIslandsEnabled: Boolean
    get() {
      return isOneIslandEnabled || isManyIslandEnabled
    }

  private val isIslandsGradientEnabled: Boolean = Registry.`is`("idea.islands.gradient.enabled", true)

  override val isProjectCustomDecorationGradientPaint: Boolean = !isIslandsEnabled || !isIslandsGradientEnabled

  override val shouldPaintEditorFadeout: Boolean = !isIslandsEnabled

  private val toolWindowDecorator = object : ToolWindowUIDecorator() {
    override fun decorateAndReturnHolder(divider: JComponent, child: JComponent): JComponent {
      return XNextIslandHolder().apply {
        layout = BorderLayout()
        background = JBUI.CurrentTheme.ToolWindow.background()
        add(divider, BorderLayout.NORTH)
        add(child, BorderLayout.CENTER)

        IslandsRoundedBorder.createToolWindowBorder(this)
        child.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
      }
    }
  }

  private val awtListener = AWTEventListener { event ->
    val component = (event as HierarchyEvent).component
    val isToolWindow = UIUtil.getParentOfType(XNextIslandHolder::class.java, component) != null

    if (isToolWindow) {
      UIUtil.forEachComponentInHierarchy(component) {
        if (it.background == JBColor.PanelBackground) {
          it.background = JBUI.CurrentTheme.ToolWindow.background()
        }
      }
    }
  }

  init {
    if (isManyIslandEnabled && JBColor.isBright()) {
      Toolkit.getDefaultToolkit().addAWTEventListener(awtListener, AWTEvent.HIERARCHY_EVENT_MASK)
    }

    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      val toolkit = Toolkit.getDefaultToolkit()

      toolkit.removeAWTEventListener(awtListener)

      if (isManyIslandEnabled && JBColor.isBright()) {
        toolkit.addAWTEventListener(awtListener, AWTEvent.HIERARCHY_EVENT_MASK)
      }
    })
  }

  private val tabPainterAdapter = ManyIslandsTabPainterAdapter()

  override val toolWindowUIDecorator: ToolWindowUIDecorator
    get() {
      if (isManyIslandEnabled) {
        return toolWindowDecorator
      }
      return super.toolWindowUIDecorator
    }

  override fun configureToolWindowPane(toolWindowPaneParent: JComponent, buttonManager: ToolWindowButtonManager) {
    if (isIslandsEnabled && buttonManager is ToolWindowPaneNewButtonManager) {
      buttonManager.addVisibleToolbarsListener { leftVisible, rightVisible ->
        if (leftVisible && rightVisible) {
          if (toolWindowPaneParent.border != null) {
            toolWindowPaneParent.border = null
          }
        }
        else {
          val gap = JBUI.getInt("Islands.emptyGap", JBUI.scale(if (isManyIslandEnabled) 4 else 8))
          val left = if (leftVisible) 0 else gap
          val right = if (rightVisible) 0 else gap

          val border = toolWindowPaneParent.border
          if (border == null) {
            toolWindowPaneParent.border = JBUI.Borders.empty(0, left, 0, right)
          }
          else {
            val insets = border.getBorderInsets(toolWindowPaneParent)
            if (insets.left != left || insets.right != right) {
              toolWindowPaneParent.border = JBUI.Borders.empty(0, left, 0, right)
            }
          }
        }
      }
      buttonManager.updateToolStripesVisibility()
    }
  }

  override fun createToolWindowPaneLayered(splitter: JComponent, frame: JFrame): JLayeredPane? {
    if (isOneIslandEnabled) {
      return object : FrameLayeredPane(splitter, frame) {
        init {
          ClientProperty.putRecursive(this, IdeBackgroundUtil.NO_BACKGROUND, true)
        }

        @Suppress("GraphicsSetClipInspection")
        override fun paintChildren(g: Graphics) {
          val isGradient = isIslandsGradientEnabled

          if (isGradient) {
            putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, null)
            val gg = IdeBackgroundUtil.withFrameBackground(g, this)
            gg.color = parent.background
            gg.fillRect(0, 0, width, height)
            putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
          }

          super.paintChildren(g)

          val config = GraphicsUtil.setupRoundedBorderAntialiasing(g)

          try {
            val gg: Graphics

            if (isGradient) {
              putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, null)
              gg = IdeBackgroundUtil.withFrameBackground(g, this)
              gg.color = parent.background
            }
            else {
              gg = g
              gg.color = background
            }

            val shape = Area(Rectangle(0, 0, width, height))
            val cornerRadius = JBUI.getInt("Island.arc", 10)
            val cornerRadiusF = cornerRadius.toFloat()
            shape.subtract(Area(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), cornerRadiusF, cornerRadiusF)))
            (gg as Graphics2D).fill(shape)

            val color = UIManager.get("Island.borderColor")

            if (color is Color) {
              gg.color = color
              gg.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
            }
          }
          finally {
            if (isGradient) {
              putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
            }
            config.restore()
          }

          val fileEditorManager = (ProjectUtil.getProjectForWindow(frame) ?: return).getServiceIfCreated(FileEditorManager::class.java)

          if (fileEditorManager?.openFiles?.isEmpty() == true) {
            val editorEmptyTextPainter = ApplicationManager.getApplication().getService(EditorEmptyTextPainter::class.java)
            editorEmptyTextPainter.paintEmptyText(IdeGlassPaneUtil.find(this) as JComponent, g)
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
      }.also {
        it.putClientProperty("DividerWidth", 0)
      }
    }
    return null
  }

  override fun configureRendererComponent(component: JComponent) {
    if (isIslandsEnabled) {
      ClientProperty.putRecursive(component, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  override fun installEditorBackground(component: JComponent) {
    if (isManyIslandEnabled) {
      ClientProperty.putRecursive(component, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  override fun configureEditorsSplitters(component: EditorsSplitters) {
    if (isManyIslandEnabled) {
      IslandsRoundedBorder.createEditorBorder(component, tabPainterAdapter)
    }
  }

  override fun paintBeforeEditorEmptyText(component: JComponent, graphics: Graphics) {
    if (isManyIslandEnabled) {
      IslandsRoundedBorder.paintBeforeEditorEmptyText(component, graphics, tabPainterAdapter)
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