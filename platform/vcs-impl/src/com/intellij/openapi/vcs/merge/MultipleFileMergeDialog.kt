// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge

import com.intellij.CommonBundle
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.InvalidDiffRequestException
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.MergeUtil
import com.intellij.diff.util.DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ColumnName
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.NonNls
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

open class MultipleFileMergeDialog(
  private val project: Project?,
  files: List<VirtualFile>,
  private val mergeProvider: MergeProvider,
  private val mergeDialogCustomizer: MergeDialogCustomizer
) : DialogWrapper(project) {
  private var unresolvedFiles = files.toMutableList()
  private val mergeSession = (mergeProvider as? MergeProvider2)?.createMergeSession(files)
  val processedFiles: MutableList<VirtualFile> = mutableListOf()
  private val table: TreeTable
  private lateinit var acceptYoursButton: JButton
  private lateinit var acceptTheirsButton: JButton
  private lateinit var mergeButton: JButton
  private val tableModel = ListTreeTableModelOnColumns(DefaultMutableTreeNode(), createColumns())

  private lateinit var descriptionLabel: JLabel

  private var groupByDirectory: Boolean = false
    get() = when {
      project != null -> VcsConfiguration.getInstance(project).GROUP_MULTIFILE_MERGE_BY_DIRECTORY
      else -> field
    }
    set(value) = when {
      project != null -> VcsConfiguration.getInstance(project).GROUP_MULTIFILE_MERGE_BY_DIRECTORY = value
      else -> field = value
    }

  private val virtualFileRenderer = object : ChangesBrowserNodeRenderer(project, { !groupByDirectory }, false) {
    override fun calcFocusedState() = UIUtil.isAncestor(this@MultipleFileMergeDialog.peer.window,
                                                        IdeFocusManager.getInstance(project).focusOwner)
  }

  init {
    project?.let { StoreReloadManager.getInstance(project).blockReloadingProjectOnExternalChanges() }
    title = mergeDialogCustomizer.getMultipleFileDialogTitle()
    virtualFileRenderer.font = UIUtil.getListFont()

    table = MergeConflictsTreeTable(tableModel)
    table.setTreeCellRenderer(virtualFileRenderer)
    table.rowHeight = virtualFileRenderer.preferredSize.height
    table.preferredScrollableViewportSize = JBUI.size(600, 300)

    @Suppress("LeakingThis")
    init()

    updateTree()
    table.tree.selectionModel.addTreeSelectionListener { updateButtonState() }
    updateButtonState()
    selectFirstFile()
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        if (EditSourceOnDoubleClickHandler.isToggleEvent(table.tree, event)) return false
        showMergeDialog()
        return true
      }
    }.installOn(table.tree)

    TableSpeedSearch.installOn(table, Convertor { (it as? VirtualFile)?.name })

    val modalityState = ModalityState.stateForComponent(descriptionLabel)
    BackgroundTaskUtil.executeOnPooledThread(disposable, Runnable {
      val description = mergeDialogCustomizer.getMultipleFileMergeDescription(unresolvedFiles)
      runInEdt(modalityState) {
        descriptionLabel.text = description
      }
    })
  }

  private fun selectFirstFile() {
    if (!groupByDirectory) {
      table.selectionModel.setSelectionInterval(0, 0)
    }
    else {
      TreeUtil.promiseSelectFirstLeaf(table.tree)
    }
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        descriptionLabel = label(VcsBundle.message("merge.loading.merge.details")).component
      }

      row {
        scrollCell(table)
          .align(Align.FILL)
          .resizableColumn()

        panel {
          row {
            acceptYoursButton = button(VcsBundle.message("multiple.file.merge.accept.yours")) {
              acceptRevision(MergeSession.Resolution.AcceptedYours)
            }.align(AlignX.FILL)
              .component
          }
          row {
            acceptTheirsButton = button(VcsBundle.message("multiple.file.merge.accept.theirs")) {
              acceptRevision(MergeSession.Resolution.AcceptedTheirs)
            }.align(AlignX.FILL)
              .component
          }
          row {
            val mergeAction = object : AbstractAction(VcsBundle.message("multiple.file.merge.merge")) {
              override fun actionPerformed(e: ActionEvent) {
                showMergeDialog()
              }
            }
            mergeAction.putValue(DEFAULT_ACTION, true)
            mergeButton = createJButtonForAction(mergeAction)
            cell(mergeButton)
              .align(AlignX.FILL)
          }
        }.align(AlignY.TOP)
      }.resizableRow()

      if (project != null) {
        row {
          checkBox(VcsBundle.message("multiple.file.merge.group.by.directory.checkbox"))
            .selected(groupByDirectory)
            .applyToComponent {
              addChangeListener { toggleGroupByDirectory(isSelected) }
            }
        }
      }
    }.also {
      // Temporary workaround for IDEA-302779
      it.minimumSize = JBUI.size(200, 150)
    }
  }

  private fun createColumns(): Array<ColumnInfo<*, *>> {
    val columns = ArrayList<ColumnInfo<*, *>>()
    columns.add(object : ColumnInfo<DefaultMutableTreeNode, Any>(VcsBundle.message("multiple.file.merge.column.name")) {
      override fun valueOf(node: DefaultMutableTreeNode) = node.userObject
      override fun getColumnClass(): Class<*> = TreeTableModel::class.java
    })

    val mergeInfoColumns = mergeSession?.mergeInfoColumns
    if (mergeInfoColumns != null) {
      var customColumnNames = mergeDialogCustomizer.getColumnNames()
      if (customColumnNames != null && customColumnNames.size != mergeInfoColumns.size) {
        LOG.error("Custom column names ($customColumnNames) don't match default columns ($mergeInfoColumns)")
        customColumnNames = null
      }
      mergeInfoColumns.mapIndexedTo(columns) { index, columnInfo ->
        ColumnInfoAdapter(columnInfo, customColumnNames?.get(index) ?: columnInfo.name)
      }
    }
    return columns.toTypedArray()
  }

  private class ColumnInfoAdapter(private val base: ColumnInfo<Any, Any>,
                                  private val columnName: @ColumnName String) : ColumnInfo<DefaultMutableTreeNode, Any>(columnName) {
    override fun valueOf(node: DefaultMutableTreeNode) = (node.userObject as? VirtualFile)?.let { base.valueOf(it) }
    override fun getMaxStringValue() = base.maxStringValue
    override fun getAdditionalWidth() = base.additionalWidth
    override fun getTooltipText() = base.tooltipText ?: columnName
  }

  private fun toggleGroupByDirectory(state: Boolean) {
    if (groupByDirectory == state) return
    groupByDirectory = state
    val firstSelectedFile = getSelectedFiles().firstOrNull()
    updateTree()
    if (firstSelectedFile != null) {
      val node = TreeUtil.findNodeWithObject(tableModel.root as DefaultMutableTreeNode, firstSelectedFile)
      node?.let { TreeUtil.selectNode(table.tree, node) }
    }
  }

  private fun updateTree() {
    val factory = when {
      project != null && groupByDirectory -> ChangesGroupingSupport.getFactory(ChangesGroupingSupport.DIRECTORY_GROUPING)
      else -> NoneChangesGroupingFactory
    }
    val model = TreeModelBuilder.buildFromVirtualFiles(project, factory, unresolvedFiles)
    tableModel.setRoot(model.root as TreeNode)
    TreeUtil.expandAll(table.tree)
    (table.model as? AbstractTableModel)?.fireTableDataChanged()
  }

  private fun updateButtonState() {
    val selectedFiles = getSelectedFiles()
    val haveSelection = selectedFiles.any()
    val haveUnmergeableFiles = selectedFiles.any { mergeSession?.canMerge(it) == false }
    val haveUnacceptableFiles = selectedFiles.any { mergeSession != null && mergeSession !is MergeSessionEx && !mergeSession.canMerge(it) }

    acceptYoursButton.isEnabled = haveSelection && !haveUnacceptableFiles
    acceptTheirsButton.isEnabled = haveSelection && !haveUnacceptableFiles
    mergeButton.isEnabled = haveSelection && !haveUnmergeableFiles
  }

  private fun getSelectedFiles(): List<VirtualFile> {
    return VcsTreeModelData.selected(table.tree).userObjects(VirtualFile::class.java)
  }

  override fun createActions(): Array<Action> {
    cancelAction.putValue(Action.NAME, CommonBundle.getCloseButtonText())
    return arrayOf(cancelAction)
  }

  override fun dispose() {
    project?.let { StoreReloadManager.getInstance(project).unblockReloadingProjectOnExternalChanges() }
    super.dispose()
  }

  @NonNls
  override fun getDimensionServiceKey(): String = "MultipleFileMergeDialog"

  @JvmSuppressWildcards
  protected open fun beforeResolve(files: Collection<VirtualFile>): Boolean {
    return true
  }

  private fun acceptRevision(resolution: MergeSession.Resolution) {
    assert(resolution == MergeSession.Resolution.AcceptedYours || resolution == MergeSession.Resolution.AcceptedTheirs)

    FileDocumentManager.getInstance().saveAllDocuments()
    val files = getSelectedFiles()

    ProgressManager.getInstance().run(object : Task.Modal(project,
                                                          VcsBundle.message(
                                                            "multiple.file.merge.dialog.progress.title.resolving.conflicts"), false) {
      override fun run(indicator: ProgressIndicator) {
        if (!beforeResolve(files)) {
          return
        }

        try {
          if (mergeSession is MergeSessionEx) {
            mergeSession.acceptFilesRevisions(files, resolution)

            for (file in files) {
              checkMarkModifiedProject(file)
            }

            markFilesProcessed(files, resolution)
          }
          else {
            for (file in files) {
              val data = mergeProvider.loadRevisions(file)
              ApplicationManager.getApplication().invokeAndWait({
                                                                  resolveFileViaContent(file, resolution, data)
                                                                }, indicator.modalityState)
              checkMarkModifiedProject(file)
              markFileProcessed(file, resolution)
            }
          }
        }
        catch (e: Exception) {
          LOG.warn(e)
          ApplicationManager.getApplication().invokeAndWait({
                                                              Messages.showErrorDialog(contentPanel,
                                                                                       VcsBundle.message(
                                                                                         "multiple.file.merge.dialog.message.error.saving.merged.data",
                                                                                         e.message))
                                                            }, indicator.modalityState)
        }
      }
    })

    updateModelFromFiles()
  }

  @RequiresEdt
  private fun resolveFileViaContent(file: VirtualFile, resolution: MergeSession.Resolution, data: MergeData) {
    if (!DiffUtil.makeWritable(project, file)) {
      throw IOException(UIBundle.message("file.is.read.only.message.text", file.presentableUrl))
    }

    val isCurrent = resolution == MergeSession.Resolution.AcceptedYours
    val message = if (isCurrent) VcsBundle.message("multiple.file.merge.dialog.command.name.accept.yours")
    else VcsBundle.message("multiple.file.merge.dialog.command.name.accept.theirs")

    writeCommandAction(project).withName(message).run<Exception> {
      if (isCurrent) {
        file.setBinaryContent(data.CURRENT)
      }
      else {
        file.setBinaryContent(data.LAST)
      }
    }
  }

  private fun markFilesProcessed(files: List<VirtualFile>, resolution: MergeSession.Resolution) {
    unresolvedFiles.removeAll(files)
    if (mergeSession is MergeSessionEx) {
      mergeSession.conflictResolvedForFiles(files, resolution)
    }
    else if (mergeSession != null) {
      files.forEach {
        mergeSession.conflictResolvedForFile(it, resolution)
      }
    }
    else {
      files.forEach {
        mergeProvider.conflictResolvedForFile(it)
      }
    }
    processedFiles.addAll(files)

    if (project != null) VcsDirtyScopeManager.getInstance(project).filesDirty(files, emptyList())
  }

  private fun markFileProcessed(file: VirtualFile, resolution: MergeSession.Resolution) {
    markFilesProcessed(listOf(file), resolution)
  }

  private fun updateModelFromFiles() {
    if (unresolvedFiles.isEmpty()) {
      doCancelAction()
    }
    else {
      var selIndex = table.selectionModel.minSelectionIndex
      updateTree()
      if (selIndex >= table.rowCount) {
        selIndex = table.rowCount - 1
      }
      table.selectionModel.setSelectionInterval(selIndex, selIndex)
      table.requestFocusInWindow()
    }
  }

  private fun showMergeDialog() {
    val requestFactory = DiffRequestFactory.getInstance()
    val files = getSelectedFiles()
    if (files.isEmpty()) return
    if (!beforeResolve(files)) {
      return
    }

    for (file in files) {
      val filePath = VcsUtil.getFilePath(file)

      val conflictData: ConflictData
      try {
        conflictData = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable<ConflictData, VcsException> {
          val mergeData = mergeProvider.loadRevisions(file)

          val title = tryCompute { mergeDialogCustomizer.getMergeWindowTitle(file) }

          val conflictTitles = listOf(
            tryCompute { mergeDialogCustomizer.getLeftPanelTitle(file) },
            tryCompute { mergeDialogCustomizer.getCenterPanelTitle(file) },
            tryCompute { mergeDialogCustomizer.getRightPanelTitle(file, mergeData.LAST_REVISION_NUMBER) }
          )

          val titleCustomizer = tryCompute { mergeDialogCustomizer.getTitleCustomizerList(filePath) }
                                ?: MergeDialogCustomizer.DEFAULT_CUSTOMIZER_LIST

          ConflictData(mergeData, title, conflictTitles, titleCustomizer)
        }, VcsBundle.message("multiple.file.merge.dialog.progress.title.loading.revisions"), true, project)
      }
      catch (ex: VcsException) {
        Messages.showErrorDialog(contentPanel, VcsBundle.message("multiple.file.merge.dialog.error.loading.revisions.to.merge", ex.message))
        break
      }

      val mergeData = conflictData.mergeData
      val byteContents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST)
      val contentTitles = conflictData.contentTitles
      val title = conflictData.title

      val callback = { result: MergeResult ->
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        if (document != null) FileDocumentManager.getInstance().saveDocument(document)
        checkMarkModifiedProject(file)

        if (result != MergeResult.CANCEL) {
          ProgressManager.getInstance()
            .runProcessWithProgressSynchronously({ markFileProcessed(file, getSessionResolution(result)) },
                                                 VcsBundle.message("multiple.file.merge.dialog.progress.title.resolving.conflicts"), true,
                                                 project, contentPanel)
        }
      }

      val request: MergeRequest
      try {
        if (mergeProvider.isBinary(file)) { // respect MIME-types in svn
          request = requestFactory.createBinaryMergeRequest(project, file, byteContents, title, contentTitles, callback)
        }
        else {
          request = requestFactory.createMergeRequest(project, file, byteContents, mergeData.CONFLICT_TYPE, title, contentTitles, callback)
        }

        MergeUtils.putRevisionInfos(request, mergeData)
      }
      catch (e: InvalidDiffRequestException) {
        if (e.cause is FileTooBigException) {
          Messages.showErrorDialog(contentPanel,
                                   VcsBundle.message("multiple.file.merge.dialog.message.file.too.big.to.be.loaded"),
                                   VcsBundle.message("multiple.file.merge.dialog.title.can.t.show.merge.dialog"))
        }
        else {
          LOG.error(e)
          Messages.showErrorDialog(contentPanel, e.message, VcsBundle.message("multiple.file.merge.dialog.title.can.t.show.merge.dialog"))
        }
        break
      }
      conflictData.contentTitleCustomizers.run {
        request.putUserData(EDITORS_TITLE_CUSTOMIZER, listOf(leftTitleCustomizer, centerTitleCustomizer, rightTitleCustomizer))
      }
      DiffManager.getInstance().showMerge(project, request)
    }
    updateModelFromFiles()
  }

  private fun getSessionResolution(result: MergeResult): MergeSession.Resolution = when (result) {
    MergeResult.LEFT -> MergeSession.Resolution.AcceptedYours
    MergeResult.RIGHT -> MergeSession.Resolution.AcceptedTheirs
    MergeResult.RESOLVED -> MergeSession.Resolution.Merged
    else -> throw IllegalArgumentException(result.name)
  }

  private fun checkMarkModifiedProject(file: VirtualFile) {
    MergeUtil.reportProjectFileChangeIfNeeded(project, file)
  }

  override fun getPreferredFocusedComponent(): JComponent? = table

  private fun <T> tryCompute(task: () -> T): T? {
    try {
      return task()
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: VcsException) {
      LOG.warn(e)
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    return null
  }

  companion object {
    private val LOG = Logger.getInstance(MultipleFileMergeDialog::class.java)
  }

  private data class ConflictData(
    val mergeData: MergeData,
    val title: @NlsContexts.DialogTitle String?,
    val contentTitles: List<@NlsContexts.Label String?>,
    val contentTitleCustomizers: MergeDialogCustomizer.DiffEditorTitleCustomizerList
  )
}