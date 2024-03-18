package com.intellij.completion.ml.actions

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.experiment.ClientExperimentStatus
import com.intellij.completion.ml.util.language
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

class DumpMLExperimentsGroupsAction : AnAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    e.presentation.isEnabled = editor != null && LookupManager.getActiveLookup(editor) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl ?: return
    val language = lookup.language() ?: return
    val groups = ClientExperimentStatus().extractGroupsMapping(language)
    CopyPasteManager.getInstance().setContents(StringSelection(groups.joinToString(separator = "\n")))
  }
}
