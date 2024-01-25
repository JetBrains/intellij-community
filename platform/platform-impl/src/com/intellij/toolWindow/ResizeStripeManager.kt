// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.actions.ToolWindowShowNamesAction
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splittable
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBUI
import java.awt.*
import kotlin.math.max

/**
 * @author Alexander Lobas
 */
class ResizeStripeManager(private val myComponent: ToolWindowToolbar, private val myAnchor: ToolWindowAnchor) : Splittable {
  private val mySplitter = object : OnePixelDivider(false, this) {
    override fun paint(g: Graphics) {
    }
  }

  private var myIgnoreProportion = true
  private var myCalculateDelta = false
  private var myDelta = 0
  private var myCustomWidth = 0
  private var myProject: Project? = null

  init {
    myComponent.addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        val action = ToolWindowShowNamesAction()
        val group = object : ActionGroup() {
          override fun getChildren(e: AnActionEvent?) = arrayOf(action)
        }
        //group = DefaultActionGroup(action) // XXX
        val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group)
        popupMenu.component.show(component, x, y)
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

      override fun preferredLayoutSize(target: Container?): Dimension {
        val size = super.preferredLayoutSize(target)
        if (myCustomWidth != 0) {
          size.width = myCustomWidth
        }
        return size
      }

      override fun layoutContainer(target: Container) {
        super.layoutContainer(target)
        if (mySplitter.parent === target) {
          val width = JBUI.scale(1)
          mySplitter.setBounds(if (myAnchor == ToolWindowAnchor.LEFT) target.width - width else 0, 0, width, target.height)
        }
      }
    }
  }

  fun updateState(project: Project) {
    myProject = project
    val manager = ToolWindowManagerEx.getInstanceEx(project)
    val enabled = manager.isShowNames()
    if (enabled) {
      myCustomWidth = manager.getSideCustomWidth(myAnchor)
      myComponent.add(mySplitter)
    }
    else {
      myCustomWidth = 0
      myComponent.remove(mySplitter)
    }
    mySplitter.setResizeEnabled(enabled)
    updateView()
  }


  override fun setProportion(proportion: Float) {
    if (myIgnoreProportion) {
      return
    }
    myIgnoreProportion = true

    val fullWidth = myComponent.parent.width
    var width = (fullWidth * proportion).toInt()
    if (myAnchor == ToolWindowAnchor.RIGHT) {
      width = fullWidth - width
    }
    if (myCalculateDelta) {
      myCalculateDelta = false
      myDelta = max(myCustomWidth - width, 4)
    }
    width += myDelta

    val min = JBUI.scale(if (UISettings.Companion.getInstance().compactMode) 32 else 40)
    if (width < min) {
      width = min
    }

    val max = JBUI.scale(100)
    if (width > max) {
      width = max
    }

    myCustomWidth = width
    ToolWindowManagerEx.getInstanceEx(myProject ?: return).setSideCustomWidth(myAnchor, width)
    updateView()
  }

  private fun updateView() {
    for (button in myComponent.topStripe.getButtons()) {
      (button.getComponent() as SquareStripeButton).setOrUpdateShowName(myCustomWidth > 0)
    }
    for (button in myComponent.bottomStripe.getButtons()) {
      (button.getComponent() as SquareStripeButton).setOrUpdateShowName(myCustomWidth > 0)
    }

    val parent = myComponent.parent
    parent.revalidate()
    myComponent.revalidate()
    parent.repaint()
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
}