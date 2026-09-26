// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.toolbar

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Dimension
import java.awt.event.InputEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max

/**
 * Puts the Search Everywhere input field on a toolbar.
 *
 * The user adds the action to a toolbar in Settings | Appearance & Behavior | Menus and Toolbars.
 * The toolbar then shows a [SeToolbarSearchField]. Shift+Shift moves the focus to the field.
 */
@Internal
class SearchEverywhereComponentAction : DumbAwareAction(), CustomComponentAction, ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    performSearchEverywhereAction(e.dataContext, e.place, e.uiKind, e.inputEvent)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return SeToolbarSearchFieldPanel(SeToolbarSearchField(place))
  }
}

/**
 * The toolbar slot of the field. The slot has the height of a toolbar button, and the field is centered in it.
 * The explicit layout avoids the rounding of a layout manager, so the field sits on the same line as the other widgets.
 */
private class SeToolbarSearchFieldPanel(private val field: SeToolbarSearchField) : JPanel(null) {
  init {
    isOpaque = false
    add(field)
  }

  override fun getPreferredSize(): Dimension = Dimension(field.preferredSize.width, slotHeight())

  override fun getMinimumSize(): Dimension = Dimension(field.minimumSize.width, slotHeight())

  override fun doLayout() {
    val fieldHeight = field.preferredSize.height
    field.setBounds(0, (height - fieldHeight) / 2, width, fieldHeight)
  }

  private fun slotHeight(): Int = max(ActionToolbar.experimentalToolbarMinimumButtonSize().height, field.preferredSize.height)
}

/** Runs the Search Everywhere action with [dataContext]. The action focuses the toolbar field of the window when there is one. */
internal fun performSearchEverywhereAction(dataContext: DataContext, place: String, uiKind: ActionUiKind, inputEvent: InputEvent?) {
  val action = ActionManager.getInstance().getAction(IdeActions.ACTION_SEARCH_EVERYWHERE) ?: return
  val event = AnActionEvent.createEvent(action, dataContext, null, place, uiKind, inputEvent)
  ActionUtil.performAction(action, event)
}
