// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vfs.newvfs.VfsImplUtil

internal class DecompilerInEditorActionGroup(private val settings: IdeaDecompilerSettings) : ActionGroup() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.text = IdeaDecompilerBundle.message("action.change.decompiler.preset")
    event.presentation.icon = AllIcons.General.Gear
    event.presentation.isPopupGroup = true
  }

  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val actions = DecompilerPreset.entries
      .map { preset -> ChangeDecompilerPresetAction(preset) }
      .toList<AnAction>()
      .toTypedArray()

    return arrayOf(
      Separator.create(IdeaDecompilerBundle.message("decompiler.preset.title")),
      *actions,
    )
  }

  private inner class ChangeDecompilerPresetAction(private val preset: DecompilerPreset) : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
      super.update(event)
      event.presentation.text = preset.description
    }

    override fun isSelected(e: AnActionEvent): Boolean = settings.state.preset == preset

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val newState = IdeaDecompilerSettings.State.fromPreset(preset)
      settings.loadState(newState)
      val virtualFile = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
      DecompilerPresetChangedCollector.decompilerPresetChanged(preset)
      VfsImplUtil.forceSyncRefresh(virtualFile)
    }
  }
}
