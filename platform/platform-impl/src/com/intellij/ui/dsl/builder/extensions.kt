// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.components.TabbedPaneHeader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Dimension
import java.io.Serial
import javax.swing.Icon
import javax.swing.JPanel

// This file contains extension functions that relates to platform-impl package only.
// Common platform related functionality should be put in correspondent module


fun Row.actionButton(action: AnAction, @NonNls actionPlace: String = ActionPlaces.UNKNOWN,
                     sinkExtender: (DataSink) -> Unit = {}): Cell<ActionButton> {
  val component = DataAwareActionButton(action, action.templatePresentation.clone(), actionPlace, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE, sinkExtender)
  return cell(component)
}

/**
 * Creates an [ActionButton] with [icon], optional [title], menu with provided [actions], with a possibility to hide disabled actions via [showDisabledActions].
 *
 * It is also possible to extend ActionButton with the UiData for the specific contexts.
 *
 * ```
 * val actionManager = ActionManager.getInstance()
 *
 * actionsButton(
 *     actions = listOfNotNull(
 *         actionManager.getAction("action1"),
 *         actionManager.getAction("action2"),
 *         actionManager.getAction("action3"),
 *     ).toTypedArray(),
 *     ActionPlaces.TOOLWINDOW_CONTENT
 * ) {
 *     it[UiConstants.DataKeys.Key1] = value1
 *     it[UiConstants.DataKeys.Key2] = value2
 * }
 * ```
 */
fun Row.actionsButton(
  vararg actions: AnAction,
  actionPlace: String = ActionPlaces.UNKNOWN,
  icon: Icon = AllIcons.General.GearPlain,
  title: String? = null,
  showDisabledActions: Boolean = true,
  sinkExtender: (DataSink) -> Unit = {},
): Cell<ActionButton> {
  val actionGroup = PopupActionGroup(arrayOf(*actions), title, icon, showDisabledActions)
  val presentation = actionGroup.templatePresentation.clone()
  val component = DataAwareActionButton(actionGroup, presentation, actionPlace, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE, sinkExtender)

  return cell(component)
}

/**
 * Creates JBTabbedPane which shows only tabs without tab content. To add a new tab call something like
 * ```
 * JBTabbedPane.addTab(tab.name, JPanel())
 * ```
 */
@ApiStatus.Experimental
fun Row.tabbedPaneHeader(items: Collection<String> = emptyList()): Cell<JBTabbedPane> {
  val tabbedPaneHeader = TabbedPaneHeader()
  for (item in items) {
    tabbedPaneHeader.add(item, JPanel())
  }
  return cell(tabbedPaneHeader)
}

private class DataAwareActionButton(
  action: AnAction,
  presentation: Presentation?,
  place: String,
  minimumSize: Dimension,
  private val sinkExtender: (DataSink) -> Unit,
) : ActionButton(action, presentation, place, minimumSize), UiDataProvider {
  override fun uiDataSnapshot(sink: DataSink) {
    sinkExtender(sink)
  }

  companion object {
    @Serial
    private const val serialVersionUID: Long = 4700000688059262171L
  }
}

private class PopupActionGroup(
  private val actions: Array<AnAction>,
  private val title: String?,
  private val icon: Icon,
  private val showDisabledActions: Boolean,
) : ActionGroup(), DumbAware {
  init {
    isPopup = true
    templatePresentation.isPerformGroup = actions.isNotEmpty()
    templatePresentation.icon = icon
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = actions

  override fun actionPerformed(e: AnActionEvent) {
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      title, this, e.dataContext,
      JBPopupFactory.ActionSelectionAid.MNEMONICS, showDisabledActions
    )
    PopupUtil.showForActionButtonEvent(popup, e)
  }
}
