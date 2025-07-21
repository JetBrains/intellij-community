// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.impl.ToolWindowUIDecorator
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.xNext.island.XNextIslandHolder
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import javax.swing.JComponent
import javax.swing.border.Border

internal class IslandsUICustomization : InternalUICustomization() {
  private val isIslandsAvailable = !Registry.`is`("llm.riderNext.enabled", false) && ExperimentalUI.isNewUI()

  private var isManyIslandEnabledCache: Boolean? = null

  private val isManyIslandEnabled: Boolean
    get() {
      var value = isManyIslandEnabledCache
      if (value == null) {
        value = isIslandsAvailable && JBUI.getInt("Islands", 0) == 1
        isManyIslandEnabledCache = value
      }
      return value
    }

  private var isIslandsGradientEnabledCache: Boolean? = null

  private val isIslandsGradientEnabled: Boolean
    get() {
      var value = isIslandsGradientEnabledCache
      if (value == null) {
        value = Registry.`is`("idea.islands.gradient.enabled", true) && UISettings.getInstance().differentiateProjects
        isIslandsGradientEnabledCache = value
      }
      return value
    }

  override fun updateBackgroundPainter() {
    isIslandsGradientEnabledCache = null
  }

  override val isProjectCustomDecorationGradientPaint: Boolean = !isManyIslandEnabled || !isIslandsGradientEnabled

  override val shouldPaintEditorFadeout: Boolean = !isManyIslandEnabled

  override val toolWindowUIDecorator: ToolWindowUIDecorator = object : ToolWindowUIDecorator() {
    override fun decorateAndReturnHolder(divider: JComponent, child: JComponent, toolWindow: ToolWindow, originalBorderBuilder: () -> Border): JComponent {
      return XNextIslandHolder().apply {
        layout = BorderLayout()
        add(divider, BorderLayout.NORTH)
        add(child, BorderLayout.CENTER)

        putClientProperty("originalBorderBuilder", originalBorderBuilder)

        if (isManyIslandEnabled) {
          background = JBUI.CurrentTheme.ToolWindow.background()
          IslandsRoundedBorder.createToolWindowBorder(toolWindow, this)
          child.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
        }
        else {
          border = originalBorderBuilder()
        }
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

    var oldManyIsland = isManyIslandEnabled

    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      val toolkit = Toolkit.getDefaultToolkit()

      toolkit.removeAWTEventListener(awtListener)
      isManyIslandEnabledCache = null

      val newManyIsland = isManyIslandEnabled

      if (newManyIsland && JBColor.isBright()) {
        toolkit.addAWTEventListener(awtListener, AWTEvent.HIERARCHY_EVENT_MASK)
      }

      if (oldManyIsland == newManyIsland) {
        return@LafManagerListener
      }
      if (newManyIsland) {
        enableManyIslands()
      }
      else {
        disableManyIslands()
      }

      oldManyIsland = newManyIsland
    })
  }

  private fun enableManyIslands() {
    editorTabPainterAdapter.isEnabled = true

    // XXX: dialogs

    for (frame in WindowManager.getInstance().allProjectFrames) {
      UIUtil.forEachComponentInHierarchy(frame.component) {
        when (it) {
          is EditorsSplitters -> {
            IslandsRoundedBorder.createEditorBorder(it, editorTabPainterAdapter)
            clearParentNoBackground(it)
          }
          is JBEditorTabs -> {
            if (it.parent is EditorsSplitters) {
              ClientProperty.putRecursive(it, IdeBackgroundUtil.NO_BACKGROUND, true)
            }
          }
          is ManyIslandDivider -> {
            it.configure(true)
          }
        }
      }

      val project = frame.project
      if (project != null) {
        val manager = ToolWindowManager.getInstance(project) as ToolWindowManagerEx
        for (toolwindow in manager.toolWindows) {
          if (toolwindow is ToolWindowImpl) {
            toolwindow.getNullableDecorator()?.also {
              UIUtil.findComponentOfType(it, XNextIslandHolder::class.java)?.also { holder ->
                setToolWindowManyBorder(toolwindow, holder)
              }
            }
          }
        }
      }
    }
  }

  private fun disableManyIslands() {
    editorTabPainterAdapter.isEnabled = false

    // XXX: dialogs

    for (frame in WindowManager.getInstance().allProjectFrames) {
      UIUtil.forEachComponentInHierarchy(frame.component) {
        if (it is JComponent) {
          ClientProperty.removeRecursive(it, IdeBackgroundUtil.NO_BACKGROUND)
        }

        when (it) {
          is EditorsSplitters -> {
            it.border = null
          }
          is ManyIslandDivider -> {
            it.configure(false)
          }
        }
      }

      val project = frame.project
      if (project != null) {
        val manager = ToolWindowManager.getInstance(project) as ToolWindowManagerEx
        for (toolwindow in manager.toolWindows) {
          if (toolwindow is ToolWindowImpl) {
            toolwindow.getNullableDecorator()?.also {
              UIUtil.findComponentOfType(it, XNextIslandHolder::class.java)?.also { holder ->
                setOriginalToolWindowBorder(holder)
              }
            }
          }
        }
      }
    }
  }

  private fun setOriginalToolWindowBorder(holder: XNextIslandHolder) {
    val builder = holder.getClientProperty("originalBorderBuilder")
    if (builder is Function0<*>) {
      val border = builder()
      if (border is Border) {
        holder.border = border
      }
    }
    holder.background = JBColor.PanelBackground
  }

  private fun setToolWindowManyBorder(toolwindow: ToolWindow, holder: XNextIslandHolder) {
    holder.background = JBUI.CurrentTheme.ToolWindow.background()
    IslandsRoundedBorder.createToolWindowBorder(toolwindow, holder)
    clearParentNoBackground(holder)

    for (child in holder.components) {
      ClientProperty.putRecursive(child as JComponent, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  private fun clearParentNoBackground(component: JComponent) {
    var nextComponent: JComponent? = component

    while (nextComponent != null && ClientProperty.get(nextComponent, IdeBackgroundUtil.NO_BACKGROUND) != null) {
      ClientProperty.removeRecursive(nextComponent, IdeBackgroundUtil.NO_BACKGROUND)
      nextComponent = nextComponent.parent as JComponent?
    }
  }

  override fun configureToolWindowPane(toolWindowPaneParent: JComponent, buttonManager: ToolWindowButtonManager) {
    if (isManyIslandEnabled && buttonManager is ToolWindowPaneNewButtonManager) {
      buttonManager.addVisibleToolbarsListener { leftVisible, rightVisible ->
        if (leftVisible && rightVisible) {
          if (toolWindowPaneParent.border != null) {
            toolWindowPaneParent.border = null
          }
        }
        else {
          val gap = JBUI.getInt("Islands.emptyGap", JBUI.scale(4))
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

  override fun createCustomDivider(isVertical: Boolean, splitter: Splittable): Divider? {
    return ManyIslandDivider(isVertical, splitter).also {
      it.configure(isManyIslandEnabled)
    }
  }

  override fun configureRendererComponent(component: JComponent) {
    if (isManyIslandEnabled) {
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
      IslandsRoundedBorder.createEditorBorder(component, editorTabPainterAdapter)
    }
  }

  override fun paintBeforeEditorEmptyText(component: JComponent, graphics: Graphics) {
    if (isManyIslandEnabled) {
      IslandsRoundedBorder.paintBeforeEditorEmptyText(component, graphics, editorTabPainterAdapter)
    }
  }

  override val editorTabPainterAdapter: IslandsTabPainterAdapter = IslandsTabPainterAdapter(isManyIslandEnabled)

  private fun getMainBackgroundColor(): Color {
    return JBColor.namedColor("MainWindow.background", JBColor.PanelBackground)
  }

  override fun getCustomMainBackgroundColor(): Color? {
    if (isManyIslandEnabled) {
      return getMainBackgroundColor()
    }
    return null
  }

  override fun attachIdeFrameBackgroundPainter(frame: IdeFrame, glassPane: IdeGlassPane) {
    if (glassPane is IdeGlassPaneImpl) {
      glassPane.addFallbackBackgroundPainter(IslandsGradientPainter(frame, getMainBackgroundColor()) {
        isManyIslandEnabled && isIslandsGradientEnabled
      })
    }
  }

  override fun configureButtonLook(look: ActionButtonLook, g: Graphics): Graphics? {
    if (isManyIslandEnabled && isIslandsGradientEnabled && look is SquareStripeButtonLook) {
      return IdeBackgroundUtil.getOriginalGraphics(g)
    }
    return null
  }

  override fun transformGraphics(component: JComponent, graphics: Graphics): Graphics {
    if (isManyIslandEnabled && isIslandsGradientEnabled) {
      return JBSwingUtilities.runGlobalCGTransform(component, graphics)
    }
    return graphics
  }

  override fun transformButtonGraphics(graphics: Graphics): Graphics {
    return preserveGraphics(graphics)
  }

  override fun preserveGraphics(graphics: Graphics): Graphics {
    if (isManyIslandEnabled && isIslandsGradientEnabled) {
      return IdeBackgroundUtil.getOriginalGraphics(graphics)
    }
    return graphics
  }
}

private class ManyIslandDivider(isVertical: Boolean, splitter: Splittable) : OnePixelDivider(isVertical, splitter) {
  private var doPaint = true

  fun configure(manyIsland: Boolean) {
    doPaint = !manyIsland

    if (manyIsland) {
      putClientProperty("DividerWidth", 0)
    }
    else {
      putClientProperty("DividerWidth", null)
    }
  }

  override fun paint(g: Graphics) {
    if (doPaint) {
      super.paint(g)
    }
  }
}