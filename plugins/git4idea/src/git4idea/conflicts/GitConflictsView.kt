// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.DIRECTORY_GROUPING
import com.intellij.openapi.vcs.impl.BackgroundableActionLock
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Alarm
import com.intellij.util.FontUtil.spaceAndThinSpace
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import git4idea.GitUtil
import git4idea.merge.GitMergeUtil
import git4idea.repo.GitConflict
import git4idea.repo.GitConflict.ConflictSide
import git4idea.repo.GitConflict.Status
import git4idea.repo.GitConflictsHolder
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultTreeModel

class GitConflictsView(private val project: Project) : Disposable {
  private val mergeHandler: GitMergeHandler = GitMergeHandler(project)

  private val panel: SimpleToolWindowPanel
  private val conflictsTree: MyChangesTree

  private val conflicts: MutableList<GitConflict> = ArrayList()
  private val reversedRoots: MutableSet<VirtualFile> = HashSet()

  private val updateQueue: MergingUpdateQueue

  init {
    conflictsTree = MyChangesTree(project)
    updateQueue = MergingUpdateQueue("GitConflictsView", 300, true, conflictsTree, this, null, Alarm.ThreadToUse.POOLED_THREAD)

    val actionManager = ActionManager.getInstance()
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.addAction(ResolveAction())
    toolbarGroup.addAction(AcceptSideAction(false))
    toolbarGroup.addAction(AcceptSideAction(true))
    toolbarGroup.addAction(actionManager.getAction("ChangesView.Refresh"))
    toolbarGroup.addAction(Separator.getInstance())
    toolbarGroup.addAction(actionManager.getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    val toolbar = actionManager.createActionToolbar("GitConflictsView", toolbarGroup, false)
    toolbar.setTargetComponent(conflictsTree)

    val mainPanel = MainPanel()
    mainPanel.add(ScrollPaneFactory.createScrollPane(conflictsTree), BorderLayout.CENTER)

    panel = SimpleToolWindowPanel(true, true)
    panel.toolbar = toolbar.component
    panel.setContent(mainPanel)


    conflictsTree.setDoubleClickHandler { showMergeWindowForSelection() }

    val connection = project.messageBus.connect(this)
    connection.subscribe(GitConflictsHolder.CONFLICTS_CHANGE, GitConflictsHolder.ConflictsListener { updateConflicts() })
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { updateConflicts() })

    updateConflicts()
  }

  val component: JComponent? get() = panel
  val preferredFocusableComponent: JComponent? get() = conflictsTree

  override fun dispose() {
  }

  private fun getSelectedConflicts(): List<GitConflict> {
    return conflictsTree.selectedChanges
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
    conflictsTree.setChangesToDisplay(conflicts)
  }

  private fun showMergeWindowForSelection() {
    val conflicts = getSelectedConflicts().filter { mergeHandler.canResolveConflict(it) }.toList()
    if (conflicts.isEmpty()) return

    val reversed = HashSet(reversedRoots)

    for (conflict in conflicts) {
      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(conflict.filePath.path)
      if (file == null) {
        VcsNotifier.getInstance(project).notifyError("Can't Resolve Conflict", "Can't find file for ${conflict.filePath}")
        continue
      }

      val lock = getConflictOperationLock(conflict)
      MergeConflictResolveUtil.showMergeWindow(project, file, lock) {
        mergeHandler.resolveConflict(conflict, file, reversed.contains(conflict.root))
      }
    }
  }

  private fun acceptConflictSideForSelection(takeTheirs: Boolean) {
    val conflicts = getSelectedConflicts()
    if (conflicts.isEmpty()) return

    val reversed = HashSet(reversedRoots)

    val locks = conflicts.map { getConflictOperationLock(it) }
    if (locks.any { it.isLocked }) return
    locks.forEach { it.lock() }

    object : Task.Backgroundable(project, StringUtil.pluralize("Resolving Conflict", conflicts.size), true) {
      override fun run(indicator: ProgressIndicator) {
        mergeHandler.acceptOneVersion(conflicts, reversed, takeTheirs)
      }

      override fun onFinished() {
        locks.forEach { it.unlock() }
      }
    }.queue()
  }

  private fun getConflictOperationLock(conflict: GitConflict): BackgroundableActionLock {
    return BackgroundableActionLock.getLock(project, conflict.filePath)
  }


  private inner class ResolveAction
    : ButtonAction("Resolve") {

    override fun update(e: AnActionEvent) {
      val selectedConflicts = getSelectedConflicts()
      e.presentation.isEnabled = selectedConflicts.any { mergeHandler.canResolveConflict(it) } &&
                                 selectedConflicts.none { getConflictOperationLock(it).isLocked }
      updateButtonPresentation(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
      showMergeWindowForSelection()
    }
  }

  private inner class AcceptSideAction(val takeTheirs: Boolean)
    : ButtonAction(if (takeTheirs) "Accept Theirs" else "Accept Yours") {

    override fun update(e: AnActionEvent) {
      val selectedConflicts = getSelectedConflicts()
      e.presentation.isEnabled = selectedConflicts.isNotEmpty() &&
                                 selectedConflicts.none { getConflictOperationLock(it).isLocked }
      updateButtonPresentation(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
      acceptConflictSideForSelection(takeTheirs)
    }
  }

  private abstract class ButtonAction(text: String) : DumbAwareAction(text), CustomComponentAction {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      val button = JButton(presentation.text)
      button.isFocusable = false
      button.addActionListener {
        val toolbar = ComponentUtil.getParentOfType(ActionToolbar::class.java, button)
        val dataContext = toolbar?.toolbarDataContext ?: DataManager.getInstance().getDataContext(button)
        actionPerformed(AnActionEvent.createFromAnAction(this@ButtonAction, null, place, dataContext))
      }

      updateButtonPresentation(button, presentation)

      return JBUI.Panels.simplePanel(button)
        .withBorder(JBUI.Borders.empty(4, if (SystemInfo.isWindows) 4 else 6))
    }

    fun updateButtonPresentation(e: AnActionEvent) {
      val button = UIUtil.findComponentOfType(e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY), JButton::class.java)
      if (button != null) updateButtonPresentation(button, e.presentation)
    }

    fun updateButtonPresentation(button: JButton, presentation: Presentation) {
      button.isEnabled = presentation.isEnabled
      button.isVisible = presentation.isVisible
    }
  }

  private inner class MainPanel : JPanel(BorderLayout()), DataProvider {
    override fun getData(dataId: String): Any? {
      if (CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId)) {
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
    val conflict = getUserObject()
    val filePath = conflict.filePath
    renderer.appendFileName(filePath.virtualFile, filePath.name, FileStatus.MERGED_WITH_CONFLICTS.color)

    if (renderer.isShowFlatten) {
      appendParentPath(renderer, filePath.parentPath)
    }

    if (!renderer.isShowFlatten && fileCount != 1 || directoryCount != 0) {
      appendCount(renderer)
    }

    val oursStatus = conflict.getStatus(ConflictSide.OURS, true)
    val theirsStatus = conflict.getStatus(ConflictSide.THEIRS, true)
    val conflictType = when {
      oursStatus == Status.DELETED && theirsStatus == Status.DELETED -> "both deleted"
      oursStatus == Status.ADDED && theirsStatus == Status.ADDED -> "both added"
      oursStatus == Status.MODIFIED && theirsStatus == Status.MODIFIED -> "both modified"
      oursStatus == Status.DELETED -> "deleted by you"
      theirsStatus == Status.DELETED -> "deleted by them"
      oursStatus == Status.ADDED -> "added by you"
      theirsStatus == Status.ADDED -> "added by them"
      else -> throw IllegalStateException("ours: $oursStatus; theirs: $theirsStatus")
    }
    renderer.append(spaceAndThinSpace() + conflictType, SimpleTextAttributes.GRAYED_ATTRIBUTES)

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

private class MyChangesTree(project: Project)
  : ChangesTreeImpl<GitConflict>(project, false, true, GitConflict::class.java) {

  companion object {
    private const val GROUPING_KEYS_PROPERTY = "GitConflictsView.GroupingKeys"
  }

  override fun buildTreeModel(conflicts: List<GitConflict>): DefaultTreeModel {
    val builder = MyTreeModelBuilder(project, grouping)
    builder.addConflicts(conflicts)
    return builder.build()
  }

  override fun installGroupingSupport(): ChangesGroupingSupport {
    val groupingSupport = ChangesGroupingSupport(myProject, this, false)

    val propertiesComponent = PropertiesComponent.getInstance(project)
    groupingSupport.setGroupingKeysOrSkip(propertiesComponent.getValues(GROUPING_KEYS_PROPERTY)?.toSet() ?: setOf(DIRECTORY_GROUPING))
    groupingSupport.addPropertyChangeListener(PropertyChangeListener {
      propertiesComponent.setValues(GROUPING_KEYS_PROPERTY, groupingSupport.groupingKeys.toTypedArray())

      val oldSelection = VcsTreeModelData.selected(this).userObjects()
      rebuildTree()
      setSelectedChanges(oldSelection)
    })
    return groupingSupport
  }
}
