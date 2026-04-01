// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.CommonBundle
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.InvalidDiffRequestException
import com.intellij.diff.merge.MergeConflictModel
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.MergeUtil
import com.intellij.diff.statistics.MergeAction
import com.intellij.diff.statistics.MergeStatisticsCollector
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.ide.DataManager
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ColumnName
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesComparator
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.NoneChangesGroupingFactory
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.vcs.merge.flow.IterativeMergeFlowDelegate
import com.intellij.openapi.vcs.merge.flow.MergeFlowDelegate
import com.intellij.openapi.vcs.merge.flow.OneShotMergeFlowDelegate
import com.intellij.openapi.vcs.merge.registry.MergeConflictFileSuggestion
import com.intellij.openapi.vcs.merge.registry.MergeConflictIterativeResolution
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.TableUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.treeStructure.treetable.DefaultTreeTableExpander
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.event.ComponentAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

open class MultipleFileMergeDialog(
  private val project: Project?,
  files: List<VirtualFile>,
  private val mergeProvider: MergeProvider,
  private val mergeDialogCustomizer: MergeDialogCustomizer,
) : DialogWrapper(project) {
  private val unresolvedFiles = files.toMutableList()
  private val _processedFiles = mutableListOf<VirtualFile>()
  private val mergeSession = (mergeProvider as? MergeProvider2)?.createMergeSession(files)
  val processedFiles: List<VirtualFile> get() = _processedFiles
  private val columns = CustomColumns.createColumns(mergeDialogCustomizer, mergeSession)
  private val tableModel = ListTreeTableModelOnColumns(DefaultMutableTreeNode(), columns)

  private val table = MergeConflictsTreeTable(tableModel).apply {
    val virtualFileRenderer = object : ChangesBrowserNodeRenderer(project, { !groupByDirectory }, false) {
      override fun calcFocusedState() = UIUtil.isAncestor(peer.window, IdeFocusManager.getInstance(project).focusOwner)
    }

    setTreeCellRenderer(virtualFileRenderer)
    tree.addTreeSelectionListener { updateButtonState() }
    rowHeight = virtualFileRenderer.preferredSize.height
    preferredScrollableViewportSize = JBUI.size(600, 300)

    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, event)) return false
        showMergeDialog()
        return true
      }
    }.installOn(this)
  }.also { table ->
    TableSpeedSearch.installOn(table, Convertor { (it as? VirtualFile)?.name })

    DataManager.registerDataProvider(table) { dataId ->
      when {
        PlatformDataKeys.TREE_EXPANDER.`is`(dataId) -> DefaultTreeTableExpander(table)
        else -> null
      }
    }
  }

  private var groupByDirectory: Boolean = false
    get() = when {
      project != null -> VcsConfiguration.getInstance(project).GROUP_MULTIFILE_MERGE_BY_DIRECTORY
      else -> field
    }
    set(value) = when {
      project != null -> VcsConfiguration.getInstance(project).GROUP_MULTIFILE_MERGE_BY_DIRECTORY = value
      else -> field = value
    }

  private val iterativeDataHolder =
    if (MergeConflictIterativeResolution.isEnabled()) MergeConflictIterativeDataHolder(project, disposable) else null

  private var popupCloseListener: ComponentAdapter? = null

  private val mergeFlowDelegate: MergeFlowDelegate = if (project != null && iterativeDataHolder != null) IterativeMergeFlowDelegate(
    project = project,
    table = table,
    columnNames = columns.map { it.name },
    files = files,
    mergeDialogCustomizer = mergeDialogCustomizer,
    rootPane = rootPane,
    onClose = ::doCancelAction,
    acceptForResolution = ::acceptForResolution,
    showMergeDialog = ::showMergeDialog,
    toggleGroupByDirectory = ::toggleGroupByDirectory,
    getGroupByDirectory = { groupByDirectory },
    iterativeDataHolder = iterativeDataHolder,
    resolveAutomatically = { resolveAutomatically(project, iterativeDataHolder) },
    updateTable = ::updateModelFromFiles
  )
  else OneShotMergeFlowDelegate(
    project = project,
    table = table,
    files = files,
    mergeDialogCustomizer = mergeDialogCustomizer,
    rootPane = rootPane,
    onClose = ::doCancelAction,
    acceptForResolution = ::acceptForResolution,
    showMergeDialog = ::showMergeDialog,
    toggleGroupByDirectory = ::toggleGroupByDirectory,
    getGroupByDirectory = { groupByDirectory })

  init {
    project?.blockReloadingProjectOnExternalChanges()
    title = mergeDialogCustomizer.getMultipleFileDialogTitle()
    init()

    updateTree(SetDefaultTreeStateStrategy())
    popupCloseListener = MergeUIUtil.installPopupAutoCloseOnResize(rootPane)
  }

  override fun createCenterPanel(): JComponent {
    return mergeFlowDelegate.createCenterPanel()
  }

  override fun createSouthPanel(): JComponent? {
    return mergeFlowDelegate.createSouthPanel() ?: super.createSouthPanel()
  }

  private fun updateButtonState() {
    val selectedFiles = table.selectedFiles
    val haveUnmergeableFiles = selectedFiles.any { mergeSession?.canMerge(it) == false }
    val haveUnacceptableFiles = selectedFiles.any { mergeSession != null && mergeSession !is MergeSessionEx && !mergeSession.canMerge(it) }

    mergeFlowDelegate.onTreeChanged(selectedFiles,
                                    unmergeableFileSelected = haveUnmergeableFiles,
                                    unacceptableFileSelected = haveUnacceptableFiles)
  }

  private fun toggleGroupByDirectory(state: Boolean) {
    if (groupByDirectory == state) return
    groupByDirectory = state
    updateTree(OnGroupingChangeTreeStateStrategy())
  }

  private fun <State> updateTree(treeStateStrategy: TreeTableStateStrategy<State>) {
    val factory = when {
      project != null && groupByDirectory -> ChangesGroupingSupport.findFactory(ChangesGroupingSupport.DIRECTORY_GROUPING)
                                             ?: NoneChangesGroupingFactory
      else -> NoneChangesGroupingFactory
    }
    val model = mergeFlowDelegate.buildTreeModel(project, factory, unresolvedFiles)

    val savedState = treeStateStrategy.saveState(table)
    tableModel.setRoot(model.root as TreeNode)
    treeStateStrategy.restoreState(table, savedState)

    (table.model as? AbstractTableModel)?.fireTableDataChanged()

    TableUtil.scrollSelectionToVisible(table)
  }

  override fun createActions(): Array<Action> = mergeFlowDelegate.createActions().toTypedArray()

  override fun dispose() {
    project?.unblockReloadingProjectOnExternalChanges()
    SwingUtilities.getWindowAncestor(rootPane)?.removeComponentListener(popupCloseListener)
    super.dispose()
  }

  @NonNls
  override fun getDimensionServiceKey(): String = "MultipleFileMergeDialog"
  override fun getPreferredFocusedComponent(): JComponent = table

  @JvmSuppressWildcards
  protected open fun beforeResolve(files: Collection<VirtualFile>): Boolean {
    return true
  }

  @Throws(ProcessCanceledException::class)
  @RequiresBlockingContext
  @RequiresEdt
  private fun resolveAutomatically(
    project: Project,
    iterativeDataHolder: MergeConflictIterativeDataHolder,
  ) {
    val files = getUnresolvedFiles()
    if (files.isEmpty()) return
    if (!beforeResolve(files)) return

    runWithErrorHandling {
      runWithModalProgressBlocking(ModalTaskOwner.component(contentPanel),
                                   VcsBundle.message("multiple.file.merge.dialog.progress.title.resolving.conflicts")) {
        for (file in files) {
          val request = createMergeRequest(file, DiffRequestFactory.getInstance(), callback = null)
          val model = iterativeDataHolder.prepareModelIfSupported(file, request) ?: continue

          writeAction {
            model.resolveAllChangesAutomatically()

            saveDocument(file)
            checkMarkModifiedProject(project, file)
          }
        }
      }
    }
    updateTree(SetDefaultTreeStateStrategy())
    updateModelFromFiles()
  }

  @RequiresEdt
  private fun acceptForResolution(resolution: MergeSession.Resolution) {
    assert(resolution.yoursOrTheirs())
    val files = table.selectedFiles
    runWithErrorHandling {
      runWithModalProgressBlocking(ModalTaskOwner.component(contentPanel),
                                   VcsBundle.message("multiple.file.merge.dialog.progress.title.resolving.conflicts")) {
        val (binaryFiles, textFiles) = files.partition(mergeProvider::isBinary)
        acceptRevision(resolution, binaryFiles)

        if (iterativeDataHolder == null) {
          acceptRevision(resolution, textFiles)
        }
        else {
          val iterativeFilesWithModels = mutableListOf<Pair<VirtualFile, MergeConflictModel>>()
          val nonIterativeFiles = mutableListOf<VirtualFile>()
          for (file in textFiles) {
            val request = createMergeRequest(file, DiffRequestFactory.getInstance(), callback = null)
            // Need to make sure that the iterative is actually possible for that given request
            val model = iterativeDataHolder.prepareModelIfSupported(file, request)
            if (model != null) {
              iterativeFilesWithModels.add(file to model)
            }
            else {
              nonIterativeFiles.add(file)
            }
          }

          withContext(Dispatchers.UiWithModelAccess) {
            acceptRevisionForIterativeResolution(iterativeFilesWithModels, resolution, columns[1].name, columns[2].name)
          }
          acceptRevision(resolution, nonIterativeFiles)
        }
      }
    }
  }

  private suspend fun acceptRevision(resolution: MergeSession.Resolution, files: List<VirtualFile>) {
    if (files.isEmpty()) return
    val side = if (resolution == MergeSession.Resolution.AcceptedYours) MergeAction.LEFT else MergeAction.RIGHT
    MergeStatisticsCollector.logButtonClickOnTable(project, side)

    if (!beforeResolve(files)) {
      return
    }

    runCatching {
      if (mergeSession is MergeSessionEx) {
        mergeSession.acceptFilesRevisions(files, resolution)
        for (file in files) {
          checkMarkModifiedProject(project, file)
        }

        markFilesProcessed(files, resolution)
      }
      else {
        for (file in files) {
          val data = mergeProvider.loadRevisions(file)
          withContext(Dispatchers.UiWithModelAccess) {
            resolveFileViaContent(file, resolution, data)
          }
          checkMarkModifiedProject(project, file)
          markFileProcessed(file, resolution)
        }
      }
    }.getOrHandleException {
      withContext(Dispatchers.UiWithModelAccess) {
        Messages.showErrorDialog(contentPanel, VcsBundle.message(
          "multiple.file.merge.dialog.message.error.saving.merged.data",
          it.message))
      }
    }

    withContext(Dispatchers.UI) {
      updateModelFromFiles()
    }
  }

  @RequiresEdt
  private fun acceptRevisionForIterativeResolution(
    filesWithModel: List<Pair<VirtualFile, MergeConflictModel>>,
    resolution: MergeSession.Resolution,
    yoursLabel: @Nls String,
    theirsLabel: @Nls String,
  ) {
    if (filesWithModel.isEmpty()) return
    if (filesWithModel.any { (_,model) ->
        model.getResolvedChanges().isNotEmpty()
      }) {
      val confirmed = MessageDialogBuilder
        .yesNo(VcsBundle.message("multiple.file.iterative.merge.accept.confirmation.title"),
               VcsBundle.message("multiple.file.iterative.merge.accept.confirmation.message",
                                 filesWithModel.size,
                                 if (resolution == MergeSession.Resolution.AcceptedYours) yoursLabel else theirsLabel))
        .yesText(VcsBundle.message("multiple.file.iterative.merge.accept.confirmation.yes"))
        .noText(CommonBundle.getCancelButtonText())
        .icon(Messages.getQuestionIcon())
        .ask(project)
      if (!confirmed) return
    }
    filesWithModel.forEach { (file, model) ->
      acceptRevisionForFileIterativeResolution(file, model, resolution)
    }

    updateModelFromFiles()
  }

  @RequiresBlockingContext
  @RequiresEdt
  private fun acceptRevisionForFileIterativeResolution(
    file: VirtualFile,
    mergeConflictModel: MergeConflictModel,
    resolution: MergeSession.Resolution,
  ) {
    val side = if (resolution == MergeSession.Resolution.AcceptedTheirs) Side.RIGHT else Side.LEFT
    mergeConflictModel.acceptRevisionForSide(side)
    saveDocument(file)
    checkMarkModifiedProject(project, file)
  }

  @RequiresEdt
  private fun resolveFileViaContent(file: VirtualFile, resolution: MergeSession.Resolution, data: MergeData) {
    if (!DiffUtil.makeWritable(project, file)) {
      throw IOException(UIBundle.message("file.is.read.only.message.text", file.presentableUrl))
    }

    val isCurrent = resolution == MergeSession.Resolution.AcceptedYours

    writeCommandAction(project).withName(resolution.presentableName).run<Exception> {
      if (isCurrent) {
        file.setBinaryContent(data.CURRENT)
      }
      else {
        file.setBinaryContent(data.LAST)
      }
    }
  }

  // Under the hood this is calling [com.intellij.dvcs.repo.VcsRepositoryManager.getRepositoryForRoot(com.intellij.openapi.vfs.VirtualFile)]
  // that needs to be done in a background thread
  @RequiresBackgroundThread
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
    _processedFiles.addAll(files)

    if (project != null) VcsDirtyScopeManager.getInstance(project).filesDirty(files, emptyList())
  }

  @RequiresBackgroundThread
  private fun markFileProcessed(file: VirtualFile, resolution: MergeSession.Resolution) {
    markFilesProcessed(listOf(file), resolution)
  }

  private fun updateModelFromFiles() {
    if (unresolvedFiles.isEmpty()) {
      doCancelAction()
    }
    else {
      updateTree(OnModelChangeTreeStateStrategy())
      table.requestFocusInWindow()
    }
  }

  override fun doCancelAction() {
    finishResolution()
    super.doCancelAction()
  }

  private fun finishResolution() {
    val iterativelyResolved = iterativeDataHolder?.getResolvedFiles() ?: return
    iterativelyResolved.forEach { file ->
      saveDocument(file)
      checkMarkModifiedProject(project, file)

      runWithModalProgressBlocking(ModalTaskOwner.component(contentPanel),
                                   VcsBundle.message("multiple.file.merge.dialog.progress.title.resolving.conflicts")) {
        markFileProcessed(file, getSessionResolution(MergeResult.RESOLVED))
      }
    }
  }

  @RequiresBlockingContext
  @RequiresEdt
  private fun showMergeDialog() {
    val files = getFilesToOpen()
    if (files.isEmpty()) return
    if (!beforeResolve(files)) {
      return
    }

    runWithErrorHandling {
      for (file in files) {
        val result = showMergeDialogForFile(file)
        if (result == MergeResult.CANCEL) return@runWithErrorHandling
      }
    }

    updateModelFromFiles()
  }

  @RequiresBlockingContext
  @RequiresEdt
  private fun showMergeDialogForFile(file: VirtualFile): MergeResult {
    var mergeResult: MergeResult? = null
    val request = runWithModalProgressBlocking(ModalTaskOwner.component(this.contentPanel),
                                               VcsBundle.message("multiple.file.merge.dialog.progress.title.resolving.conflicts")) {
      createMergeRequest(file, DiffRequestFactory.getInstance()) { result: MergeResult ->
        mergeResult = result
        saveDocument(file)
        checkMarkModifiedProject(project, file)
        iterativeDataHolder?.getMergeConflictModel(file)?.markReviewed()
        if (result != MergeResult.CANCEL) {
          val iterativelyResolved = iterativeDataHolder?.isFileResolved(file) ?: false

          if (!iterativelyResolved) {
            runWithModalProgressBlocking(ModalTaskOwner.component(contentPanel),
                                         VcsBundle.message("multiple.file.merge.dialog.progress.title.resolving.conflicts")) {
              markFileProcessed(file, getSessionResolution(result))
            }
          }
        }
      }.also { request ->
        iterativeDataHolder?.prepareModelIfSupported(file, request)
      }
    }

    DiffManager.getInstance().showMerge(project, request)
    return mergeResult!!
  }

  private fun getFilesToOpen(): List<VirtualFile> {
    if (!MergeConflictFileSuggestion.isEnabled()) return table.selectedFiles

    val comparator = ChangesComparator.getVirtualFileComparator(!groupByDirectory)
    // 1. Selected files (sorted)
    val selected = table.selectedFiles.sortedWith(comparator)

    // 2. Unresolved files (sorted)
    val unresolved = getUnresolvedFiles().sortedWith(comparator)

    // 3. Resolved but not reviewed files (sorted)
    val resolvedNotReviewed = getResolvedFiles()
      .filter { iterativeDataHolder?.isFileReviewed(it) == false }
      .sortedWith(comparator)

    // Combine and remove duplicates while preserving the order
    return (selected + unresolved + resolvedNotReviewed).distinct()
  }

  private fun getUnresolvedFiles(): List<VirtualFile> = unresolvedFiles - getResolvedFiles()
  private fun getResolvedFiles(): Set<VirtualFile> = (iterativeDataHolder?.getResolvedFiles() ?: emptySet())

  @RequiresEdt
  private fun runWithErrorHandling(block: () -> Unit) {
    try {
      block()
    }
    catch (ex: VcsException) {
      Messages.showErrorDialog(contentPanel, VcsBundle.message("multiple.file.merge.dialog.error.loading.revisions.to.merge", ex.message))
    }
    catch (e: InvalidDiffRequestException) {
      when (e.cause) {
        is FileTooBigException -> {
          Messages.showErrorDialog(contentPanel,
                                   VcsBundle.message("multiple.file.merge.dialog.message.file.too.big.to.be.loaded"),
                                   VcsBundle.message("multiple.file.merge.dialog.title.can.t.show.merge.dialog"))
        }

        is ReadOnlyModificationException -> {
          Messages.showErrorDialog(contentPanel,
                                   DiffBundle.message("error.cant.resolve.conflicts.in.a.read.only.file"),
                                   VcsBundle.message("multiple.file.merge.dialog.title.can.t.show.merge.dialog"))
        }
        else -> {
          LOG.error(e)
          Messages.showErrorDialog(contentPanel, e.message, VcsBundle.message("multiple.file.merge.dialog.title.can.t.show.merge.dialog"))
        }
      }
    }
  }

  private suspend fun createMergeRequest(
    file: VirtualFile,
    requestFactory: DiffRequestFactory,
    callback: ((MergeResult) -> Unit)?,
  ): MergeRequest {
    val conflictData = loadConflictData(file)
    val mergeData = conflictData.mergeData
    val byteContents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST)
    val contentTitles = conflictData.contentTitles
    val title = conflictData.title

    return if (mergeProvider.isBinary(file)) { // respect MIME-types in svn
      requestFactory.createBinaryMergeRequest(project, file, byteContents, title, contentTitles, callback)
    }
    else {
      requestFactory.createMergeRequest(project, file, byteContents, mergeData.CONFLICT_TYPE, title, contentTitles, callback)
    }.also {
      MergeUtils.putRevisionInfos(it, mergeData)
      conflictData.contentTitleCustomizers.run {
        DiffUtil.addTitleCustomizers(it, listOf(leftTitleCustomizer, centerTitleCustomizer, rightTitleCustomizer))
      }
    }
  }

  private suspend fun loadConflictData(file: VirtualFile): ConflictData {
    val filePath = VcsUtil.getFilePath(file)
    val mergeData = withContext(Dispatchers.IO) {
      mergeProvider.loadRevisions(file)
    }

    val title = tryCompute { mergeDialogCustomizer.getMergeWindowTitle(file) }

    val conflictTitles = listOf(
      tryCompute { mergeDialogCustomizer.getLeftPanelTitle(file) },
      tryCompute { mergeDialogCustomizer.getCenterPanelTitle(file) },
      tryCompute { mergeDialogCustomizer.getRightPanelTitle(file, mergeData.LAST_REVISION_NUMBER) }
    )

    val titleCustomizer = tryCompute { mergeDialogCustomizer.getTitleCustomizerList(filePath) }
                          ?: MergeDialogCustomizer.DEFAULT_CUSTOMIZER_LIST

    return ConflictData(mergeData, title, conflictTitles, titleCustomizer)
  }
}

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

private val LOG = Logger.getInstance(MultipleFileMergeDialog::class.java)

private fun Project?.blockReloadingProjectOnExternalChanges() {
  this ?: return
  StoreReloadManager.getInstance(this).blockReloadingProjectOnExternalChanges()
}

private fun Project?.unblockReloadingProjectOnExternalChanges() {
  this ?: return
  StoreReloadManager.getInstance(this).unblockReloadingProjectOnExternalChanges()
}

private fun checkMarkModifiedProject(project: Project?, file: VirtualFile) {
  MergeUtil.reportProjectFileChangeIfNeeded(project, file)
}

private fun saveDocument(file: VirtualFile) {
  val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return
  application.runWriteAction { FileDocumentManager.getInstance().saveDocument(document) }
}

private fun MergeSession.Resolution.yoursOrTheirs(): Boolean = when (this) {
  MergeSession.Resolution.AcceptedYours, MergeSession.Resolution.AcceptedTheirs -> true
  MergeSession.Resolution.Merged -> false
}

private fun getSessionResolution(result: MergeResult): MergeSession.Resolution = when (result) {
  MergeResult.LEFT -> MergeSession.Resolution.AcceptedYours
  MergeResult.RIGHT -> MergeSession.Resolution.AcceptedTheirs
  MergeResult.RESOLVED -> MergeSession.Resolution.Merged
  MergeResult.CANCEL -> throw IllegalArgumentException(result.name)
}

private val TreeTable.selectedFiles: List<VirtualFile>
  get() = VcsTreeModelData.selected(tree).userObjects(VirtualFile::class.java)

private object CustomColumns {
  fun createColumns(customizer: MergeDialogCustomizer, session: MergeSession?): Array<ColumnInfo<*, *>> {
    val columns = ArrayList<ColumnInfo<*, *>>()
    val name = if (MergeConflictIterativeResolution.isEnabled()) "" else VcsBundle.message("multiple.file.merge.column.name")

    columns.add(object : ColumnInfo<DefaultMutableTreeNode, Any>(name) {
      override fun valueOf(node: DefaultMutableTreeNode) = node.userObject
      override fun getColumnClass(): Class<*> = TreeTableModel::class.java
    })

    val mergeInfoColumns = session?.mergeInfoColumns
    if (mergeInfoColumns != null) {
      var customColumnNames = customizer.getColumnNames()
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


  private class ColumnInfoAdapter(
    private val base: ColumnInfo<Any, Any>,
    private val columnName: @ColumnName String,
  ) : ColumnInfo<DefaultMutableTreeNode, Any>(columnName) {
    override fun valueOf(node: DefaultMutableTreeNode) = (node.userObject as? VirtualFile)?.let { base.valueOf(it) }
    override fun getMaxStringValue() = base.maxStringValue
    override fun getAdditionalWidth() = base.additionalWidth
    override fun getTooltipText() = base.tooltipText ?: columnName
  }
}

/**
 * See [ChangesTree.TreeStateStrategy] that cannot be applied to [TreeTable]
 */
private interface TreeTableStateStrategy<T> {
  fun saveState(table: TreeTable): T

  fun restoreState(table: TreeTable, state: T)
}

private class SetDefaultTreeStateStrategy : TreeTableStateStrategy<Any?> {
  override fun saveState(table: TreeTable): Any? = null

  override fun restoreState(table: TreeTable, state: Any?) {
    TreeUtil.expandAll(table.tree)
    TreeUtil.promiseSelectFirst(table.tree)
  }
}

private class OnGroupingChangeTreeStateStrategy : TreeTableStateStrategy<OnGroupingChangeTreeStateStrategy.SelectionState> {
  override fun saveState(table: TreeTable): SelectionState {
    val selectedFiles = table.selectedFiles
    return SelectionState(selectedFiles)
  }

  override fun restoreState(table: TreeTable, state: SelectionState) {
    TreeUtil.expandAll(table.tree)

    val newRoot = table.tree.model.root as DefaultMutableTreeNode
    val treePaths = state.selectedFiles
      .mapNotNull { file -> TreeUtil.findNodeWithObject(newRoot, file) }
      .map { node -> TreeUtil.getPath(newRoot, node) }
    TreeUtil.selectPaths(table.tree, treePaths)

    if (table.tree.selectionCount == 0) {
      TreeUtil.promiseSelectFirstLeaf(table.tree)
    }
  }

  class SelectionState(val selectedFiles: List<VirtualFile>)
}

private class OnModelChangeTreeStateStrategy : TreeTableStateStrategy<OnModelChangeTreeStateStrategy.SelectionState> {
  override fun saveState(table: TreeTable): SelectionState {
    val treeState = TreeState.createOn(table.tree, false, true)
    val firstSelectedIndex = table.selectionModel.minSelectionIndex
    return SelectionState(treeState, firstSelectedIndex)
  }

  override fun restoreState(table: TreeTable, state: SelectionState) {
    state.treeState.applyTo(table.tree)
    TreeUtil.expandAll(table.tree)
    if (table.tree.selectionCount == 0) {
      val toSelect = state.firstSelectedIndex.coerceAtMost(table.rowCount - 1)
      table.selectionModel.setSelectionInterval(toSelect, toSelect)
    }
  }

  class SelectionState(val treeState: TreeState, val firstSelectedIndex: Int)
}

private data class ConflictData(
  val mergeData: MergeData,
  val title: @NlsContexts.DialogTitle String?,
  val contentTitles: List<@NlsContexts.Label String?>,
  val contentTitleCustomizers: MergeDialogCustomizer.DiffEditorTitleCustomizerList,
)

private val MergeSession.Resolution.presentableName: @Nls String
  get() = when (this) {
    MergeSession.Resolution.Merged -> TODO()
    MergeSession.Resolution.AcceptedYours -> VcsBundle.message("multiple.file.merge.dialog.command.name.accept.yours")
    MergeSession.Resolution.AcceptedTheirs -> VcsBundle.message("multiple.file.merge.dialog.command.name.accept.theirs")
  }