// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.cleanup

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.GitDisposable
import git4idea.GitLocalBranch
import git4idea.branch.DeepComparator
import git4idea.branch.GitBrancher
import git4idea.branch.GitNewBranchDialog
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.TableModelEvent
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableRowSorter

internal class CleanupBranchesDialog(private val project: Project) : DialogWrapper(project, true) {

  private val repositories: List<GitRepository> = GitRepositoryManager.getInstance(project).repositories
  private val cs: CoroutineScope = GitDisposable.getInstance(project).childScope("CleanupBranchesDialog")

  private val localBranchNames: List<String> = repositories.asSequence().flatMap { it.branches.localBranches.asSequence() }.map(GitLocalBranch::name).distinct().sorted().toList()

  private val targetBranchField = TextFieldWithCompletion(project, GitNewBranchDialog.BranchNamesCompletion(localBranchNames, localBranchNames), project.getDefaultTargetBranchSuggestion().orEmpty(), true, true, false, false)

  private val branchPrefixes = GitNewBranchDialog.collectDirectories(localBranchNames, false).toList()
  private val prefixField = TextFieldWithCompletion(project, GitNewBranchDialog.BranchNamesCompletion(branchPrefixes, branchPrefixes), "", true, true, false, false).apply { minimumSize = JBUI.size(200, 0) }

  private val tableModel = ListTableModel<BranchRow>(arrayOf(SelectedColumn(), NameColumn(), LastCommitDateColumn(), TrackedBranchColumn(), MergedStatusColumn()), mutableListOf(), 1 // default sort by name
  )

  private val table = JBTable(tableModel).apply {
    setShowGrid(false)
    setAutoCreateRowSorter(false)
    tableHeader.reorderingAllowed = true
    rowSorter = TableRowSorter(model).apply { sortsOnUpdates = true } // Ensure Boolean columns ("Selected") are rendered/edited as checkboxes
    setDefaultRenderer(Boolean::class.java, BooleanTableCellRenderer())
    setDefaultEditor(Boolean::class.java, BooleanTableCellEditor()) // Default renderer for Long timestamps (fallback)
    setDefaultRenderer(Long::class.java, object : DefaultTableCellRenderer() {
      override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
      ): java.awt.Component {
        val ts = (value as? Long) ?: Long.MIN_VALUE
        val text = if (ts != Long.MIN_VALUE) DateFormatUtil.formatDateTime(ts) else ""
        return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
      }
    }) // Do not sort by the checkbox column to avoid conflicts with header toggle behavior
    (rowSorter as? TableRowSorter<*>)?.setSortable(0, false) // For the Last Commit Date column (model index 2), sort by timestamp and keep unknowns (Long.MIN_VALUE) last in ascending order
    (rowSorter as? TableRowSorter<*>)?.setSortable(2, true) // Help define initial dialog size via viewport preferred size
    preferredScrollableViewportSize = JBUI.size(900, 300)
  }

  /** Ensure the Last Commit Date column renders human-readable strings even if default renderer is overridden elsewhere. */
  private fun installLastCommitDateColumnRenderer() { // The date column is model index 2
    val modelIndex = 2
    val viewIndex = table.convertColumnIndexToView(modelIndex)
    if (viewIndex < 0) return
    val column = table.columnModel.getColumn(viewIndex)
    column.cellRenderer = object : DefaultTableCellRenderer() {
      override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
      ): java.awt.Component {
        val ts = (value as? Long) ?: Long.MIN_VALUE
        val text = if (ts != Long.MIN_VALUE) DateFormatUtil.formatDateTime(ts) else ""
        return super.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column)
      }
    }
  }

  // Header checkbox for the "Selected" column
  private val headerCheckBox = ThreeStateCheckBox().apply { // Start with NOT_SELECTED by default
    state = ThreeStateCheckBox.State.NOT_SELECTED // Paint background via header renderer container
    isOpaque = false
  }

  // Reference to the Delete action button to enable/disable based on selection
  private lateinit var deleteActionRef: Action

  init {
    title = GitBundle.message("git.cleanup.branches.dialog.title")
    setOKButtonText(GitBundle.message("git.cleanup.branches.close")) // Make the dialog modeless
    isModal = false
    init() // Listen for table model changes and update Delete button enabled state accordingly
    tableModel.addTableModelListener { _ -> updateDeleteButtonEnabled() }
    installHeaderCheckbox()
    installLastCommitDateColumnRenderer()
    refreshBranches()
  }

  /**
   * Copy provider to copy selected table rows (Branch Name, Last Commit Date, Tracked Branch) to the clipboard.
   */
  private val tableCopyProvider = object : CopyProvider {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun performCopy(dataContext: DataContext) {
      val selected = table.selectedRows
      if (selected.isEmpty()) return
      val sb = StringBuilder()
      for ((i, viewRow) in selected.withIndex()) {
        val modelRow = table.convertRowIndexToModel(viewRow)
        if (modelRow in 0 until tableModel.rowCount) {
          val row = tableModel.items[modelRow]
          val name = row.branch.name
          val ts = row.lastCommitDate
          val date = if (ts != Long.MIN_VALUE) DateFormatUtil.formatDateTime(ts) else ""
          val tracked = row.trackedBranch.orEmpty()
          if (i > 0) sb.append('\n')
          sb.append(name).append('\t').append(date).append('\t').append(tracked)
        }
      }
      CopyPasteManager.getInstance().setContents(StringSelection(sb.toString()))
    }

    override fun isCopyEnabled(dataContext: DataContext): Boolean = table.selectedRowCount > 0
    override fun isCopyVisible(dataContext: DataContext): Boolean = true
  }

  override fun createActions(): Array<Action> {
    val deleteAction = object : DialogWrapperAction(GitBundle.message("git.cleanup.branches.delete")) {
      override fun doAction(e: java.awt.event.ActionEvent?) {
        ActionUtil.performAction(DeleteBranchesAction(), ActionUtil.createEmptyEvent())
      }
    }
    // Keep a reference and disable initially (until at least one row is selected)
    deleteActionRef = deleteAction
    deleteAction.isEnabled = false
    val calcAction = object : DialogWrapperAction(GitBundle.message("git.cleanup.branches.calculate")) {
      override fun doAction(e: java.awt.event.ActionEvent?) {
        ActionUtil.performAction(CalculateMergeStatusAction(), ActionUtil.createEmptyEvent())
      }
    }
    return arrayOf(deleteAction, calcAction, okAction)
  }

  override fun createCenterPanel() = panel {
    row(GitBundle.message("find.merged.local.branches.target.label")) {
      cell(targetBranchField).align(AlignX.FILL).focused().applyToComponent { selectAll() }.comment(GitBundle.message("find.merged.local.branches.target.comment"))
    }
    row(GitBundle.message("git.cleanup.branches.filter.prefix.label")) {
      cell(prefixField).align(AlignX.FILL).comment(GitBundle.message("git.cleanup.branches.filter.prefix.comment"))
      val filter = JButton(GitBundle.message("git.cleanup.branches.filter"))
      filter.addActionListener { refreshBranches() }
      cell(filter)
    }

    row {
      val decorator = ToolbarDecorator.createDecorator(table).disableAddAction().disableRemoveAction()
      val tablePanel = decorator.createPanel()
      val wrapped = UiDataProvider.wrapComponent(tablePanel) { sink ->
        sink[PlatformDataKeys.COPY_PROVIDER] = tableCopyProvider
      }
      cell(wrapped).align(AlignX.FILL)
    }
  }.apply { // Drive the initial dialog size through panel's preferredSize instead of getInitialSize()
    preferredSize = JBUI.size(900, 400)
  }

  private fun refreshBranches() {
    val prefix = prefixField.text.trim()
    // Preserve previously calculated "Merged to target" state if the target hasn't changed
    val prevMergeStatuses: Map<Pair<GitRepository, String>, String> =
      tableModel.items.asSequence()
        .filter { it.mergedStatus.isNotBlank() }
        .associate { (it.repository to it.branch.name) to it.mergedStatus }

    val rows = mutableListOf<BranchRow>()
    for (repo in repositories) {
      val locals = repo.branches.localBranches
      for (branch in locals) {
        val name = branch.name
        if (prefix.isNotEmpty() && !name.startsWith(prefix)) continue
        val tracked = branch.findTrackedBranch(repo)?.name ?: ""
        val merged = prevMergeStatuses[repo to name] ?: ""
        rows += BranchRow(repository = repo, branch = branch, selected = false, lastCommitDate = Long.MIN_VALUE, trackedBranch = tracked, mergedStatus = merged)
      }
    }

    // ListTableModel.items returns an unmodifiable view; mutate via the model API instead
    tableModel.setItems(rows)
    updateHeaderCheckboxState()
    table.tableHeader.repaint() // Keep the checkbox column compact after data changes
    setSelectedColumnMinimalWidth() // Update Delete button enabled state according to current selection
    updateDeleteButtonEnabled()

    // Populate "Last commit date" asynchronously using Vcs Log commit metadata cache
    populateLastCommitDates(rows)
  }

  /**
   * Populate lastCommitDate for given rows using VcsLogData.commitMetadataCache.
   * Runs when VCS Log is ready and updates the table on EDT.
   */
  private fun populateLastCommitDates(rows: List<BranchRow>) {
    if (rows.isEmpty()) return
    VcsProjectLog.runWhenLogIsReady(project) { logManager ->
      val dataProvider = logManager.dataManager
      cs.launch(Dispatchers.Default) { // First try cache; if missing, synchronously load via MiniDetailsGetter (AbstractDataGetter)
        val tsByRow = HashMap<BranchRow, Long>(rows.size)
        val indexByRow = HashMap<BranchRow, Int>(rows.size)
        val missing = ArrayList<Int>()

        for (row in rows) {
          val hash = row.repository.branches.getHash(row.branch) ?: continue
          val index = dataProvider.storage.getCommitIndex(hash, row.repository.root)
          indexByRow[row] = index
          val cached = dataProvider.commitMetadataCache.getCachedData(index)
          if (cached != null) {
            tsByRow[row] = cached.timestamp
          }
          else {
            missing.add(index)
          }
        }

        if (missing.isNotEmpty()) {
          try { // Use AbstractDataGetter#loadCommitsDataSynchronously via MiniDetailsGetter
            val collected = HashMap<Int, Long>(missing.size)
            dataProvider.miniDetailsGetter.loadCommitsDataSynchronously(missing, EmptyProgressIndicator()) { commitIndex, details ->
              collected[commitIndex] = details.timestamp
            } // Map loaded details back to rows
            for ((row, idx) in indexByRow) {
              val ts = collected[idx]
              if (ts != null) {
                tsByRow[row] = ts
              }
            }
          }
          catch (_: VcsException) { // Ignore: leave missing dates empty on failure
          }
        }

        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) { // Dialog may already be closed; in that case, do nothing
          if (!contentPane.isShowing) return@withContext

          // Apply results to the current rows that are still present in the model and refresh the table
          val currentItems = tableModel.items
          for (row in rows) {
            if (currentItems.contains(row)) {
              row.lastCommitDate = tsByRow[row] ?: Long.MIN_VALUE
            }
          }
          if (tableModel.rowCount > 0) {
            tableModel.fireTableRowsUpdated(0, tableModel.rowCount - 1)
          }
        }
      }
    }
  }

  inner class DeleteBranchesAction : AnAction() { //support FUS
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun isDumbAware(): Boolean = true
    override fun actionPerformed(e: AnActionEvent) {
      deleteSelectedBranches()
    }
  }

  private fun deleteSelectedBranches() {
    val toDelete = tableModel.items.filter { it.selected }
    if (toDelete.isEmpty()) return

    val branchNames = toDelete.joinToString(separator = "\n") { it.branch.name }
    val result = Messages.showYesNoDialog(project, GitBundle.message("git.cleanup.branches.delete.confirm.text", toDelete.size, branchNames), GitBundle.message("git.cleanup.branches.delete.confirm.title"), GitBundle.message("git.cleanup.branches.delete.confirm.yes"), GitBundle.message("git.cleanup.branches.delete.confirm.no"), null)
    if (result != Messages.YES) return

    // Build map: branch name -> repositories where it should be deleted
    val branchesToRepos: Map<String, List<GitRepository>> = toDelete.groupBy { it.branch.name }.mapValues { (_, rows) -> rows.map { it.repository } }

    // Use bulk deletion with AWt callback to refresh the table when done
    val brancher = GitBrancher.getInstance(project)
    brancher.deleteBranches(branchesToRepos, ::refreshBranches)
  }

  private fun updateDeleteButtonEnabled() {
    if (this::deleteActionRef.isInitialized) {
      val anySelected = tableModel.items.any { it.selected }
      deleteActionRef.isEnabled = anySelected
    }
  }

  inner class CalculateMergeStatusAction : AnAction() { //support FUS
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun isDumbAware(): Boolean = true
    override fun actionPerformed(e: AnActionEvent) {
      calculateMergeStatus()
    }
  }

  private fun calculateMergeStatus() {
    val targetBranch = targetBranchField.text.trim().takeIf { it.isNotEmpty() } ?: return
    val reposWithTarget = repositories.filter { it.branches.findLocalBranch(targetBranch) != null }.associateWith { targetBranch }

    runWithModalProgressBlocking(ModalTaskOwner.component(contentPane), GitBundle.message("git.cleanup.branches.calculate.progress"), TaskCancellation.cancellable()) {
      coroutineToIndicator { indicator ->
        val dataProvider = VcsProjectLog.getInstance(project).dataManager ?: return@coroutineToIndicator
        val threadsCount = Runtime.getRuntime().availableProcessors().coerceAtMost(5)
        val pool = AppExecutorUtil.createBoundedApplicationPoolExecutor("Cleanup Branches Status", threadsCount)
        try {
          val rows = tableModel.items.filter { it.mergedStatus.isBlank() }.toList()
          val total = rows.size
          var processed = 0
          val tasks = rows.map { row ->
            pool.submit<Boolean> {
              indicator.checkCanceled()
              val comparator = DeepComparator(project, dataProvider, dataProvider.dataPack, reposWithTarget, row.branch.name)
              val result = comparator.compare()
              indicator.checkCanceled()
              val merged = result.exception == null && result.nonPickedCommits.isEmpty()
              row.mergedStatus = if (merged) GitBundle.message("git.cleanup.branches.status.merged") else GitBundle.message("git.cleanup.branches.status.not.merged")
              processed++
              indicator.text = GitBundle.message("find.merged.local.branches.progress.processed", processed, total)
              indicator.fraction = processed.toDouble() / total
              merged
            }
          } // wait for completion off-EDT
          tasks.forEach { it.get() }
        }
        finally {
          pool.shutdown()
          cs.launch {
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              if (!contentPane.isShowing) return@withContext
              if (tableModel.rowCount > 0) {
                tableModel.fireTableRowsUpdated(0, tableModel.rowCount - 1)
              }
            }
          }
        }
      }
    }
  }

  /** Install ThreeStateCheckBox into the Selected column header and wire interactions. */
  private fun installHeaderCheckbox() { // Reflect current selection in header whenever model changes
    tableModel.addTableModelListener { e ->
      if (e == null) return@addTableModelListener
      if (e.type == TableModelEvent.UPDATE && e.column != 0 && e.column != TableModelEvent.ALL_COLUMNS) return@addTableModelListener
      updateHeaderCheckboxState()
      table.tableHeader.repaint()
    }

    // Provide custom header renderer for the Selected column
    val header = table.tableHeader
    fun applyHeaderRenderer() {
      val viewIndex = getSelectedColumnViewIndex()
      if (viewIndex >= 0) {
        val column = header.columnModel.getColumn(viewIndex)
        column.headerRenderer = TableCellRenderer { _, _, _, _, _, _ -> // Center the three-state checkbox inside the header cell
          JPanel(GridBagLayout()).apply {
            isOpaque = true
            background = header.background
            border = UIManager.getBorder("TableHeader.cellBorder")
            add(headerCheckBox, GridBagConstraints().apply { anchor = GridBagConstraints.CENTER })
          }
        } // Also re-apply compact width whenever the renderer is (re)installed
        setSelectedColumnMinimalWidth()
      }
    }

    applyHeaderRenderer()

    // Toggle all rows on header click on the Selected column
    header.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        val viewIndex = getSelectedColumnViewIndex()
        if (viewIndex < 0) return
        val colAtX = header.columnModel.getColumnIndexAtX(e.x)
        if (colAtX != viewIndex) return

        val selectAll = headerCheckBox.state != ThreeStateCheckBox.State.SELECTED
        setAllRowsSelected(selectAll)
        updateHeaderCheckboxState()
        header.repaint(header.getHeaderRect(viewIndex))
      }
    })

    // Re-apply renderer when columns are moved
    header.columnModel.addColumnModelListener(object : javax.swing.event.TableColumnModelListener {
      override fun columnAdded(e: javax.swing.event.TableColumnModelEvent?) {
        applyHeaderRenderer()
      }

      override fun columnRemoved(e: javax.swing.event.TableColumnModelEvent?) {
        applyHeaderRenderer()
      }

      override fun columnMoved(e: javax.swing.event.TableColumnModelEvent?) {
        applyHeaderRenderer()
      }

      override fun columnMarginChanged(e: javax.swing.event.ChangeEvent?) {}
      override fun columnSelectionChanged(e: javax.swing.event.ListSelectionEvent?) {}
    })
  }

  private fun getSelectedColumnViewIndex(): Int = table.convertColumnIndexToView(0)

  /**
   * Make the "Selected" checkbox column as narrow as possible while still fitting the checkbox
   * both in cells and in the header.
   */
  private fun setSelectedColumnMinimalWidth() {
    val viewIndex = getSelectedColumnViewIndex()
    if (viewIndex < 0) return

    val column = table.columnModel.getColumn(viewIndex)

    // Measure preferred width of checkbox both for cell and header
    val cellWidth = if (tableModel.rowCount > 0) {
      val cellRenderer = table.getDefaultRenderer(Boolean::class.java)
      val comp = cellRenderer.getTableCellRendererComponent(table, false, false, false, 0, viewIndex)
      comp.preferredSize.width
    }
    else { // Avoid calling table/renderer when there are no rows: BooleanTableCellRenderer may query
      // JTable.isCellEditable(row, col) that tries to convert row index and fails for empty model.
      JCheckBox().preferredSize.width
    }
    val headerWidth = headerCheckBox.preferredSize.width
    val padding = JBUI.scale(12)
    val width = maxOf(cellWidth, headerWidth) + padding

    // Keep it compact; allow a tiny wiggle room to avoid layout jitter
    column.minWidth = width
    column.preferredWidth = width
    column.maxWidth = width
  }

  private fun setAllRowsSelected(value: Boolean) {
    if (tableModel.rowCount == 0) return
    tableModel.items.forEach { it.selected = value }
    tableModel.fireTableRowsUpdated(0, tableModel.rowCount - 1)
  }

  private fun updateHeaderCheckboxState() {
    val total = tableModel.rowCount
    if (total == 0) {
      headerCheckBox.state = ThreeStateCheckBox.State.NOT_SELECTED
      return
    }
    val selectedCount = tableModel.items.count { it.selected }
    headerCheckBox.state = when (selectedCount) {
      0 -> ThreeStateCheckBox.State.NOT_SELECTED
      total -> ThreeStateCheckBox.State.SELECTED
      else -> ThreeStateCheckBox.State.DONT_CARE
    }
  }

  private fun Project.getDefaultTargetBranchSuggestion(): String? {
    val repo = GitRepositoryManager.getInstance(this).repositories.firstOrNull()
    return repo?.branches?.localBranches?.find { it.name == "master" || it.name == "main" }?.name ?: repo?.currentBranch?.name
  }

  private data class BranchRow(
    val repository: GitRepository,
    val branch: GitLocalBranch,
    var selected: Boolean,
    var lastCommitDate: Long,
    val trackedBranch: @NlsSafe String?,
    var mergedStatus: @NlsSafe String,
  )

  private class SelectedColumn : ColumnInfo<BranchRow, Boolean>(GitBundle.message("git.cleanup.branches.col.selected")) {
    override fun valueOf(item: BranchRow): Boolean = item.selected
    override fun getColumnClass(): Class<*> = Boolean::class.java
    override fun isCellEditable(item: BranchRow?): Boolean = true
    override fun setValue(item: BranchRow, value: Boolean) {
      item.selected = value
    }

    override fun getComparator(): java.util.Comparator<BranchRow> = compareBy { it.selected }
  }

  private class NameColumn : ColumnInfo<BranchRow, String>(GitBundle.message("git.cleanup.branches.col.name")) {
    override fun valueOf(item: BranchRow): String = item.branch.name
  }

  private class LastCommitDateColumn : ColumnInfo<BranchRow, Long>(GitBundle.message("git.cleanup.branches.col.last.commit")) {
    override fun valueOf(item: BranchRow): Long = item.lastCommitDate
    override fun getColumnClass(): Class<*> = Long::class.java
  }

  private class TrackedBranchColumn : ColumnInfo<BranchRow, String>(GitBundle.message("git.cleanup.branches.col.tracked")) {
    override fun valueOf(item: BranchRow): String = item.trackedBranch.orEmpty()
  }

  private class MergedStatusColumn : ColumnInfo<BranchRow, String>(GitBundle.message("git.cleanup.branches.col.merged.status")) {
    override fun valueOf(item: BranchRow): String = item.mergedStatus
  }
}
