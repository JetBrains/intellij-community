// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesTree.DEFAULT_GROUPING_KEYS
import com.intellij.openapi.vcs.merge.MergeConflictsTreeTable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.Alarm
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import git4idea.GitUtil
import git4idea.merge.GitMergeUtil
import git4idea.repo.GitConflict
import git4idea.repo.GitConflict.ConflictSide
import git4idea.repo.GitConflictsHolder
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

class GitConflictsView(private val project: Project) : Disposable {
  private val mergeHandler: GitMergeHandler = GitMergeHandler(project)

  private val panel: SimpleToolWindowPanel
  private val table: MergeConflictsTreeTable
  private val tableModel: ListTreeTableModelOnColumns

  private val conflicts: MutableList<GitConflict> = ArrayList()
  private val reversedRoots: MutableSet<VirtualFile> = HashSet()

  private val updateQueue: MergingUpdateQueue
  private val groupingSupport: ChangesGroupingSupport = ChangesGroupingSupport(project, this, false)

  init {
    tableModel = ListTreeTableModelOnColumns(DefaultMutableTreeNode(), arrayOf(PathColumn(), YoursColumn(), TheirsColumn()))
    table = MergeConflictsTreeTable(tableModel)
    updateQueue = MergingUpdateQueue("GitConflictsView", 300, true, table, this, null, Alarm.ThreadToUse.POOLED_THREAD)

    val renderer = ChangesBrowserNodeRenderer(project, { !groupingSupport.isDirectory }, false)
    renderer.font = UIUtil.getListFont()
    table.setTreeCellRenderer(renderer)
    table.rowHeight = renderer.preferredSize.height

    groupingSupport.addPropertyChangeListener(PropertyChangeListener { rebuildTree() })
    groupingSupport.setGroupingKeysOrSkip(DEFAULT_GROUPING_KEYS.toSet())

    TableSpeedSearch(table, Convertor { (it as? GitConflict)?.filePath?.name })


    val actionManager = ActionManager.getInstance()
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.addAction(actionManager.getAction("ChangesView.Refresh"))
    toolbarGroup.addAction(ResolveAction())
    toolbarGroup.addAction(AcceptSideAction(false))
    toolbarGroup.addAction(AcceptSideAction(true))
    toolbarGroup.addAction(actionManager.getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    val toolbar = actionManager.createActionToolbar("GitConflictsView", toolbarGroup, false)
    toolbar.setTargetComponent(table)

    val mainPanel = MainPanel()
    mainPanel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)

    panel = SimpleToolWindowPanel(false, true)
    panel.toolbar = toolbar.component
    panel.setContent(mainPanel)


    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        showMergeWindowForSelection()
        return true
      }
    }.installOn(table)

    val connection = project.messageBus.connect(this)
    connection.subscribe(GitConflictsHolder.CONFLICTS_CHANGE, GitConflictsHolder.ConflictsListener { updateConflicts() })
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { updateConflicts() })

    updateConflicts()
  }

  val component: JComponent? get() = panel
  val preferredFocusableComponent: JComponent? get() = table

  override fun dispose() {
  }

  private fun getSelectedConflicts(): List<GitConflict> {
    return TreeUtil.collectSelectedObjectsOfType(table.tree, GitConflict::class.java)
  }

  private fun updateConflicts() {
    updateQueue.queue(Update.create("update") {
      val newConflicts = ArrayList<GitConflict>()
      val newReversedRoots = ArrayList<VirtualFile>()

      val repos = GitUtil.getRepositories(project)
      for (repo in repos) {
        if (GitMergeUtil.isReverseRoot(repo)) newReversedRoots.add(repo.root)
        newConflicts.addAll(repo.conflictsHolder.conflicts)
      }

      runInEdt {
        conflicts.clear()
        conflicts.addAll(newConflicts)

        reversedRoots.clear()
        reversedRoots.addAll(newReversedRoots)

        rebuildTree()
      }
    })
  }

  private fun rebuildTree() {
    val builder = MyTreeModelBuilder(project, groupingSupport.grouping)
    builder.addConflicts(conflicts)
    val newRoot = builder.build().root

    tableModel.setRoot(newRoot as TreeNode)
    TreeUtil.expandAll(table.tree)
  }

  private fun showMergeWindowForSelection() {
    val conflicts = getSelectedConflicts().filter { mergeHandler.canResolveConflict(it) }.toList()
    if (conflicts.isEmpty()) return

    val reversed = HashSet(reversedRoots)

    for (conflict in conflicts) {
      val file = conflict.filePath.virtualFile
      MergeConflictResolveUtil.showMergeWindow(project, file) {
        mergeHandler.resolveConflict(conflict, reversed.contains(conflict.root))
      }
    }
  }

  private fun acceptConflictSideForSelection(takeTheirs: Boolean) {
    val conflicts = getSelectedConflicts()
    if (conflicts.isEmpty()) return

    val reversed = HashSet(reversedRoots)

    runBackgroundableTask(StringUtil.pluralize("Resolving Conflict", conflicts.size), project, true) {
      mergeHandler.acceptOneVersion(conflicts, reversed, takeTheirs)
    }
  }


  private class PathColumn : ColumnInfo<ChangesBrowserNode<*>, Any?>("Path") {
    override fun valueOf(node: ChangesBrowserNode<*>): Any? = node.userObject
    override fun getColumnClass(): Class<*> = TreeTableModel::class.java
  }

  private inner class YoursColumn : BaseColumn("Yours") {
    override fun valueOf(o: ChangesBrowserNode<*>): String? {
      val c = o.conflict ?: return null
      val status = c.getStatus(ConflictSide.OURS, reversedRoots.contains(c.root))
      return status.name
    }
  }

  private inner class TheirsColumn : BaseColumn("Theirs") {
    override fun valueOf(o: ChangesBrowserNode<*>): String? {
      val c = o.conflict ?: return null
      val status = c.getStatus(ConflictSide.THEIRS, reversedRoots.contains(c.root))
      return status.name
    }
  }

  private abstract class BaseColumn(name: String) : ColumnInfo<ChangesBrowserNode<*>, String>(name) {
    protected val ChangesBrowserNode<*>.conflict: GitConflict?
      get() = when (this) {
        is ConflictChangesBrowserNode -> this.userObject
        else -> null
      }
  }

  private inner class ResolveAction : DumbAwareAction("Resolve", null, AllIcons.Vcs.Merge) {
    override fun actionPerformed(e: AnActionEvent) {
      showMergeWindowForSelection()
    }
  }

  private inner class AcceptSideAction(val takeTheirs: Boolean) :
    DumbAwareAction(if (takeTheirs) "Accept Theirs" else "Accept Yours", null,
                    if (takeTheirs) AllIcons.Vcs.Arrow_left else AllIcons.Vcs.Arrow_right) {
    override fun actionPerformed(e: AnActionEvent) {
      acceptConflictSideForSelection(takeTheirs)
    }
  }

  private inner class MainPanel : JPanel(BorderLayout()), DataProvider {
    override fun getData(dataId: String): Any? {
      if (ChangesGroupingSupport.KEY.`is`(dataId)) {
        return groupingSupport
      }
      else if (CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId)) {
        return ChangesUtil.getNavigatableArray(project, getSelectedConflicts().mapNotNull { it.filePath.virtualFile }.stream())
      }
      return null
    }
  }
}

private class MyTreeModelBuilder(project: Project, grouping: ChangesGroupingPolicyFactory) : TreeModelBuilder(project, grouping) {
  fun addConflicts(conflicts: List<GitConflict>) {
    for (conflict in conflicts) {
      insertChangeNode(conflict.filePath, myRoot, ConflictChangesBrowserNode(conflict))
    }
  }
}

private class ConflictChangesBrowserNode(conflict: GitConflict) : ChangesBrowserNode<GitConflict>(conflict) {
  override fun isFile(): Boolean = true
  override fun isDirectory(): Boolean = false

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val filePath = getUserObject().filePath
    renderer.appendFileName(filePath.virtualFile, filePath.name, FileStatus.MERGED_WITH_CONFLICTS.color)

    if (renderer.isShowFlatten) {
      appendParentPath(renderer, filePath.parentPath)
    }

    if (!renderer.isShowFlatten && fileCount != 1 || directoryCount != 0) {
      appendCount(renderer)
    }

    renderer.setIcon(filePath.fileType, filePath.isDirectory || !isLeaf)
  }

  override fun getTextPresentation(): String {
    return getUserObject().filePath.name
  }

  override fun toString(): String {
    return FileUtil.toSystemDependentName(getUserObject().filePath.path)
  }

  override fun compareUserObjects(o2: GitConflict): Int {
    return compareFilePaths(getUserObject().filePath, o2.filePath)
  }
}
