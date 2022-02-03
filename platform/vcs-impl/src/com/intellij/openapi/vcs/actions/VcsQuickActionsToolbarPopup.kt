// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.GotoClassPresentationUpdater.getActionTitlePluralized
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsActions
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.ui.JBInsets
import java.awt.Color
import java.awt.Insets
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.FocusManager
import javax.swing.JComponent

/**
 * Vcs quick popup action which is shown in the new toolbar and has two different presentations
 * depending on vcs repo availability
 */
open class VcsQuickActionsToolbarPopup : IconWithTextAction(), CustomComponentAction, DumbAware {
  inner class MyActionButtonWithText(
    action: AnAction,
    presentation: Presentation,
    place: String,
  ) : ActionButtonWithText(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {

    override fun getInactiveTextColor(): Color = foreground
    override fun getInsets(): Insets = JBInsets(0, 0, 0, 0)
    override fun updateToolTipText() {
      val shortcut = KeymapUtil.getShortcutText("Vcs.QuickListPopupAction")
      val classesTabName = java.lang.String.join("/", getActionTitlePluralized())
      if (Registry.`is`("ide.helptooltip.enabled")) {
        HelpTooltip.dispose(this)
        HelpTooltip()
          .setTitle(VcsBundle.message("Vcs.Toolbar.ShowMoreActions.description"))
          .setShortcut(shortcut)
          .installOn(this)
      }
      else {
        toolTipText = VcsBundle.message("Vcs.Toolbar.ShowMoreActions.description", shortcutText, classesTabName)
      }
    }

    fun getShortcut(): String {
      val shortcuts = KeymapUtil.getActiveKeymapShortcuts(VcsActions.VCS_OPERATIONS_POPUP).shortcuts
      return KeymapUtil.getShortcutsText(shortcuts)
    }
  }


  open fun getName(project: Project): String? {
    return null
  }

  protected fun updateVcs(project: Project?, e: AnActionEvent): Boolean {
    if (project == null || e.place !== ActionPlaces.MAIN_TOOLBAR || getName(project) == null ||
        !ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(getName(project))) {
      e.presentation.isEnabledAndVisible = false
      return false
    }
    return true
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun getInactiveTextColor(): Color {
        return foreground
      }

      override fun getInsets(): Insets {
        return JBInsets(0, 0, 0, 0)
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val group = DefaultActionGroup()
    CustomActionsSchema.getInstance().getCorrectedAction(VcsActions.VCS_OPERATIONS_POPUP)?.let {
      group.add(
        it)
    }
    if (group.childrenCount == 0) return
    val dataContext = DataManager.getInstance().getDataContext(FocusManager.getCurrentManager().focusOwner)
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      VcsBundle.message("action.Vcs.Toolbar.QuickListPopupAction.text"),
      group, dataContext, JBPopupFactory.ActionSelectionAid.NUMBERING, true, null, -1,
      { action: AnAction? -> true }, ActionPlaces.RUN_TOOLBAR_LEFT_SIDE)
    val component = e.inputEvent.component
    popup.showUnderneathOf(component)
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    if (e.project == null ||
        e.place !== ActionPlaces.MAIN_TOOLBAR || ProjectLevelVcsManager.getInstance(e.project!!).hasActiveVcss()) {
      presentation.isEnabledAndVisible = false
      return
    }
    presentation.isEnabledAndVisible = true
    presentation.icon = AllIcons.Vcs.BranchNode
    presentation.text = VcsBundle.message("action.Vcs.Toolbar.ShowMoreActions.text") + " "
  }

  companion object {
    private fun showPopup(e: AnActionEvent, popup: ListPopup) {
      val mouseEvent = e.inputEvent
      if (mouseEvent is MouseEvent) {
        val source = mouseEvent.getSource()
        if (source is JComponent) {
          val topLeftCorner = source.locationOnScreen
          val bottomLeftCorner = Point(topLeftCorner.x, topLeftCorner.y + source.height)
          popup.setLocation(bottomLeftCorner)
          popup.show(source)
        }
      }
    }
  }
}