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
import com.intellij.openapi.ui.DialogWrapper
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
import com.intellij.openapi.vcs.merge.MergeUIUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayer
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.JTable
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.plaf.LayerUI
import javax.swing.table.TableCellRenderer
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

  private lateinit var descriptionLabel: JLabel
  private lateinit var resolveAutomaticallyButton: JButton
  private lateinit var reviewOrResolveButton: JButton
  private lateinit var acceptAndFinishButton: JButton
  private var wasResolveAutomaticallyPressedOnce = false
  private var isResolveAutomaticallyPressed = false
  private var isResolvingConflicts = false

  @Nls
  private var currentDescription: String = VcsBundle.message("merge.loading.merge.details")

  override fun createCenterPanel(): JComponent {
    table.installButtonRenderer(iterativeDataHolder, getSelectedFiles = { state.selectedFiles }) { _, column ->
      val resolution = when (column) {
        1 -> MergeSession.Resolution.AcceptedYours
        2 -> MergeSession.Resolution.AcceptedTheirs
        else -> error("Invalid column index: $column")
      }
      acceptForResolution(resolution)
    }
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
        descriptionLabel = label(currentDescription).component.apply {
          initOnShow("MultipleFileMergeDialog - Load Label") {
            @Suppress("HardCodedStringLiteral") // withContext loses the nls annotation
            currentDescription = withContext(Dispatchers.Default) {
              mergeDialogCustomizer.getMultipleFileMergeDescription(files)
            }.also {
              text = it
            }
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
    }.apply {
      // If the width is smaller than this, then buttons don't render properly
      minimumSize = JBUI.size(550, 240)

      preferredSize = JBUI.size(preferredSize.width, if (files.size <= 6) 400 else 500)
    }
  }

  override fun createSouthPanel(): JComponent = panel {
    val defaultSpacingConfiguration = IntelliJSpacingConfiguration()
    row {
      cell(JPanel()).resizableColumn()
      val closeAction = object : AbstractAction(CommonBundle.getCloseButtonText()) {
        override fun actionPerformed(e: ActionEvent) {
          val hasChanges = files.any { iterativeDataHolder.getMergeConflictModel(it)?.getResolvedChanges()?.isNotEmpty() == true }
          if (!hasChanges || MessageDialogBuilder.yesNo(VcsBundle.message("multiple.file.iterative.merge.close.confirmation.title"),
                                                        VcsBundle.message("multiple.file.iterative.merge.close.confirmation.message"))
              .yesText(VcsBundle.message("multiple.file.iterative.merge.close.confirmation.yes"))
              .noText(VcsBundle.message("multiple.file.iterative.merge.close.confirmation.no"))
              .ask(project)) {
            onClose()
          }
        }
      }
      cell(DialogWrapper.createJButtonForAction(closeAction, rootPane))
        .customize(UnscaledGaps(left = defaultSpacingConfiguration.segmentedButtonHorizontalGap))

      val acceptAndFinishAction = object : AbstractAction(VcsBundle.message("multiple.file.iterative.merge.accept.finish")) {
        override fun actionPerformed(e: ActionEvent) {
          onClose()
        }
      }
      acceptAndFinishButton = DialogWrapper.createJButtonForAction(acceptAndFinishAction, rootPane)
      cell(acceptAndFinishButton)
        .customize(UnscaledGaps(left = defaultSpacingConfiguration.segmentedButtonHorizontalGap))

      val reviewOrResolveAction = object : AbstractAction(VcsBundle.message("multiple.file.iterative.merge.resolve.manually")) {
        override fun actionPerformed(e: ActionEvent) {
          showMergeDialog()
        }
      }
      reviewOrResolveButton = DialogWrapper.createJButtonForAction(reviewOrResolveAction, rootPane)
      cell(reviewOrResolveButton)
        .customize(UnscaledGaps(left = defaultSpacingConfiguration.segmentedButtonHorizontalGap))
    }.customize(UnscaledGapsY(top = 32))
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

  // Our buttons are dynamic, cannot use the built-in action system
  override fun createActions(): List<Action> = emptyList()

  private lateinit var state: IterativeMergeDialogState
  override fun onTreeChanged(selectedFiles: List<VirtualFile>, unmergeableFileSelected: Boolean, unacceptableFileSelected: Boolean) {
    state = IterativeMergeDialogState(
      selectedFiles = selectedFiles,
      unmergeableFileSelected = unmergeableFileSelected,
      unacceptableFileSelected = unacceptableFileSelected,
      resolvedFilesSelected = selectedFiles.any { iterativeDataHolder.isFileResolved(it) },
      allSelectedFilesResolved = selectedFiles.all { iterativeDataHolder.isFileResolved(it) },
      onlyRevertableFilesSelected = selectedFiles.isNotEmpty() && selectedFiles.all {
        iterativeDataHolder.getMergeConflictModel(it)?.getResolvedChanges()?.isNotEmpty() == true
      },
      allFilesResolvedAndReviewed = files.all {
        val model = iterativeDataHolder.getMergeConflictModel(it)
        model?.wasReviewed == true && model.getUnresolvedChanges().isEmpty()
      })
    updateButtonsState()
  }

  private fun updateButtonsState() {
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

    reviewOrResolveButton.apply {
      isEnabled = table.isEnabled
      text = if (state.allSelectedFilesResolved) VcsBundle.message("multiple.file.iterative.merge.review.changes")
      else VcsBundle.message("multiple.file.iterative.merge.resolve.manually")
    }

    acceptAndFinishButton.isEnabled = table.isEnabled && files.all { iterativeDataHolder.isFileResolved(it) }

    if (state.allFilesResolvedAndReviewed) {
      reviewOrResolveButton.isVisible = false
      rootPane.defaultButton = acceptAndFinishButton
      descriptionLabel.text = VcsBundle.message("multiple.file.iterative.merge.all.reviews.resolved")
      descriptionLabel.icon = AllIcons.Status.Success
    }
    else {
      reviewOrResolveButton.isVisible = true
      rootPane.defaultButton = reviewOrResolveButton
      descriptionLabel.text = currentDescription
      descriptionLabel.icon = null
    }
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
    updateButtonsState()
    try {
      resolveAutomatically()
    }
    catch (e: ProcessCanceledException) {
      isResolveAutomaticallyPressed = false
      throw e
    }
    finally {
      isResolvingConflicts = false
      updateButtonsState()
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

private data class ButtonRectKey(val row: Int, val column: Int)

private const val ROW_HEIGHT = 28

private fun MergeConflictsTreeTable.installButtonRenderer(
  iterativeDataHolder: MergeConflictIterativeDataHolder,
  getSelectedFiles: () -> List<VirtualFile>,
  onButtonClick: (row: Int, column: Int) -> Unit,
) {
  // Default is 22, need to make it a tiny bit bigger so the button is nicely shown
  tree.rowHeight = JBUI.scale(ROW_HEIGHT)
  // If smaller than this, the buttons are not rendered properly
  minimumColumnWidth = JBUI.scale(188)

  val inlineButtonRects = mutableMapOf<ButtonRectKey, Rectangle>()
  val table = this
  TableHoverListener.DEFAULT.addTo(table)

  val inlineRenderer = InlineButtonRenderer(table,
                                            iterativeDataHolder,
                                            getSelectedFiles,
                                            inlineButtonRects,
                                            getHoveredRow = { TableHoverListener.getHoveredRow(table) })
  val colCount = table.columnModel.columnCount
  if (colCount > 1) table.columnModel.getColumn(1).cellRenderer = inlineRenderer
  if (colCount > 2) table.columnModel.getColumn(2).cellRenderer = inlineRenderer

  // Click handler: trigger only if the click is inside the cached button rect
  table.addMouseListener(object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      if (!table.isEnabled) return
      val row = table.rowAtPoint(e.point)
      val column = table.columnAtPoint(e.point)

      val cellRect = table.getCellRect(row, column, false)
      val localX = e.x - cellRect.x
      val localY = e.y - cellRect.y
      if (inlineButtonRects[ButtonRectKey(row = row, column = column)]?.contains(localX, localY) == true) {
        onButtonClick(row, column)
      }
    }
  })

  // Cache invalidation on resize (both per-column and whole table) and on data changes
  table.columnModel.addColumnModelListener(object : TableColumnModelListener {
    override fun columnMarginChanged(e: ChangeEvent?) {
      inlineButtonRects.clear()
    }

    override fun columnAdded(e: TableColumnModelEvent?) {
      inlineButtonRects.clear()
    }

    override fun columnRemoved(e: TableColumnModelEvent?) {
      inlineButtonRects.clear()
    }

    override fun columnMoved(e: TableColumnModelEvent?) {
      inlineButtonRects.clear()
    }

    override fun columnSelectionChanged(e: ListSelectionEvent?) { /* no-op */
    }
  })
  table.addComponentListener(object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      inlineButtonRects.clear()
    }
  })
  (table.model)?.addTableModelListener { inlineButtonRects.clear() }
}

private class InlineButtonRenderer(
  private val table: MergeConflictsTreeTable,
  private val iterativeDataHolder: MergeConflictIterativeDataHolder,
  private val getSelectedFiles: () -> List<VirtualFile>,
  private val inlineButtonRects: MutableMap<ButtonRectKey, Rectangle>,
  private val getHoveredRow: () -> Int,
) : TableCellRenderer {

  private lateinit var label: JLabel
  private lateinit var button: JButton
  private val panel = panel {
    row {
      label = label("").component
      button = button(CommonBundle.message("button.accept"), actionListener = {
        // Not working here because the cells are not editable by default,
        // and even when making them editable, it first requires a click to enter edit mode
        // and then another one to actually trigger the button action
      }).applyToComponent {
        putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
        putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.NONE)
        putClientProperty("ActionToolbar.smallVariant", true)
      }.component
    }
  }

  override fun getTableCellRendererComponent(
    jTable: JTable,
    value: Any?,
    isSelected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int,
  ): Component {
    label.text = value?.toString().orEmpty()

    val hoveredRow = getHoveredRow()
    val someRowHovered = hoveredRow != -1
    val isHovered = hoveredRow == row
    val file = MergeUIUtil.getFileAtRow(jTable, row)
    val selectedFiles = getSelectedFiles()
    val showBySelection = selectedFiles.size == 1 && file == selectedFiles.first()

    button.apply {
      isVisible =
        file?.isDirectory == false && !iterativeDataHolder.isFileResolved(file) && if (someRowHovered) isHovered else showBySelection
      isEnabled = table.isEnabled
    }

    panel.background = RenderingUtil.getBackground(table, isSelected)
    panel.foreground = RenderingUtil.getForeground(table, isSelected)

    panel.doLayout()
    if (button.isVisible) {
      inlineButtonRects[ButtonRectKey(row, column)] = Rectangle(button.bounds)
    }

    return panel
  }
}

private data class IterativeMergeDialogState(
  val selectedFiles: List<VirtualFile>,
  val unmergeableFileSelected: Boolean,
  val unacceptableFileSelected: Boolean,
  val resolvedFilesSelected: Boolean,
  val onlyRevertableFilesSelected: Boolean,
  val allSelectedFilesResolved: Boolean,
  val allFilesResolvedAndReviewed: Boolean,
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