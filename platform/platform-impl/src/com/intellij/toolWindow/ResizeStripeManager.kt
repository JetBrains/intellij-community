// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseEvent

/**
 * @author Alexander Lobas
 */
class ResizeStripeManager(private val myComponent: ToolWindowToolbar) : Splittable {
  private val mySplitter = object : OnePixelDivider(false, this) {
    override fun noDeepestComponent(e: MouseEvent, deepestComponentAt: Component?): Boolean {
      if (e.id == MouseEvent.MOUSE_DRAGGED && myDragging) {
        return false
      }
      return super.noDeepestComponent(e, deepestComponentAt)
    }

    override fun paint(g: Graphics) {
    }
  }

  private var myIgnoreProportion = true
  private var myCalculateDelta = false
  private var myDelta = 0
  private var myCustomWidth = 0
  private var myCurrentScale = 0f

  init {
    myComponent.addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        if (enabled()) {
          val action = ActionManager.getInstance().getAction("ToolWindowShowNamesAction")!!
          val group = object : ActionGroup() {
            override fun getChildren(e: AnActionEvent?) = arrayOf(action)
          }
          showPopup(group, component, x, y)
        }
      }
    })
  }

  fun createLayout(): BorderLayout {
    return object : BorderLayout() {
      override fun addLayoutComponent(name: String?, component: Component) {
        if (component !== mySplitter) {
          super.addLayoutComponent(name, component)
        }
      }

      override fun preferredLayoutSize(target: Container): Dimension {
        val size = super.preferredLayoutSize(target)
        if (myCustomWidth != 0 && (!myComponent.topStripe.getButtons().isEmpty() || !myComponent.bottomStripe.getButtons().isEmpty())) {
          size.width = myCustomWidth
        }
        return size
      }

      override fun layoutContainer(target: Container) {
        super.layoutContainer(target)
        if (mySplitter.parent === target) {
          val width = JBUI.scale(1)
          mySplitter.setBounds(if (myComponent.anchor == ToolWindowAnchor.LEFT) target.width - width else 0, 0, width, target.height)
        }
      }
    }
  }

  fun updateState(toolbar: ToolWindowToolbar?) {
    if (toolbar == null) {
      val enabled = isShowNames()
      if (enabled) {
        myCustomWidth = getSideCustomWidth(myComponent.anchor)
        myCurrentScale = UISettings.getInstance().currentIdeScale
        myComponent.add(mySplitter)
      }
      else {
        myCustomWidth = 0
        myComponent.remove(mySplitter)
      }
      mySplitter.setResizeEnabled(enabled)
    }
    else if (toolbar === myComponent || toolbar.anchor != myComponent.anchor) {
      return
    }
    else {
      myCustomWidth = getSideCustomWidth(myComponent.anchor)
      myCurrentScale = UISettings.getInstance().currentIdeScale
    }
    updateView()
  }

  override fun setProportion(proportion: Float) {
    if (myIgnoreProportion) {
      return
    }
    myIgnoreProportion = true

    val fullWidth = myComponent.parent.width
    var width = (fullWidth * proportion).toInt()
    if (myComponent.anchor == ToolWindowAnchor.RIGHT) {
      width = fullWidth - width
    }
    if (myCalculateDelta) {
      myCalculateDelta = false
      myDelta = myCustomWidth - width
    }
    width += myDelta

    width = checkMinMax(width)

    myCustomWidth = width
    myCurrentScale = UISettings.getInstance().currentIdeScale
    setSideCustomWidth(myComponent, width)
    updateView()
  }

  private fun checkMinMax(width: Int): Int {
    val min = JBUI.scale(if (UISettings.getInstance().compactMode) 33 else 40)
    if (width < min) {
      return min
    }

    val max = JBUI.scale(100)
    if (width > max) {
      return max
    }

    return width
  }

  fun updateNamedState() {
    val currentScale = UISettings.getInstance().currentIdeScale
    if (myCustomWidth == 0 && myCurrentScale == 0f) {
      myCustomWidth = getSideCustomWidth(myComponent.anchor)
      val width = checkMinMax(myCustomWidth)
      if (width != myCustomWidth) {
        myCustomWidth = width
        setSideCustomWidth(myComponent, width)
      }
    }
    else if (myCurrentScale != currentScale) {
      myCustomWidth = (myCustomWidth * currentScale / myCurrentScale).toInt()
      setSideCustomWidth(myComponent, myCustomWidth)
    }
    else {
      return
    }
    myCurrentScale = currentScale
    updateView()
  }

  private fun updateView() {
    for (button in myComponent.topStripe.getButtons()) {
      (button.getComponent() as SquareStripeButton).setOrUpdateShowName(myCustomWidth > 0)
    }
    for (button in myComponent.bottomStripe.getButtons()) {
      (button.getComponent() as SquareStripeButton).setOrUpdateShowName(myCustomWidth > 0)
    }

    myComponent.revalidate()
  }

  override fun asComponent(): Component {
    // OnePixelDivider has behaviour for click and double click into divider - sets 0.5 proportion
    // if user do drag for resize that we have calls: asComponent(), setProportion(), asComponent(), setProportion(), ...
    // if user do click we have calls: setProportion(), setProportion(), ...
    // so myIgnoreProportion used for ignore click behaviour
    myIgnoreProportion = false
    return myComponent.parent
  }

  override fun getMinProportion(first: Boolean) = 0f

  override fun getOrientation() = false

  override fun setOrientation(verticalSplit: Boolean) {
  }

  override fun setDragging(dragging: Boolean) {
    myCalculateDelta = dragging
    myDelta = 0
  }

  companion object {
    private var myKeyListener: RegistryValueListener? = null

    fun enabled(): Boolean {
      if (myKeyListener == null) {
        myKeyListener = object : RegistryValueListener {
          override fun afterValueChanged(value: RegistryValue) {
            if (!value.asBoolean()) {
              setShowNames(false)
            }
          }
        }
        Registry.get("toolwindow.enable.show.names").addListener(myKeyListener!!, ApplicationManager.getApplication())
      }
      return Registry.`is`("toolwindow.enable.show.names", true)
    }

    fun isShowNames(): Boolean = enabled() && UISettings.getInstance().showToolWindowsNames

    fun setShowNames(value: Boolean) {
      UISettings.getInstance().showToolWindowsNames = value
      applyShowNames()
    }

    fun applyShowNames() {
      val uiSettings = UISettings.getInstance()
      val newValue = uiSettings.showToolWindowsNames
      val defaultWidth = if (newValue) JBUI.scale(59) else 0

      uiSettings.toolWindowLeftSideCustomWidth = defaultWidth
      uiSettings.toolWindowRightSideCustomWidth = defaultWidth

      for (project in ProjectManager.getInstance().openProjects) {
        ToolWindowManagerEx.getInstanceEx(project).setShowNames(newValue)
      }

      showToolWindowNamesChanged(newValue)
    }

    fun getSideCustomWidth(side: ToolWindowAnchor): Int {
      if (side == ToolWindowAnchor.LEFT) {
        return UISettings.getInstance().toolWindowLeftSideCustomWidth
      }
      if (side == ToolWindowAnchor.RIGHT) {
        return UISettings.getInstance().toolWindowRightSideCustomWidth
      }
      return 0
    }

    fun setSideCustomWidth(toolbar: ToolWindowToolbar, width: Int) {
      when (toolbar.anchor) {
        ToolWindowAnchor.LEFT -> {
          UISettings.getInstance().toolWindowLeftSideCustomWidth = width
        }
        ToolWindowAnchor.RIGHT -> {
          UISettings.getInstance().toolWindowRightSideCustomWidth = width
        }
      }

      for (project in ProjectManager.getInstance().openProjects) {
        ToolWindowManagerEx.getInstanceEx(project).setSideCustomWidth(toolbar, width)
      }
    }

    fun showPopup(group: ActionGroup, component: Component, x: Int, y: Int) {
      val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group)
      popupMenu.component.show(component, x, y)
    }
  }
}