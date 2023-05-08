package com.intellij.toolWindow

import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.SquareAnActionButton
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseEvent
import javax.swing.JComponent

class StripeActionGroup: ActionGroup(), DumbAware {
  private val myFactory: Map<ToolWindowImpl, AnAction> = ConcurrentFactoryMap.create(::createAction) {
    ContainerUtil.createConcurrentWeakKeyWeakValueMap()
  }
  private val myMore = MyMoreAction()

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val twm = e?.project?.let { ToolWindowManager.getInstance(it) } ?: return emptyArray()
    val toolWindows = twm.toolWindowIds.mapNotNullTo(ArrayList()) { twm.getToolWindow(it) as? ToolWindowImpl }
    toolWindows.sortBy(::getOrder)
    val actions = toolWindows.mapNotNullTo(ArrayList(), myFactory::get)
    actions += myMore
    return actions.toTypedArray()
  }

  private fun getOrder(tw: ToolWindowImpl): Int =
    tw.windowInfo.run {
      when (anchor) {
        ToolWindowAnchor.LEFT -> 0 + order
        ToolWindowAnchor.TOP -> 100 + order
        ToolWindowAnchor.BOTTOM -> 200 + order
        ToolWindowAnchor.RIGHT -> 300 + order
        else -> -1
      }
    }

  private fun createAction(tw: ToolWindowImpl) = MyButtonAction(tw)

  private class MyButtonAction(tw: ToolWindowImpl): SquareAnActionButton(tw), CustomComponentAction {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return object : SquareStripeButton(this@MyButtonAction, window) {
        override fun isFocused(): Boolean = false
        override fun addNotify() {
          super.addNotify()
          window.project.service<ButtonsRepaintService>().trackButton(this)
        }

        override fun removeNotify() {
          super.removeNotify()
          window.project.service<ButtonsRepaintService>().unTrackButton(this)
        }

        override fun setLook(look: ActionButtonLook?) {
          if (look is SquareStripeButtonLook) super.setLook(look)
        }

        override fun getAlignment(anchor: ToolWindowAnchor, splitMode: Boolean): HelpTooltip.Alignment {
          return HelpTooltip.Alignment.BOTTOM
        }
      }
    }
  }

  private class MyMoreAction: DumbAwareAction(), CustomComponentAction {
    override fun actionPerformed(e: AnActionEvent) {

    }

    private fun getChildren(e: AnActionEvent?): List<AnAction> {
      val project = e?.project ?: return emptyList()
      return ToolWindowsGroup.getToolWindowActions(project, true)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return object : MoreSquareStripeButtonBase(this) {
        override fun setLook(look: ActionButtonLook?) {
          if (look is SquareStripeButtonLook) super.setLook(look)
        }

        override val side: ToolWindowAnchor
          get() = ToolWindowAnchor.TOP

        override fun actionPerformed(event: AnActionEvent) {
          HelpTooltip.hide(this)
          showActionGroupPopup(DefaultActionGroup(getChildren(event)), event)
        }
      }
    }
  }


  @Service(Service.Level.PROJECT)
  private class ButtonsRepaintService(project: Project): Disposable {
    private val buttons = ContainerUtil.createWeakSet<SquareStripeButton>()
    init {
      project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
        override fun stateChanged(toolWindowManager: ToolWindowManager) {
          UIUtil.invokeAndWaitIfNeeded(this@ButtonsRepaintService::repaintButtons)
        }
      })
    }

    @RequiresEdt
    fun trackButton(btn: SquareStripeButton) {
      buttons.add(btn)
    }

    @RequiresEdt
    fun unTrackButton(btn: SquareStripeButton) {
      buttons.remove(btn)
    }

    @RequiresEdt
    fun repaintButtons() {
      for (button in buttons.toList()) {
        button.repaint()
      }
    }

    override fun dispose() {
    }
  }
}