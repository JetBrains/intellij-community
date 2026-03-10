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
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.DEFAULT_ACTION
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.merge.MergeConflictIterativeDataHolder
import com.intellij.openapi.vcs.merge.MergeConflictsTreeTable
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Graphics
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.JRootPane
import javax.swing.plaf.LayerUI
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer

internal class IterativeMergeFlowDelegate(
  private val project: Project,
  private val iterativeDataHolder: MergeConflictIterativeDataHolder,
  private val table: MergeConflictsTreeTable,
  private val columnNames: List<String>,
  private val mergeDialogCustomizer: MergeDialogCustomizer,
  private val rootPane: JRootPane,
  private val files: List<VirtualFile>,
  private val onClose: () -> Unit,
  private val acceptForResolution: (MergeSession.Resolution) -> Unit,
  private val showMergeDialog: () -> Unit,
  private val toggleGroupByDirectory: (Boolean) -> Unit,
  private val resolveAutomatically: () -> Unit,
  private val getGroupByDirectory: () -> Boolean,
  private val updateTable: () -> Unit,
) : MergeFlowDelegate {

  private lateinit var resolveAutomaticallyButton: JButton
  private lateinit var acceptYoursButton: JButton
  private lateinit var acceptTheirsButton: JButton
  private lateinit var mergeButton: JButton
  private var wasResolveAutomaticallyPressedOnce = false
  private var isResolveAutomaticallyPressed = false
  private var isResolvingConflicts = false

  override fun createCenterPanel(): JComponent {
    table.toolTipTextProvider = { file ->
      iterativeDataHolder.getMergeConflictModel(file)?.let {
        VcsBundle.message("multiple.file.iterative.merge.tooltip", it.getResolvedChanges().size, it.getAllChanges().size)
      }
    }
    table.installTableContextMenu()
    table.installNameDecorator {
      iterativeDataHolder.getMergeConflictModel(it)?.let { model ->
        VcsBundle.message("multiple.file.iterative.merge.files.resolved.changes.count",
                          model.getResolvedChanges().size,
                          model.getAllChanges().size)
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
      }.customize(UnscaledGapsY(bottom = 24, top = 12))
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          resolveAutomaticallyButton =
            button(VcsBundle.message(if (isResolvingConflicts) "multiple.file.merge.dialog.progress.title.resolving.conflicts"
                                     else "multiple.file.iterative.merge.resolve.automatically"),
                   actionListener = { onResolveAutomaticallyClick() }).applyToComponent {
              icon = AllIcons.Diff.MagicResolve
            }.align(AlignX.LEFT).customize(UnscaledGaps(left = 2)).component

        cell(createToolbar().component)
          .align(AlignX.RIGHT)
          .customize(UnscaledGaps(right = 4, top = 4, bottom = 4))
      }
        row {
          scrollCell(JLayer(table, DisabledStateLayerUI(table)))
            .align(Align.FILL)
            .resizableColumn()
        }.resizableRow()
      }
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

  private lateinit var state: IterativeMergeDialogState
  override fun onTreeChanged(selectedFiles: List<VirtualFile>, unmergeableFileSelected: Boolean, unacceptableFileSelected: Boolean) {
    state = IterativeMergeDialogState(
      selectedFiles = selectedFiles,
      unmergeableFileSelected = unmergeableFileSelected,
      unacceptableFileSelected = unacceptableFileSelected,
      resolvedFilesSelected = selectedFiles.any { iterativeDataHolder.isFileResolved(it) },
      onlyRevertableFilesSelected = selectedFiles.isNotEmpty() && selectedFiles.all {
        iterativeDataHolder.getMergeConflictModel(it)?.getResolvedChanges()?.isNotEmpty() == true
      })
    updateResolveAutomaticallyButtonState()
  }

  private fun updateResolveAutomaticallyButtonState() {
    val autoResolvableFiles =
      files.any { iterativeDataHolder.getMergeConflictModel(it)?.getAutoResolvableChanges()?.isNotEmpty() == true }
    val allFilesResolved = files.all { iterativeDataHolder.getMergeConflictModel(it)?.getUnresolvedChanges()?.isEmpty() == true }
    resolveAutomaticallyButton.apply {
      // Initially enable the button regardless of resolvable changes
      isEnabled = when {
        autoResolvableFiles -> true
        allFilesResolved -> false
        !wasResolveAutomaticallyPressedOnce -> true
        isResolveAutomaticallyPressed -> false
        isResolvingConflicts -> false
        else -> true
      }
      icon = if (isResolvingConflicts) AnimatedIcon.Default() else AllIcons.Diff.MagicResolve
      text = if (isResolvingConflicts) VcsBundle.message("multiple.file.merge.dialog.progress.title.resolving.conflicts")
      else VcsBundle.message("multiple.file.iterative.merge.resolve.automatically")

      toolTipText =
        if (!isEnabled && !isResolvingConflicts) VcsBundle.message("multiple.file.iterative.merge.resolve.automatically.disabled.tooltip") else null
    }

    table.isEnabled = !isResolvingConflicts
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
      if (resolvedFiles.isNotEmpty() || wasResolveAutomaticallyPressedOnce) {
        insertSubtreeRoot(resolvedNode)
        insertFilesIntoNode(resolvedFiles, resolvedNode)
      }
    }.build()
  }

  private fun TreeTable.installTableContextMenu() {
    val group = DefaultActionGroup().apply {
      add(object : DumbAwareAction(VcsBundle.message("multiple.file.iterative.merge.accept", columnNames[1])) {
        override fun actionPerformed(e: AnActionEvent) {
          acceptForResolution(MergeSession.Resolution.AcceptedYours)
        }

        override fun update(e: AnActionEvent) {
          super.update(e)
          e.presentation.isEnabled = !state.unacceptableFileSelected && !state.resolvedFilesSelected
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      })
      add(object : DumbAwareAction(VcsBundle.message("multiple.file.iterative.merge.accept", columnNames[2])) {
        override fun actionPerformed(e: AnActionEvent) {
          acceptForResolution(MergeSession.Resolution.AcceptedTheirs)
        }

        override fun update(e: AnActionEvent) {
          super.update(e)
          e.presentation.isEnabled = !state.unacceptableFileSelected && !state.resolvedFilesSelected
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      })
      addSeparator()
      add(object : DumbAwareAction(VcsBundle.message("multiple.file.iterative.merge.revert.resolution"),
                                   VcsBundle.message("multiple.file.iterative.merge.revert.resolution"),
                                   AllIcons.Actions.Rollback) {
        override fun actionPerformed(e: AnActionEvent) {
          val confirmed = MessageDialogBuilder
            .yesNo(VcsBundle.message("multiple.file.iterative.merge.revert.confirmation.title"),
                   VcsBundle.message("multiple.file.iterative.merge.revert.confirmation.message", state.selectedFiles.size))
            .yesText(CommonBundle.message("button.revert"))
            .noText(CommonBundle.getCancelButtonText())
            .icon(Messages.getQuestionIcon())
            .ask(project)
          if (confirmed) {
            iterativeDataHolder.removeFiles(state.selectedFiles)
            isResolveAutomaticallyPressed = false
            updateTable()
          }
        }

        override fun update(e: AnActionEvent) {
          super.update(e)
          e.presentation.isEnabled = state.onlyRevertableFilesSelected
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      })
    }
    PopupHandler.installPopupMenu(this, group, ActionPlaces.POPUP)
  }

  private fun onResolveAutomaticallyClick() {
    wasResolveAutomaticallyPressedOnce = true
    isResolveAutomaticallyPressed = true
    isResolvingConflicts = true
    updateResolveAutomaticallyButtonState()
    try {
      resolveAutomatically()
    }
    catch (e: ProcessCanceledException) {
      isResolveAutomaticallyPressed = false
      throw e
    }
    finally {
      isResolvingConflicts = false
      updateResolveAutomaticallyButtonState()
    }
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

private fun TreeTable.installNameDecorator(extra: (VirtualFile) -> String?) {
  val original = tree.cellRenderer
  tree.cellRenderer = TreeCellRenderer { tree, value, selected, expanded, leaf, row, hasFocus ->
    val component = original.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    if (component is SimpleColoredComponent && value is DefaultMutableTreeNode) {
      val virtualFile = value.userObject as? VirtualFile
      val extraText = virtualFile?.let(extra)
      if (!extraText.isNullOrBlank()) {
        component.append(" ")
        component.append(extraText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
    component
  }
}

private data class IterativeMergeDialogState(
  val selectedFiles: List<VirtualFile>,
  val unmergeableFileSelected: Boolean,
  val unacceptableFileSelected: Boolean,
  val resolvedFilesSelected: Boolean,
  val onlyRevertableFilesSelected: Boolean,
)

private class DisabledStateLayerUI(private val table: MergeConflictsTreeTable) : LayerUI<MergeConflictsTreeTable>() {
  override fun paint(g: Graphics, layer: JComponent) {
    super.paint(g, layer)
    if (!table.isEnabled) {
      g.color = JBUI.CurrentTheme.Banner.INFO_BACKGROUND
      GraphicsUtil.paintWithAlpha(g, 0.4f) {
        g.fillRect(0, 0, layer.width, layer.height)
      }
    }
  }
}