// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.merge

import com.intellij.CommonBundle
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.InvalidDiffRequestException
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.MergeUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.impl.mergeTool.MergeVersion
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.openapi.vcs.changes.ui.NoneChangesGroupingFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.layout.*
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.NonNls
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.io.IOException
import java.util.*
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

/**
 * @author yole
 */
open class MultipleFileMergeDialog2(
  private val project: Project,
  files: List<VirtualFile>,
  private val mergeProvider: MergeProvider,
  private val mergeDialogCustomizer: MergeDialogCustomizer
) : DialogWrapper(project) {

  private var files = files.toMutableList()
  private val mergeSession = (mergeProvider as? MergeProvider2)?.createMergeSession(files)
  val processedFiles: MutableList<VirtualFile> = mutableListOf<VirtualFile>()
  private lateinit var table: TreeTable
  private lateinit var acceptYoursButton: JButton
  private lateinit var acceptTheirsButton: JButton
  private lateinit var mergeButton: JButton
  private val tableModel = ListTreeTableModelOnColumns(DefaultMutableTreeNode(), createColumns())
  private val projectManager = ProjectManagerEx.getInstanceEx()
  private var groupByDirectory = VcsConfiguration.getInstance(project).GROUP_MULTIFILE_MERGE_BY_DIRECTORY

  private val virtualFileRenderer = object : ChangesBrowserNodeRenderer(project, { !groupByDirectory }, false) {
    override fun calcFocusedState() = table.hasFocus()
  }

  init {
    projectManager.blockReloadingProjectOnExternalChanges()
    title = mergeDialogCustomizer.multipleFileDialogTitle
    virtualFileRenderer.font = UIUtil.getListFont()

    @Suppress("LeakingThis")
    init()

    updateColumnSizes()
    updateTree()
    table.tree.selectionModel.addTreeSelectionListener { updateButtonState() }
    selectFirstFile()
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        showMergeDialog()
        return true
      }
    }.installOn(table)

    TableSpeedSearch(table, Convertor { (it as? VirtualFile)?.name })
  }

  private fun selectFirstFile() {
    if (!groupByDirectory) {
      table.selectionModel.setSelectionInterval(0, 0)
    }
    else {
      table.tree.selectionPath = TreeUtil.getFirstLeafNodePath(table.tree)
    }
  }

  override fun createCenterPanel(): JComponent {
    return panel(LCFlags.disableMagic) {
      val description = mergeDialogCustomizer.getMultipleFileMergeDescription(files)
      if (!description.isNullOrBlank()) {
        row {
          label(description!!)
        }
      }

      row {
        scrollPane(TreeTable(tableModel).also {
          table = it
          it.tree.isRootVisible = false
          it.setTreeCellRenderer(virtualFileRenderer)
          if (tableModel.columnCount > 1) {
            it.setShowColumns(true)
          }
          it.rowHeight = virtualFileRenderer.preferredSize.height
        }, growX, growY, pushX, pushY)

        cell(isVerticalFlow = true) {
          JButton("Accept Yours").also {
            it.addActionListener { acceptRevision(MergeSession.Resolution.AcceptedYours) }
            acceptYoursButton = it
          }(growX)
          JButton("Accept Theirs").also {
            it.addActionListener { acceptRevision(MergeSession.Resolution.AcceptedTheirs) }
            acceptTheirsButton = it
          }(growX)
          val mergeAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
              showMergeDialog()
            }
          }
          mergeAction.putValue(DialogWrapper.DEFAULT_ACTION, java.lang.Boolean.TRUE)
          createJButtonForAction(mergeAction).also {
            it.text = "Merge..."
            mergeButton = it
          }(growX)
        }
      }

      row {
        checkBox("Group files by directory", groupByDirectory) { _, component ->
          toggleGroupByDirectory(component.isSelected)
        }
      }
    }
  }

  private fun createColumns(): Array<ColumnInfo<*, *>> {
    val columns = ArrayList<ColumnInfo<*, *>>()
    columns.add(object : ColumnInfo<DefaultMutableTreeNode, Any>(VcsBundle.message("multiple.file.merge.column.name")) {
      override fun valueOf(node: DefaultMutableTreeNode) = node.userObject
      override fun getColumnClass(): Class<*> = TreeTableModel::class.java
    })

    mergeSession?.mergeInfoColumns?.mapTo(columns) { ColumnInfoAdapter(it) }
    return columns.toTypedArray()
  }

  private class ColumnInfoAdapter(private val base: ColumnInfo<Any, Any>) : ColumnInfo<DefaultMutableTreeNode, Any>(base.name) {
    override fun valueOf(node: DefaultMutableTreeNode) = (node.userObject as? VirtualFile)?.let { base.valueOf(it) }
    override fun getMaxStringValue() = base.maxStringValue
    override fun getAdditionalWidth() = base.additionalWidth
  }

  private fun updateColumnSizes() {
    for ((index, columnInfo) in tableModel.columns.withIndex()) {
      val column = table.columnModel.getColumn(index)
      columnInfo.maxStringValue?.let {
        column.maxWidth = Math.max(table.getFontMetrics(table.font).stringWidth(it),
                                   table.getFontMetrics(table.tableHeader.font).stringWidth(columnInfo.name)) + columnInfo.additionalWidth
      }
    }
  }

  private fun toggleGroupByDirectory(state: Boolean) {
    groupByDirectory = state
    VcsConfiguration.getInstance(project).GROUP_MULTIFILE_MERGE_BY_DIRECTORY = groupByDirectory
    val firstSelectedFile = getSelectedFiles().firstOrNull()
    updateTree()
    if (firstSelectedFile != null) {
      val node = TreeUtil.findNodeWithObject(tableModel.root as DefaultMutableTreeNode, firstSelectedFile)
      node?.let { TreeUtil.selectNode(table.tree, node) }
    }
  }

  private fun updateTree() {
    val factory = if (groupByDirectory)
      ChangesGroupingSupport.collectFactories(project).getByKey(ChangesGroupingSupport.DIRECTORY_GROUPING)
    else
      NoneChangesGroupingFactory
    val model = TreeModelBuilder.buildFromVirtualFiles(project, factory, files)
    tableModel.setRoot(model.root as TreeNode)
    TreeUtil.expandAll(table.tree)
  }

  private fun updateButtonState() {
    val selectedFiles = getSelectedFiles()
    val haveSelection = selectedFiles.any()
    val haveUnmergeableFiles = selectedFiles.any { mergeSession?.canMerge(it) == false }

    acceptYoursButton.isEnabled = haveSelection
    acceptTheirsButton.isEnabled = haveSelection
    mergeButton.isEnabled = haveSelection && !haveUnmergeableFiles
  }


  private fun getSelectedFiles(): List<VirtualFile> {
    return TreeUtil.collectSelectedObjectsOfType(table.tree, VirtualFile::class.java)
  }

  protected fun beforeResolve(files: Collection<VirtualFile>): Boolean {
    return true
  }

  override fun createActions(): Array<Action> = arrayOf(cancelAction)


  override fun getCancelAction(): Action = super.getCancelAction().apply {
    putValue(Action.NAME, CommonBundle.getCloseButtonText())
  }

  override fun dispose() {
    projectManager.unblockReloadingProjectOnExternalChanges()
    super.dispose()
  }

  @NonNls
  override fun getDimensionServiceKey(): String = "MultipleFileMergeDialog"

  private fun acceptRevision(resolution: MergeSession.Resolution) {
    FileDocumentManager.getInstance().saveAllDocuments()
    val files = getSelectedFiles()
    if (!beforeResolve(files)) {
      return
    }

    try {
      for (file in files) {
        acceptFileRevision(file, resolution)
        checkMarkModifiedProject(file)
        markFileProcessed(file, resolution)
      }
    }
    catch (e: Exception) {
      LOG.warn(e)
      Messages.showErrorDialog(contentPanel, "Error saving merged data: " + e.message)
    }

    updateModelFromFiles()
  }

  private fun acceptFileRevision(file: VirtualFile, resolution: MergeSession.Resolution) {
    if (mergeSession?.canMerge(file)  == false) return

    if (mergeSession?.acceptFileRevision(file, resolution)  == true) return

    if (!DiffUtil.makeWritable(project, file)) {
      throw IOException("File is read-only: " + file.presentableName)
    }

    val isCurrent = resolution == MergeSession.Resolution.AcceptedYours
    val data = mergeProvider.loadRevisions(file)

    writeCommandAction(project).withName("Accept " + if (isCurrent) "Yours" else "Theirs").run<Exception> {
      if (isCurrent) {
        file.setBinaryContent(data.CURRENT)
      }
      else {
        file.setBinaryContent(data.LAST)
      }
    }
  }

  private fun markFileProcessed(file: VirtualFile, resolution: MergeSession.Resolution) {
    files.remove(file)
    if (mergeSession != null) {
      mergeSession.conflictResolvedForFile(file, resolution)
    }
    else {
      mergeProvider.conflictResolvedForFile(file)
    }
    processedFiles.add(file)
    VcsDirtyScopeManager.getInstance(project).fileDirty(file)
  }

  private fun updateModelFromFiles() {
    if (files.isEmpty()) {
      doCancelAction()
    }
    else {
      var selIndex = table.selectionModel.minSelectionIndex
      updateTree()
      if (selIndex >= table.rowCount) {
        selIndex = table.rowCount - 1
      }
      table.selectionModel.setSelectionInterval(selIndex, selIndex)
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
      val mergeData: MergeData
      try {
        mergeData = mergeProvider.loadRevisions(file)
      }
      catch (ex: VcsException) {
        Messages.showErrorDialog(contentPanel, "Error loading revisions to merge: " + ex.message)
        break
      }

      if (mergeData.CURRENT == null || mergeData.LAST == null || mergeData.ORIGINAL == null) {
        Messages.showErrorDialog(contentPanel, "Error loading revisions to merge")
        break
      }

      val leftTitle = mergeDialogCustomizer.getLeftPanelTitle(file)
      val baseTitle = mergeDialogCustomizer.getCenterPanelTitle(file)
      val rightTitle = mergeDialogCustomizer.getRightPanelTitle(file, mergeData.LAST_REVISION_NUMBER)
      val title = mergeDialogCustomizer.getMergeWindowTitle(file)

      val byteContents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST)
      val contentTitles = listOf(leftTitle, baseTitle, rightTitle)

      val callback = { result: MergeResult ->
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        if (document != null) FileDocumentManager.getInstance().saveDocument(document)
        checkMarkModifiedProject(file)

        if (result != MergeResult.CANCEL) {
          markFileProcessed(file, getSessionResolution(result))
        }
      }

      val request: MergeRequest
      try {
        if (mergeProvider.isBinary(file)) { // respect MIME-types in svn
          request = requestFactory.createBinaryMergeRequest(project, file, byteContents, title, contentTitles, callback)
        }
        else {
          request = requestFactory.createMergeRequest(project, file, byteContents, title, contentTitles, callback)
        }

        MergeUtil.putRevisionInfos(request, mergeData)
      }
      catch (e: InvalidDiffRequestException) {
        LOG.error(e)
        Messages.showErrorDialog(contentPanel, "Can't show merge dialog")
        break
      }

      DiffManager.getInstance().showMerge(project, request)
    }
    updateModelFromFiles()
    IdeFocusManager.getInstance(project).requestFocus(table, false)
  }

  private fun getSessionResolution(result: MergeResult): MergeSession.Resolution = when (result) {
    MergeResult.LEFT -> MergeSession.Resolution.AcceptedYours
    MergeResult.RIGHT -> MergeSession.Resolution.AcceptedTheirs
    MergeResult.RESOLVED -> MergeSession.Resolution.Merged
    else -> throw IllegalArgumentException(result.name)
  }

  private fun checkMarkModifiedProject(file: VirtualFile) {
    MergeVersion.MergeDocumentVersion.reportProjectFileChangeIfNeeded(project, file)
  }

  override fun getPreferredFocusedComponent(): JComponent? = table


  companion object {
    private val LOG = Logger.getInstance(MultipleFileMergeDialog2::class.java)
  }

}