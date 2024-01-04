// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.getHeaderBackgroundColor
import com.intellij.ui.ColorUtil
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JComponent

class GitMergeRebaseWidget : DecorativeElement(), DumbAware, CustomComponentAction {
  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val component = createToolbar().component
    component.border = JBUI.Borders.empty()
    return component
  }
}

private fun createToolbar(): ActionToolbar {
  return ActionManager.getInstance().createActionToolbar(
    GIT_MERGE_REBASE_WIDGET_PLACE,
    ActionManager.getInstance().getAction(GIT_MERGE_REBASE_WIDGET_GROUP) as ActionGroup,
    true
  ).apply {
    if (this is ActionToolbarImpl) {
      isOpaque = false

      targetComponent = null
      layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY

      setMinimumButtonSize { ActionToolbar.experimentalToolbarMinimumButtonSize() }
      setCustomButtonLook(ColoredActionButtonLook())
    }
  }
}

private val RESOLVE_ACTIONS: Set<String> = setOf(
  "Git.ResolveConflicts",
  "Git.Rebase.Continue"
)

private val ABORT_ACTIONS: Set<String> = setOf(
  "Git.Rebase.Abort",
  "Git.Merge.Abort",
  "Git.CherryPick.Abort",
  "Git.Revert.Abort"
)

private class ColoredActionButtonLook : HeaderToolbarButtonLook() {
  override fun getStateBackground(component: JComponent, state: Int): Color? {
    val actionButton = (component as? ActionButton) ?: return null
    val id = actionButton.action.id

    val buttonColor = getActionButtonBackgroundColor(id)

    if (buttonColor == null) {
      return getHeaderBackgroundColor(component, state)
    }

    return when (state) {
      ActionButtonComponent.NORMAL -> buttonColor
      ActionButtonComponent.PUSHED -> ColorUtil.alphaBlending(JBUI.CurrentTheme.RunWidget.PRESSED_BACKGROUND, buttonColor)
      else -> ColorUtil.alphaBlending(JBUI.CurrentTheme.RunWidget.HOVER_BACKGROUND, buttonColor)
    }
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon) {
    val iconPos = getIconPosition(actionButton, icon)
    paintIcon(g, actionButton, icon, iconPos.x, iconPos.y)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon, x: Int, y: Int) {
    val ab = (actionButton as? ActionButton)
    val id = ab?.action?.id

    val buttonColor = getActionButtonBackgroundColor(id)
    val newIconColor = getActionButtonIconColor(buttonColor)

    paintIconImpl(g, ab, toStrokeIcon(icon, newIconColor), x, y)
  }

  // todo: add colors separated from RunWidget
  private fun getActionButtonIconColor(buttonColor: Color?): Color = when {
    buttonColor != null -> JBUI.CurrentTheme.RunWidget.RUNNING_ICON_COLOR
    else -> JBUI.CurrentTheme.RunWidget.ICON_COLOR
  }

  // todo: add colors separated from RunWidget
  private fun getActionButtonBackgroundColor(id: String?): Color? = when {
    RESOLVE_ACTIONS.contains(id) -> JBUI.CurrentTheme.RunWidget.RUNNING_BACKGROUND
    ABORT_ACTIONS.contains(id) -> JBUI.CurrentTheme.RunWidget.STOP_BACKGROUND
    else -> null
  }
}

private val AnAction.id: String?
  get() = ActionManager.getInstance().getId(this)


private const val GIT_MERGE_REBASE_WIDGET_GROUP = "GitMergeRebaseWidgetGroup"

const val GIT_MERGE_REBASE_WIDGET_PLACE = "GitMergeRebaseWidgetPlace"

