// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi.Companion.SAVED_PATCHES_UI_PLACE
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import com.intellij.vcs.commit.CommitActionsPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.CompoundBorder

@ApiStatus.Internal
object SavedPatchesComponents {
  fun createBottomComponent(bottomToolbar: ActionToolbar): JComponent {
    val bottomPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
      border = CompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                              JBUI.Borders.empty(7, 5))
    }
    bottomPanel.add(bottomToolbar.component)
    bottomPanel.add(JLabel(AllIcons.General.ContextHelp).apply {
      border = JBUI.Borders.empty(1)
      toolTipText = VcsBundle.message("saved.patch.apply.pop.help.tooltip")
    })
    return bottomPanel
  }

  fun buildBottomToolbar(
    patchesTree: SavedPatchesTree,
    selectedProvider: () -> SavedPatchesProvider<*>,
    parentComponent: JComponent,
    parentDisposable: Disposable
  ): ActionToolbar {
    val applyAction = object : JButtonActionWrapper(VcsBundle.message("saved.patch.apply.action"), true) {
      override fun getDelegate(): AnAction {
        return selectedProvider().applyAction
      }
    }.apply {
      registerCustomShortcutSet(CommitActionsPanel.DEFAULT_COMMIT_ACTION_SHORTCUT, parentComponent, parentDisposable)
    }
    val popAction = object : JButtonActionWrapper(VcsBundle.message("saved.patch.pop.action"), false) {
      override fun getDelegate(): AnAction {
        return selectedProvider().popAction
      }
    }
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(applyAction)
    toolbarGroup.add(popAction)
    val toolbar = ActionManager.getInstance().createActionToolbar(SAVED_PATCHES_UI_PLACE, toolbarGroup, true)
    toolbar.targetComponent = patchesTree
    return toolbar
  }
}