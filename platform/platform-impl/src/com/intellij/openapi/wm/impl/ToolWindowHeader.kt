// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.PopupHandler
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.migLayout.*
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.ui.popup.PopupState
import com.intellij.ui.tabs.impl.MorePopupAware
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.*
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.ConstraintParser
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.function.Supplier
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.plaf.PanelUI

abstract class ToolWindowHeader internal constructor(
  private val toolWindow: ToolWindowImpl,
  private val contentUi: ToolWindowContentUi,
  private val gearProducer: Supplier<ActionGroup>
) :
  JPanel(MigLayout(createLayoutConstraints(0, 0).noVisualPadding().fill(), ConstraintParser.parseColumnConstraints("[grow][pref!]"))),
  UISettingsListener, DataProvider {
  private var image: BufferedImage? = null
  private var activeImage: BufferedImage? = null
  private var imageType: ToolWindowType? = null
  private val actionGroup = DefaultActionGroup()
  private val actionGroupWest = DefaultActionGroup()
  private val toolbar: ActionToolbar
  private var toolbarWest: ActionToolbar? = null
  private val westPanel: JPanel

  init {
    @Suppress("LeakingThis")
    AccessibleContextUtil.setName(this, IdeBundle.message("toolwindow.header.accessible.name"))
    westPanel = JPanel(MigLayout(createLayoutConstraints(0, 0).noVisualPadding().fillY()))
    westPanel.isOpaque = false
    westPanel.add(contentUi.tabComponent, CC().growY())
    @Suppress("LeakingThis")
    add(westPanel, CC().grow())
    ToolWindowContentUi.initMouseListeners(westPanel, contentUi, true)
    toolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLWINDOW_TITLE,
      object : ActionGroup(), DumbAware {
        private val children by lazy<Array<AnAction>> {
          val tabListAction = ActionManager.getInstance().getAction("TabList")
          arrayOf(tabListAction, actionGroup, ShowOptionsAction(), HideAction())
        }

        override fun getChildren(e: AnActionEvent?) = children

        override fun isDumbAware() = true
      },
      true
    )

    @Suppress("LeakingThis")
    toolbar.setTargetComponent(this)
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    toolbar.setReservePlaceAutoPopupIcon(false)
    val component = toolbar.component
    component.border = JBUI.Borders.empty(2, 0)
    component.isOpaque = false
    @Suppress("LeakingThis")
    add(component)

    westPanel.addMouseListener(
      object : PopupHandler() {
        override fun invokePopup(comp: Component, x: Int, y: Int) {
          contentUi.showContextMenu(comp, x, y, toolWindow.popupGroup, contentUi.contentManager.selectedContent)
        }
      }
    )
    westPanel.addMouseListener(
      object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          toolWindow.fireActivated(ToolWindowEventSource.ToolWindowHeader)
        }
      }
    )

    @Suppress("LeakingThis")
    addMouseListener(
      object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
          if (e.isPopupTrigger) {
            return
          }

          if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
            if (e.isAltDown) {
              toolWindow.fireHidden(ToolWindowEventSource.ToolWindowHeaderAltClick)
            }
            else {
              toolWindow.fireHiddenSide(ToolWindowEventSource.ToolWindowHeader)
            }
          }
          else {
            toolWindow.fireActivated(ToolWindowEventSource.ToolWindowHeader)
          }
        }
      }
    )

    isOpaque = true
    border = JBUI.Borders.empty(0)
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        val manager = toolWindow.toolWindowManager
        manager.setMaximized(this@ToolWindowHeader.toolWindow, !manager.isMaximized(this@ToolWindowHeader.toolWindow))
        return true
      }
    }.installOn(westPanel)
    westPanel.addMouseListener(
      object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
          val runnable = Runnable { dispatchEvent(SwingUtilities.convertMouseEvent(e.component, e, this@ToolWindowHeader)) }
          SwingUtilities.invokeLater(runnable)
        }
      }
    )
  }

  fun getToolbar(): ActionToolbar? {
    return toolbar
  }

  override fun getData(dataId: String): Any? {
    if (MorePopupAware.KEY.`is`(dataId)) {
      return contentUi.getData(dataId)
    }
    else {
      return null
    }
  }

  private fun initWestToolBar(westPanel: JPanel) {
    toolbarWest = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TITLE, DefaultActionGroup(actionGroupWest), true)
    toolbarWest!!.setTargetComponent(this)
    toolbarWest!!.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    toolbarWest!!.setReservePlaceAutoPopupIcon(false)
    val component = toolbarWest!!.component
    component.isOpaque = false
    component.border = JBUI.Borders.empty()
    westPanel.add(component)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    clearCaches()
  }

  fun setTabActions(actions: Array<AnAction>) {
    if (toolbarWest == null) {
      initWestToolBar(westPanel)
    }
    actionGroupWest.removeAll()
    actionGroupWest.addSeparator()
    actionGroupWest.addAll(*actions)
    toolbarWest?.updateActionsImmediately()
  }

  fun setAdditionalTitleActions(actions: List<AnAction>) {
    actionGroup.removeAll()
    actionGroup.addAll(actions)
    if (actions.isNotEmpty()) {
      actionGroup.addSeparator()
    }
    toolbar.updateActionsImmediately()
  }

  override fun getComponentGraphics(g: Graphics): Graphics {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
  }

  override fun paintComponent(g: Graphics) {
    val r = bounds
    val g2d = g as Graphics2D
    val clip = g2d.clip
    val type = toolWindow.type
    val image: Image?
    if (isActive) {
      if (activeImage == null ||  /*myActiveImage.getHeight() != r.height ||*/type != imageType) {
        activeImage = drawToBuffer(g2d, true, r.height, type == ToolWindowType.FLOATING)
      }
      image = activeImage
    }
    else {
      if (this.image == null ||  /*myImage.getHeight() != r.height ||*/type != imageType) {
        this.image = drawToBuffer(g2d, false, r.height, type == ToolWindowType.FLOATING)
      }
      image = this.image
    }
    imageType = type
    val clipBounds = clip.bounds
    var x = clipBounds.x
    while (x < clipBounds.x + clipBounds.width) {
      StartupUiUtil.drawImage(g, image!!, x, 0, null)
      x += 150
    }
  }

  override fun setUI(ui: PanelUI) {
    clearCaches()
    super.setUI(ui)
  }

  fun clearCaches() {
    image = null
    activeImage = null
  }

  override fun paintChildren(g: Graphics) {
    val graphics = g.create() as Graphics2D
    setupAntialiasing(graphics)
    super.paintChildren(graphics)
    val r = bounds
    if (!isActive && !StartupUiUtil.isUnderDarcula()) {
      graphics.color = Color(255, 255, 255, 30)
      graphics.fill(r)
    }
    graphics.dispose()
  }

  protected abstract val isActive: Boolean

  protected abstract fun hideToolWindow()

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    val insets = insets
    val height = JBUI.scale(SingleHeightTabs.UNSCALED_PREF_HEIGHT) - insets.top - insets.bottom
    return Dimension(size.width, height)
  }

  private inner class ShowOptionsAction : DumbAwareAction() {
    val myPopupState = PopupState.forPopupMenu()
    override fun actionPerformed(e: AnActionEvent) {
      if (myPopupState.isRecentlyHidden) return // do not show new popup
      val inputEvent = e.inputEvent
      val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, gearProducer.get())
      var x = 0
      var y = 0
      if (inputEvent is MouseEvent) {
        x = inputEvent.x
        y = inputEvent.y
      }
      myPopupState.prepareToShow(popupMenu.component)
      popupMenu.component.show(inputEvent.component, x, y)
    }

    init {
      copyFrom(gearProducer.get())
    }
  }

  private inner class HideAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      hideToolWindow()
    }

    override fun update(event: AnActionEvent) {
      event.presentation.isEnabled = toolWindow.isVisible
    }

    init {
      ActionUtil.copyFrom(this, InternalDecoratorImpl.HIDE_ACTIVE_WINDOW_ACTION_ID)
      templatePresentation.icon = AllIcons.General.HideToolWindow
      templatePresentation.setText { UIBundle.message("tool.window.hide.action.name") }
    }
  }
}

private fun drawToBuffer(g2d: Graphics2D, active: Boolean, height: Int, floating: Boolean): BufferedImage {
  val width = 150
  val image = ImageUtil.createImage(g2d, width, height, BufferedImage.TYPE_INT_RGB)
  val g = image.createGraphics()
  UIUtil.drawHeader(g, 0, width, height, active, true, !floating, true)
  g.dispose()
  return image
}