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
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.merge.MergeConflictIterativeDataHolder
import com.intellij.openapi.vcs.merge.MergeConflictsTreeTable
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeResolveActionContext
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vcs.merge.MergeUIUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColorUtil
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.FontUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
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
import javax.swing.JTree
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.plaf.LayerUI
import javax.swing.table.TableCellRenderer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

internal class IterativeMergeFlowDelegate(
  private val project: Project,
  private val iterativeDataHolder: MergeConflictIterativeDataHolder,
  private val table: MergeConflictsTreeTable,
  private val columnNames: List<String>,
  private val mergeDialogCustomizer: MergeDialogCustomizer,
  private val rootPane: JRootPane,
  private val files: List<VirtualFile>,
  private val onClose: () -> Unit,
  private val onAcceptAndFinish: () -> Unit,
  acceptForResolution: (MergeSession.Resolution) -> Unit,
  private val showMergeDialog: () -> Unit,
  private val toggleGroupByDirectory: (Boolean) -> Unit,
  private val resolveAutomatically: () -> Unit,
  private val getGroupByDirectory: () -> Boolean,
  private val updateTable: () -> Unit,
) : MergeFlowDelegate {

  private lateinit var descriptionLabel: JLabel
  private lateinit var resolveAutomaticallyButton: JButton
  private lateinit var resolveStatusLabel: JLabel
  private lateinit var reviewOrResolveButton: JButton
  private lateinit var acceptAndFinishButton: JButton
  private var resolveActionControllers: List<MergeResolveActionComponentController> = emptyList()
  private var wasResolveAutomaticallyPressedOnce = false
  private var isResolveAutomaticallyPressed = false
  private var isResolvingConflicts = false

  @Nls
  private var currentDescription: String = VcsBundle.message("merge.loading.merge.details")

  private val acceptForResolution: (MergeSession.Resolution) -> Unit = { resolution ->
    acceptForResolution(resolution)
    clearAutoResolveStatus()
  }

  override fun createCenterPanel(): JComponent {
    table.changeHeaderColor()
    table.installDoubleClickListener { _ ->
      clearAutoResolveStatus()
      return@installDoubleClickListener false
    }
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
    table.createCellRenderer(project, { !getGroupByDirectory() }) {
      iterativeDataHolder.getMergeConflictModel(it)?.let { model ->
        val resolvedChanged = model.getResolvedChanges()
        val isModified = resolvedChanged.isNotEmpty()
        val text = VcsBundle.message("multiple.file.iterative.merge.files.resolved.changes.count",
                                     model.getResolvedChanges().size,
                                     model.getAllChanges().size)
        ColoredString(value = text,
                      background = if (isModified) BADGE_MODIFIED_BACKGROUND else BADGE_BACKGROUND,
                      foreground = if (isModified) BADGE_MODIFIED_FOREGROUND else BADGE_FOREGROUND)
      }
    }
    val defaultSpacingConfiguration = IntelliJSpacingConfiguration()
    val mergeContext = MergeResolveActionContext(
      project = project,
      selectionHintFilesProvider = { if (::state.isInitialized) state.selectedFiles else emptyList() },
      closeSourceUiHandler = onAcceptAndFinish,
    )
    resolveActionControllers = createMergeResolveActionComponentControllers(mergeContext, ITERATIVE_MERGE_DIALOG_ACTION_PLACE)
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
      }.customize(UnscaledGapsY(top = 8, bottom = 4))
      row {
        resolveAutomaticallyButton =
          button(VcsBundle.message(if (isResolvingConflicts) "multiple.file.merge.dialog.progress.title.resolving.conflicts"
                                   else "multiple.file.iterative.merge.resolve.automatically"),
                 actionListener = { onResolveAutomaticallyClick() }).applyToComponent {
            icon = AllIcons.Diff.MagicResolve
          }.align(AlignX.LEFT).component

        for (controller in resolveActionControllers) {
          cell(controller.component)
            .customize(UnscaledGaps(left = defaultSpacingConfiguration.segmentedButtonHorizontalGap))
        }

        resolveStatusLabel = label("")
          .resizableColumn()
          .component
        cell(createViewOptionsToolbar().component)
          .align(AlignX.RIGHT)
      }

      row {
        scrollCell(JLayer(table, DisabledStateLayerUI(table)))
          .align(Align.FILL)
      }.resizableRow()
    }.apply {
      // If the width is smaller than this, then buttons don't render properly
      minimumSize = JBUI.size(550, 240)

      preferredSize = JBUI.size(850, if (files.size <= 6) 400 else 500)
    }
  }

  override fun createSouthPanel(): JComponent = panel {
    val defaultSpacingConfiguration = IntelliJSpacingConfiguration()
    row {
      cell(JPanel()).resizableColumn()
      val closeAction = object : AbstractAction(CommonBundle.getCloseButtonText()) {
        override fun actionPerformed(e: ActionEvent) {
          val hasPartiallyResolvedFiles = files.any {
            val model = iterativeDataHolder.getMergeConflictModel(it) ?: return@any false
            model.getResolvedChanges().isNotEmpty() && model.getUnresolvedChanges().isNotEmpty()
          }
          if (!hasPartiallyResolvedFiles || MessageDialogBuilder.yesNo(VcsBundle.message("multiple.file.iterative.merge.close.confirmation.title"),
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
          onAcceptAndFinish()
        }
      }
      acceptAndFinishButton = DialogWrapper.createJButtonForAction(acceptAndFinishAction, rootPane)
      cell(acceptAndFinishButton)
        .customize(UnscaledGaps(left = defaultSpacingConfiguration.segmentedButtonHorizontalGap))

      val reviewOrResolveAction = object : AbstractAction(VcsBundle.message("multiple.file.iterative.merge.resolve.manually")) {
        override fun actionPerformed(e: ActionEvent) {
          clearAutoResolveStatus()
          showMergeDialog()
        }
      }
      reviewOrResolveButton = DialogWrapper.createJButtonForAction(reviewOrResolveAction, rootPane)
      cell(reviewOrResolveButton)
        .customize(UnscaledGaps(left = defaultSpacingConfiguration.segmentedButtonHorizontalGap))
    }.customize(UnscaledGapsY(top = 32))
  }

  private fun createViewOptionsToolbar(): ActionToolbar {
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
  override fun onTreeChanged(
    selectedFiles: List<VirtualFile>,
    processedFiles: List<VirtualFile>,
    unmergeableFileSelected: Boolean,
    unacceptableFileSelected: Boolean,
  ) {
    val iterativeFiles = files - processedFiles.toSet()
    state = IterativeMergeDialogState(
      selectedFiles = selectedFiles,
      filesThatShouldBeResolvedIteratively = iterativeFiles,
      unmergeableFileSelected = unmergeableFileSelected,
      unacceptableFileSelected = unacceptableFileSelected,
      resolvedFilesSelected = selectedFiles.any { iterativeDataHolder.isFileResolved(it) },
      allSelectedFilesResolved = selectedFiles.all { iterativeDataHolder.isFileResolved(it) },
      onlyRevertableFilesSelected = selectedFiles.isNotEmpty() && selectedFiles.all {
        iterativeDataHolder.getMergeConflictModel(it)?.getResolvedChanges()?.isNotEmpty() == true
      },
      allFilesResolvedAndReviewed = iterativeFiles.all {
        val model = iterativeDataHolder.getMergeConflictModel(it)
        model?.wasReviewed == true && model.getUnresolvedChanges().isEmpty()
      })
    updateButtonsState()
    resolveActionControllers.forEach { it.update() }
  }

  private val animatedIcon = AnimatedIcon.Default()
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
      icon = if (isResolvingConflicts) animatedIcon else AllIcons.Diff.MagicResolve
      disabledIcon = if (isResolvingConflicts) animatedIcon else IconLoader.getDisabledIcon(AllIcons.Diff.MagicResolve)
      text = if (isResolvingConflicts) VcsBundle.message("multiple.file.merge.dialog.progress.title.resolving.conflicts")
      else VcsBundle.message("multiple.file.iterative.merge.resolve.automatically")

      toolTipText = when {
        isResolvingConflicts -> null
        !isEnabled -> VcsBundle.message("multiple.file.iterative.merge.resolve.automatically.disabled.tooltip")
        else -> VcsBundle.message("multiple.file.iterative.merge.resolve.automatically.tooltip")
      }
    }

    table.isEnabled = !isResolvingConflicts

    reviewOrResolveButton.apply {
      isEnabled = table.isEnabled
      text = if (state.allSelectedFilesResolved) VcsBundle.message("multiple.file.iterative.merge.review.changes")
      else VcsBundle.message("multiple.file.iterative.merge.resolve.manually")
    }

    acceptAndFinishButton.isEnabled =
      table.isEnabled && state.filesThatShouldBeResolvedIteratively.all { iterativeDataHolder.isFileResolved(it) }

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
    val resolvedFiles = iterativeDataHolder.getResolvedFilesAndModels().keys
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
            clearAutoResolveStatus()
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

  private fun clearAutoResolveStatus() {
    resolveStatusLabel.text = ""
    resolveStatusLabel.isVisible = false
  }

  private fun onResolveAutomaticallyClick() {
    wasResolveAutomaticallyPressedOnce = true
    isResolveAutomaticallyPressed = true
    isResolvingConflicts = true
    clearAutoResolveStatus()
    updateButtonsState()

    val resolvedBefore = files.sumOf { iterativeDataHolder.getMergeConflictModel(it)?.getResolvedChanges()?.size ?: 0 }
    try {
      resolveAutomatically()
    }
    catch (e: ProcessCanceledException) {
      isResolveAutomaticallyPressed = false
      throw e
    }
    finally {
      isResolvingConflicts = false
      val resolvedAfter = files.sumOf { iterativeDataHolder.getMergeConflictModel(it)?.getResolvedChanges()?.size ?: 0 }
      val resolvedByAutoResolve = resolvedAfter - resolvedBefore
      val totalUnresolved = files.sumOf { iterativeDataHolder.getMergeConflictModel(it)?.getUnresolvedChanges()?.size ?: 0 }
      val filesWithUnresolved = files.count { iterativeDataHolder.getMergeConflictModel(it)?.getUnresolvedChanges()?.isNotEmpty() == true }
      resolveStatusLabel.text = when {
        resolvedByAutoResolve == 0 -> VcsBundle.message("multiple.file.iterative.merge.status.none.resolved")
        totalUnresolved == 0 -> VcsBundle.message("multiple.file.iterative.merge.status.all.resolved")
        else -> VcsBundle.message("multiple.file.iterative.merge.status.partially.resolved",
                                  resolvedByAutoResolve,
                                  totalUnresolved,
                                  filesWithUnresolved)
      }
      resolveStatusLabel.isVisible = true
      updateButtonsState()
    }
  }
}

private enum class ConflictsNodeType {
  UNRESOLVED,
  RESOLVED
}

private class ConflictsGroupNode(val type: ConflictsNodeType) : ChangesBrowserNode<ConflictsNodeType>(type) {
  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.icon = when (type) {
      ConflictsNodeType.RESOLVED -> AllIcons.General.GreenCheckmark
      ConflictsNodeType.UNRESOLVED -> AllIcons.Vcs.Remove
    }
    renderer.append(FontUtil.spaceAndThinSpace() + getTextPresentation(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    appendCount(renderer)
  }

  override fun getTextPresentation(): String = when (type) {
    ConflictsNodeType.UNRESOLVED -> VcsBundle.message("changes.nodetitle.merge.dialog.unresolved")
    ConflictsNodeType.RESOLVED -> VcsBundle.message("changes.nodetitle.merge.dialog.resolved")
  }

  override fun getCountText(): @Nls String {
    return FontUtil.spaceAndThinSpace() + VcsBundle.message("iterative.merge.files.count", fileCount)
  }

  override fun isLeaf(): Boolean = false
}

private fun TreeTable.changeHeaderColor() {
  val defaultRenderer = tableHeader.defaultRenderer
  tableHeader.defaultRenderer = TableCellRenderer { table, value, isSelected, hasFocus, row, column ->
    defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).apply {
      foreground = UIUtil.getLabelInfoForeground()
    }
  }
}

private fun TreeTable.installDoubleClickListener(onDoubleClick: (MouseEvent) -> Boolean) {
  object : DoubleClickListener() {
    override fun onDoubleClick(event: MouseEvent) = onDoubleClick(event)
  }.installOn(this)
}

private val BADGE_FOREGROUND = JBColor.namedColor("VersionControl.Merge.Badge.infoForeground", 0x5A5D6B, 0xB4B8BF)
private val BADGE_BACKGROUND = JBColor.namedColor("VersionControl.Merge.Badge.infoBackground",
                                                  JBColor(ColorUtil.withAlpha(Color(0x5A5D6B), 0.16),
                                                          ColorUtil.withAlpha(Color(0xB4B8BF), 0.20)))
private val BADGE_MODIFIED_FOREGROUND = JBColor.namedColor("VersionControl.Merge.Badge.modifiedItemForeground", 0x2E55A3, 0xD1E0FF)
private val BADGE_MODIFIED_BACKGROUND = JBColor.namedColor("VersionControl.Merge.Badge.modifiedItemBackground",
                                                           JBColor(ColorUtil.withAlpha(Color(0x3574F0), 0.16),
                                                                   ColorUtil.withAlpha(Color(0x35538F), 0.8)))

private fun TreeTable.createCellRenderer(
  project: Project?,
  showFlattenGetter: () -> Boolean,
  nameDecorator: (VirtualFile) -> ColoredString?,
) {
  tree.cellRenderer = object : ChangesBrowserNodeRenderer(project, showFlattenGetter, false) {

    override fun appendFileName(vFile: VirtualFile?, fileName: @NlsSafe String, color: Color?) {
      super.appendFileName(vFile, fileName, null)
    }

    override fun doPaintFragmentBackground(g: Graphics2D, index: Int, bgColor: Color, x: Int, y: Int, width: Int, height: Int) {
      val yOffset = JBUI.scale(6)
      val badgeHeight = height - 2 * yOffset
      g.color = bgColor
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.fillRoundRect(x, y + yOffset, width, badgeHeight, badgeHeight, badgeHeight)
    }

    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      val virtualFile = (value as? DefaultMutableTreeNode)?.userObject as? VirtualFile
      val extraText = virtualFile?.let(nameDecorator)
      if (extraText != null) {
        setFileNameSuffixAppender {
          val badgeAttrs = SimpleTextAttributes(extraText.background,
                                                extraText.foreground,
                                                null,
                                                SimpleTextAttributes.STYLE_OPAQUE)
          // Pad to get the capsule look without having to worry about horizontal alignment
          append("  ${extraText.value}  ", badgeAttrs)
        }
      }

      if (value is ChangesBrowserNode<*>) {
        value.render(tree, this, selected, expanded, hasFocus)
      }
    }
  }
}

private data class ColoredString(val value: @Nls String, val background: JBColor, val foreground: JBColor)
private data class ButtonRectKey(val row: Int, val column: Int)

private const val ROW_HEIGHT = 28
private const val MIN_COLUMN_WIDTH = 160

private fun MergeConflictsTreeTable.installButtonRenderer(
  iterativeDataHolder: MergeConflictIterativeDataHolder,
  getSelectedFiles: () -> List<VirtualFile>,
  onButtonClick: (row: Int, column: Int) -> Unit,
) {
  // Default is 22, need to make it a tiny bit bigger so the button is nicely shown
  tree.rowHeight = JBUI.scale(ROW_HEIGHT)

  val inlineButtonRects = mutableMapOf<ButtonRectKey, Rectangle>()
  val table = this
  TableHoverListener.DEFAULT.addTo(table)

  val inlineRenderer = InlineButtonRenderer(table,
                                            iterativeDataHolder,
                                            getSelectedFiles,
                                            inlineButtonRects,
                                            getHoveredRow = { TableHoverListener.getHoveredRow(table) })
  val colCount = table.columnModel.columnCount
  val minColumnWidth = JBUI.scale(MIN_COLUMN_WIDTH)
  if (colCount > 1) table.columnModel.getColumn(1).apply {
    cellRenderer = inlineRenderer
    minWidth = minColumnWidth
  }
  if (colCount > 2) table.columnModel.getColumn(2).apply {
    cellRenderer = inlineRenderer
    minWidth = minColumnWidth
  }

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
  }.apply {
    border = JBUI.Borders.emptyLeft(6)
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
  // The `files` param contains ALL the files in the merge conflict,
  // which may include files that are binary or using external tools for resolving.
  // Those files will not trigger an iterative merge and will not have a model.
  val filesThatShouldBeResolvedIteratively: List<VirtualFile>,
  val unmergeableFileSelected: Boolean,
  val unacceptableFileSelected: Boolean,
  val resolvedFilesSelected: Boolean,
  val onlyRevertableFilesSelected: Boolean,
  val allSelectedFilesResolved: Boolean,
  val allFilesResolvedAndReviewed: Boolean,
)

private class DisabledStateLayerUI(private val table: MergeConflictsTreeTable) : LayerUI<JComponent>() {
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