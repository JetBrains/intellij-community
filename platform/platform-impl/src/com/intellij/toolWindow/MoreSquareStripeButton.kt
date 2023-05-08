// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.ui.PopupHandler
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseEvent
import javax.swing.Icon

internal class MoreSquareStripeButton(toolWindowToolbar: ToolWindowToolbar,
                                      override val side: ToolWindowAnchor,
                                      vararg moveTo: ToolWindowAnchor) :
  MoreSquareStripeButtonBase(createAction(toolWindowToolbar)) {

  init {
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        val popupMenu = ActionManager.getInstance()
          .createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, createPopupGroup(moveTo))
        popupMenu.component.show(component, x, y)
      }
    })
  }

  private fun createPopupGroup(moveTo: Array<out ToolWindowAnchor>): DefaultActionGroup {
    val group = DefaultActionGroup()

    for (anchor in moveTo) {
      group.add(object : DumbAwareAction(UIBundle.message("tool.window.more.button.move", anchor.capitalizedDisplayName)) {
        override fun actionPerformed(e: AnActionEvent) {
          ToolWindowManagerEx.getInstanceEx(e.project ?: return).setMoreButtonSide(anchor)
        }
      })
    }

    return group
  }

  private var myDragState = false

  fun setDragState(state: Boolean) {
    myDragState = state
    resetMouseState()
    revalidate()
    repaint()
  }

  override fun paint(g: Graphics?) {
    if (!myDragState) {
      super.paint(g)
    }
  }

  override fun isAvailable(project: Project): Boolean {
    return super.isAvailable(project) && ToolWindowManagerEx.getInstanceEx(
      project).getMoreButtonSide() == side
  }

  override fun checkSkipPressForEvent(e: MouseEvent) = e.button != MouseEvent.BUTTON1

  fun paintDraggingButton(g: Graphics) {
    val areaSize = size.also {
      JBInsets.removeFrom(it, insets)
      JBInsets.removeFrom(it, SquareStripeButtonLook.ICON_PADDING)
    }

    val rect = Rectangle(areaSize)
    buttonLook.paintLookBackground(g, rect, JBUI.CurrentTheme.ActionButton.pressedBackground())
    icon.let {
      val x = (areaSize.width - it.iconWidth) / 2
      val y = (areaSize.height - it.iconHeight) / 2
      buttonLook.paintIcon(g, this, it, x, y)
    }

    buttonLook.paintLookBorder(g, rect, JBUI.CurrentTheme.ActionButton.pressedBorder())
  }
}

private fun createPresentation(): Presentation {
  val presentation = Presentation()
  presentation.icon = scaleIcon()
  presentation.isEnabledAndVisible = true
  return presentation
}

private fun scaleIcon(): Icon {
  return loadIconCustomVersionOrScale(
    icon = AllIcons.Actions.MoreHorizontal as ScalableIcon,
    size = JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconSize()
  )
}

private fun createAction(toolWindowToolbar: ToolWindowToolbar): DumbAwareAction {
  return object : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val actions = ToolWindowsGroup.getToolWindowActions(e.project ?: return, true)
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(null, DefaultActionGroup(actions), e.dataContext, null, true)
      popup.setMinimumSize(Dimension(300, -1))


      val moreSquareStripeButton = toolWindowToolbar.moreButton
      popup.show(RelativePoint(toolWindowToolbar, Point(toolWindowToolbar.width, moreSquareStripeButton.y)))
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
  }
}

internal abstract class MoreSquareStripeButtonBase(action: AnAction)
  : ActionButton(action, createPresentation(), ActionPlaces.TOOLWINDOW_TOOLBAR_BAR,
                 { JBUI.CurrentTheme.Toolbar.stripeToolbarButtonSize() }) {

  init {
    setLook(SquareStripeButtonLook(this))
  }

  override fun update() {
    super.update()
    val project = dataContext.getData(CommonDataKeys.PROJECT)
    if (project == null) {
      return
    }

    val available = isAvailable(project)

    myPresentation.isEnabledAndVisible = available
    isEnabled = available
    isVisible = available
  }

  protected open fun isAvailable(project: Project) = ToolWindowsGroup.getToolWindowActions(project, true).isNotEmpty()

  abstract val side: ToolWindowAnchor

  override fun updateToolTipText() {
    HelpTooltip()
      .setTitle(UIBundle.message("tool.window.new.stripe.more.title"))
      .setLocation(when(side) {
        ToolWindowAnchor.LEFT -> HelpTooltip.Alignment.RIGHT
        ToolWindowAnchor.TOP -> HelpTooltip.Alignment.BOTTOM
        else -> HelpTooltip.Alignment.LEFT
      })
      .setInitialDelay(0).setHideDelay(0)
      .installOn(this)
  }

  override fun updateUI() {
    super.updateUI()
    myPresentation.icon = scaleIcon()
  }
}
