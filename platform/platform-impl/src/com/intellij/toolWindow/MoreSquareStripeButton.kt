// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.AbstractSquareStripeButton
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.Icon

internal class MoreSquareStripeButton(toolWindowToolbar: ToolWindowToolbar,
                                      override val side: ToolWindowAnchor,
                                      vararg moveTo: ToolWindowAnchor) :
  AbstractMoreSquareStripeButton(ShowMoreToolWindowsAction(toolWindowToolbar)) {

  init {
    AccessibleContextUtil.setName(this, UIBundle.message("more.button.accessible.name"))
    doInit { createPopupGroup(moveTo) }
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
    return super.isAvailable(project) && ToolWindowManagerEx.getInstanceEx(project).getMoreButtonSide() == side
  }

  override fun checkSkipPressForEvent(e: MouseEvent) = e.button != MouseEvent.BUTTON1
}

private fun createPresentation(): Presentation {
  val presentation = Presentation()
  presentation.setIconSupplier(SynchronizedClearableLazy(::scaleIcon))
  presentation.isEnabledAndVisible = true
  return presentation
}

private fun scaleIcon(): Icon {
  return loadIconCustomVersionOrScale(
    icon = AllIcons.Actions.MoreHorizontal as ScalableIcon,
    size = JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconSize()
  )
}

@ApiStatus.Internal
class ShowMoreToolWindowsAction(private val toolWindowToolbar: ToolWindowToolbar) : DumbAwareAction(), Toggleable {
  val isOnTheLeft: Boolean = toolWindowToolbar is ToolWindowLeftToolbar

  private val minPopupWidth: Int
    get() = JBUI.scale(300)

  override fun actionPerformed(e: AnActionEvent) {
    val popup = createPopup(e) ?: return
    showPopup(popup)
  }

  fun createPopup(e: AnActionEvent): JBPopup? {
    val actions = ToolWindowsGroup.getToolWindowActions(e.project ?: return null, true)
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(null, DefaultActionGroup(actions), e.dataContext, null, true)
    popup.setMinimumSize(Dimension(minPopupWidth, -1))
    PopupUtil.addToggledStateListener(popup, e.presentation)
    return popup
  }

  fun showPopup(popup: JBPopup) {
    val moreSquareStripeButton = toolWindowToolbar.moreButton
    val x = if (isOnTheLeft) toolWindowToolbar.width else -minPopupWidth
    popup.show(RelativePoint(toolWindowToolbar, Point(x, moreSquareStripeButton.y)))
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

internal abstract class AbstractMoreSquareStripeButton(action: AnAction, minimumSize: Supplier<Dimension>? = null) : AbstractSquareStripeButton(action, createPresentation(), minimumSize) {
  override fun update() {
    super.update()
    updateState(dataContext.getData(CommonDataKeys.PROJECT))
  }

  fun updateState(project: Project?) {
    val available = project != null && isAvailable(project)

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
