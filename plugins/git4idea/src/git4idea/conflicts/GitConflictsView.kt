// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesTree.DEFAULT_GROUPING_KEYS
import com.intellij.openapi.vcs.merge.MergeConflictsTreeTable
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.merge.GitMergeUtil
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

class GitConflictsView(private val project: Project) {
  private val mergeHandler: GitMergeHandler = GitMergeHandler(project)

  private val table: MergeConflictsTreeTable
  private val tableModel: ListTreeTableModelOnColumns
  private val conflicts: MutableList<GitConflict> = ArrayList()

  private val groupingSupport: ChangesGroupingSupport = ChangesGroupingSupport(project, this, false)
  private val panel: SimpleToolWindowPanel

  init {
    tableModel = ListTreeTableModelOnColumns(DefaultMutableTreeNode(), arrayOf(PathColumn(), YoursColumn(), TheirsColumn()))
    table = MergeConflictsTreeTable(tableModel)

    val renderer = ChangesBrowserNodeRenderer(project, { !groupingSupport.isDirectory }, false)
    renderer.font = UIUtil.getListFont()
    table.setTreeCellRenderer(renderer)
    table.rowHeight = renderer.preferredSize.height

    groupingSupport.addPropertyChangeListener(PropertyChangeListener { refreshView() })
    groupingSupport.setGroupingKeysOrSkip(DEFAULT_GROUPING_KEYS.toSet())

    TableSpeedSearch(table, Convertor { (it as? GitConflict)?.filePath?.name })


    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.addAction(RefreshAction())
    toolbarGroup.addAction(ResolveAction())
    toolbarGroup.addAction(AcceptSideAction(false))
    toolbarGroup.addAction(AcceptSideAction(true))
    toolbarGroup.addAction(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    val toolbar = ActionManager.getInstance().createActionToolbar("GitConflictsView", toolbarGroup, false)
    toolbar.setTargetComponent(table)

    val mainPanel = MainPanel()
    mainPanel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)

    panel = SimpleToolWindowPanel(false, true)
    panel.toolbar = toolbar.component
    panel.setContent(mainPanel)


    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        showMergeWindow()
        return true
      }
    }.installOn(table)

  }

  val component: JComponent? get() = panel
  val preferredFocusableComponent: JComponent? get() = table


  private fun getSelectedConflicts(): List<GitConflict> {
    return TreeUtil.collectSelectedObjectsOfType(table.tree, GitConflict::class.java)
  }

  private fun updateConflicts() {
    // FIXME: MUQ
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating Conflicts", true) {
      val result = ArrayList<GitConflict>()

      override fun run(indicator: ProgressIndicator) {
        val repos = GitUtil.getRepositories(project)
        for (repo in repos) {
          val reversed = GitMergeUtil.isReverseRoot(repo)
          result.addAll(GitMergeUtil.getConflicts(project, repo.root, reversed))
        }
      }

      override fun onSuccess() {
        conflicts.clear()
        conflicts.addAll(result)

        refreshView()
      }

      override fun onThrowable(error: Throwable) {
        LOG.warn(error)
        VcsNotifier.getInstance(project).notifyError("Can't Load Conflicts", error.message!!)
      }
    })
  }

  private fun refreshView() {
    val builder = MyTreeModelBuilder(project, groupingSupport.grouping)
    builder.addConflicts(conflicts)
    tableModel.setRoot(builder.build().root as TreeNode)
    TreeUtil.expandAll(table.tree)
  }

  private fun showMergeWindow() {
    val conflicts = getSelectedConflicts().filter { mergeHandler.canResolveConflict(it) }
    if (conflicts.isEmpty()) return

    val resolvers = VcsUtil.computeWithModalProgress(project, "Loading Conflict", true) {
      conflicts.map { mergeHandler.resolveConflict(it) }
    }

    for (resolver in resolvers) {
      MergeConflictResolveUtil.showMergeWindow(project, resolver, { updateConflicts() })
    }
  }

  private fun acceptSide(takeTheirs: Boolean) {
    val conflicts = getSelectedConflicts()
    if (conflicts.isEmpty()) return

    VcsUtil.runVcsProcessWithProgress({ mergeHandler.acceptOneVersion(conflicts, takeTheirs) }, "Loading Conflict", true, project)

    updateConflicts()
  }


  private class PathColumn : ColumnInfo<ChangesBrowserNode<*>, Any?>("Path") {
    override fun valueOf(node: ChangesBrowserNode<*>): Any? = node.userObject
    override fun getColumnClass(): Class<*> = TreeTableModel::class.java
  }

  private class YoursColumn : BaseColumn("Yours") {
    override fun valueOf(o: ChangesBrowserNode<*>): String? {
      val c = o.conflict ?: return null
      val currentStatus = if (!c.isReversed) c.getStatus(GitConflict.ConflictSide.OURS) else c.getStatus(GitConflict.ConflictSide.THEIRS)
      return currentStatus.name
    }
  }

  private class TheirsColumn : BaseColumn("Theirs") {
    override fun valueOf(o: ChangesBrowserNode<*>): String? {
      val c = o.conflict ?: return null
      val lastStatus = if (c.isReversed) c.getStatus(GitConflict.ConflictSide.OURS) else c.getStatus(GitConflict.ConflictSide.THEIRS)
      return lastStatus.name
    }
  }

  private abstract class BaseColumn(name: String) : ColumnInfo<ChangesBrowserNode<*>, String>(name) {
    protected val ChangesBrowserNode<*>.conflict: GitConflict?
      get() = when (this) {
        is ConflictChangesBrowserNode -> this.userObject
        else -> null
      }
  }

  private inner class RefreshAction : DumbAwareAction("Refresh", null, AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
      updateConflicts()
    }
  }

  private inner class ResolveAction : DumbAwareAction("Resolve", null, AllIcons.Vcs.Merge) {
    override fun actionPerformed(e: AnActionEvent) {
      showMergeWindow()
    }
  }

  private inner class AcceptSideAction(val takeTheirs: Boolean) :
    DumbAwareAction(if (takeTheirs) "Accept Theirs" else "Accept Yours", null,
                    if (takeTheirs) AllIcons.Vcs.Arrow_left else AllIcons.Vcs.Arrow_right) {
    override fun actionPerformed(e: AnActionEvent) {
      acceptSide(takeTheirs)
    }
  }

  private inner class MainPanel : JPanel(BorderLayout()), DataProvider {
    override fun getData(dataId: String): Any? {
      if (ChangesGroupingSupport.KEY.`is`(dataId)) {
        return groupingSupport
      }
      return null
    }
  }
  companion object {
    private val LOG = Logger.getInstance(GitConflictsView::class.java)
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
    return getUserObject().filePath.path.compareTo(o2.filePath.path, ignoreCase = true)
  }
}
