// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge.flow

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.DEFAULT_ACTION
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.merge.MergeConflictIterativeDataHolder
import com.intellij.openapi.vcs.merge.MergeConflictsTreeTable
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.tree.DefaultTreeModel

internal class IterativeMergeFlowDelegate(
  private val iterativeDataHolder: MergeConflictIterativeDataHolder,
  private val table: MergeConflictsTreeTable,
  private val mergeDialogCustomizer: MergeDialogCustomizer,
  private val rootPane: JRootPane,
  private val files: List<VirtualFile>,
  private val onClose: () -> Unit,
  private val acceptForResolution: (MergeSession.Resolution) -> Unit,
  private val showMergeDialog: () -> Unit,
  private val toggleGroupByDirectory: (Boolean) -> Unit,
  private val resolveAutomatically: () -> Unit,
  private val getGroupByDirectory: () -> Boolean,
) : MergeFlowDelegate {

  private lateinit var resolveAutomaticallyButton: JComponent
  private lateinit var acceptYoursButton: JButton
  private lateinit var acceptTheirsButton: JButton
  private lateinit var mergeButton: JButton

  override fun createCenterPanel(): JComponent {
    table.toolTipTextProvider = { file ->
      iterativeDataHolder.getMergeConflictModel(file)?.let {
        VcsBundle.message("multiple.file.iterative.merge.tooltip", it.getResolvedChanges().size, it.getAllChanges().size)
      }
    }
    return panel {
      row {
        label(VcsBundle.message("merge.loading.merge.details")).applyToComponent {
          initOnShow("MultipleFileMergeDialog - Load Label") {
            @Suppress("HardCodedStringLiteral") // withContext loses the nls annotation
            val title = withContext(Dispatchers.Default) {
              mergeDialogCustomizer.getMultipleFileMergeDescription(files)
            }
            text = title
          }
        }
      }
      row {
        resolveAutomaticallyButton = button(VcsBundle.message("multiple.file.merge.resolve.automatically")) {
          resolveAutomatically()
        }.align(AlignX.LEFT).component

        cell(createToolbar().component)
          .align(AlignX.RIGHT)
      }
      row {
        scrollCell(table)
          .align(Align.FILL)
          .resizableColumn()
      }.resizableRow()
    }
  }

  private fun createToolbar(): ActionToolbar {
    val viewOptionsGroup = DefaultActionGroup(IdeBundle.message("group.view.options"), true).apply {
      templatePresentation.icon = AllIcons.Actions.Show
      add(object : DumbAwareToggleAction(VcsBundle.messagePointer("multiple.file.merge.group.by.directory.checkbox"),
                                         VcsBundle.messagePointer("multiple.file.merge.group.by.directory.checkbox"),
                                         AllIcons.Actions.ToggleVisibility) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent): Boolean = getGroupByDirectory()

        override fun setSelected(e: AnActionEvent, state: Boolean) {
          toggleGroupByDirectory(state)
        }
      })
    }

    val toolbarGroup = DefaultActionGroup().apply { add(viewOptionsGroup) }
    return ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, toolbarGroup, true)
      .apply {
        setTargetComponent(table)
      }
  }

  override fun createActions(): List<Action> {
    val cancelAction = object : AbstractAction(CommonBundle.getCloseButtonText()) {
      override fun actionPerformed(e: ActionEvent) {
        onClose()
      }
    }.apply {
      putValue(DEFAULT_ACTION, true)
    }
    val resolveAction = object : AbstractAction(VcsBundle.message("multiple.file.merge.resolve.manually")) {
      override fun actionPerformed(e: ActionEvent) {
        showMergeDialog()
      }
    }
    resolveAction.putValue(DEFAULT_ACTION, true)

    val acceptAndFinishAction = object : AbstractAction(VcsBundle.message("multiple.file.merge.accept.finish")) {
      override fun actionPerformed(e: ActionEvent) {
        showMergeDialog()
      }
    }.apply {
      isEnabled = false
    }
    return listOf(cancelAction, resolveAction, acceptAndFinishAction)
  }

  override fun onTreeChanged(selectedFiles: List<VirtualFile>, unmergeableFileSelected: Boolean, unacceptableFileSelected: Boolean) {
    val onlyResolvedFiles = selectedFiles.all { iterativeDataHolder.isFileResolved(it) }
    val haveSelection = selectedFiles.any()
    acceptYoursButton.isEnabled = haveSelection && !unacceptableFileSelected
    acceptTheirsButton.isEnabled = haveSelection && !unacceptableFileSelected
    mergeButton.isEnabled = haveSelection && !unmergeableFileSelected
    mergeButton.text =
      if (!onlyResolvedFiles || !haveSelection) VcsBundle.message("multiple.file.merge.merge") else VcsBundle.message("multiple.file.merge.open")
  }

  override fun buildTreeModel(
    project: Project?,
    grouping: ChangesGroupingPolicyFactory,
    unresolvedFiles: List<VirtualFile>,
  ): DefaultTreeModel {
    val resolvedFiles = iterativeDataHolder.getResolvedFiles()
    val unresolvedFiles = unresolvedFiles - resolvedFiles
    val unresolvedNode = ConflictsGroupNode(ConflictsNodeType.UNRESOLVED)
    val resolvedNode = ConflictsGroupNode(ConflictsNodeType.RESOLVED)

    return TreeModelBuilder(project, grouping).apply {
      if (unresolvedFiles.isNotEmpty()) {
        insertSubtreeRoot(unresolvedNode)
        insertFilesIntoNode(unresolvedFiles, unresolvedNode)
      }
      if (resolvedFiles.isNotEmpty()) {
        insertSubtreeRoot(resolvedNode)
        insertFilesIntoNode(resolvedFiles, resolvedNode)
      }
    }.build()
  }
}

private enum class ConflictsNodeType {
  UNRESOLVED,
  RESOLVED
}

private class ConflictsGroupNode(val type: ConflictsNodeType) : ChangesBrowserNode<ConflictsNodeType>(type) {
  override fun getTextPresentation(): String = when (type) {
    ConflictsNodeType.UNRESOLVED -> VcsBundle.message("changes.nodetitle.merge.dialog.unresolved")
    ConflictsNodeType.RESOLVED -> VcsBundle.message("changes.nodetitle.merge.dialog.resolved")
  }

  override fun shouldExpandByDefault(): Boolean = true
}