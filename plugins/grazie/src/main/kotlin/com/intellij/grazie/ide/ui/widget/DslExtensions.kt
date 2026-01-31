package com.intellij.grazie.ide.ui.widget

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.not
import com.intellij.ui.popup.AbstractPopup
import org.jetbrains.annotations.NonNls
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent

internal fun Panel.panel(visibleIf: ComponentPredicate, block: Panel.() -> Unit): Panel = panel(block).visibleIf(visibleIf)

internal fun Panel.either(isLeft: ComponentPredicate, left: Panel.() -> Unit, right: Panel.() -> Unit): Panel =
  panel {
    panel(left).visibleIf(isLeft)
    panel(right).visibleIf(!isLeft)
  }

internal fun Panel.rowGroup(init: Panel.() -> Unit): Row = row { panel { init(this) } }

internal fun Row.panel(visibleIf: ComponentPredicate, block: Panel.() -> Unit): Panel = panel(init = block).visibleIf(visibleIf)

internal fun Panel.row(visibleIf: ComponentPredicate, block: Row.() -> Unit): Row = row(init = block).visibleIf(visibleIf)

internal fun JBPopup.adjustLocation(component: JComponent, verticalOffset: Int = 0, horizontalOffset: Int = 0) {
  val point = RelativePoint(component, Point())
  val adComponentHeight = (this as? AbstractPopup)?.adComponentHeight ?: 0
  val location = Point(point.screenPoint).apply {
    x -= size.width - component.width - horizontalOffset
    y -= size.height + adComponentHeight + verticalOffset
  }
  setLocation(location)
}

internal fun JBPopup.showAboveOnTheLeft(component: JComponent, verticalOffset: Int = 0, horizontalOffset: Int = 0) {
  addListener(object : JBPopupListener {
    override fun beforeShown(event: LightweightWindowEvent) {
      val popup = event.asPopup()
      popup.adjustLocation(component, verticalOffset, horizontalOffset)
    }
  })
  show(component)
}

fun Row.actionsButtonWithoutDropdownIcon(
  vararg actions: AnAction,
  icon: Icon = AllIcons.General.GearPlain,
): Cell<ActionButton> {
  return buttonWithActionPopup(actions = actions, icon = icon).applyToComponent {
    action.templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
  }
}

private fun Row.buttonWithActionPopup(
  vararg actions: AnAction,
  @NonNls actionPlace: String = ActionPlaces.UNKNOWN,
  icon: Icon = AllIcons.General.GearPlain,
): Cell<ActionButton> {
  val actionGroup = PopupActionGroup(actions)
  actionGroup.templatePresentation.icon = icon
  return cell(ActionButton(
    actionGroup,
    actionGroup.templatePresentation.clone(),
    actionPlace,
    ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
  ))
}

internal class PopupActionGroup(private val actions: Array<out AnAction>) : ActionGroup(), DumbAware {
  init {
    isPopup = true
    templatePresentation.isPerformGroup = actions.isNotEmpty()
  }

  override fun getChildren(event: AnActionEvent?): Array<out AnAction> = actions

  override fun actionPerformed(event: AnActionEvent) {
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      null,
      this,
      event.dataContext,
      JBPopupFactory.ActionSelectionAid.MNEMONICS,
      true
    )
    PopupUtil.showForActionButtonEvent(popup, event)
  }
}
