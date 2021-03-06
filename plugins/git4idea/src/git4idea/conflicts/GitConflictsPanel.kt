// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.DIRECTORY_GROUPING
import com.intellij.openapi.vcs.impl.BackgroundableActionLock
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Alarm
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EventDispatcher
import com.intellij.util.FontUtil.spaceAndThinSpace
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import git4idea.GitNotificationIdsHolder.Companion.CANNOT_RESOLVE_CONFLICT
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.merge.GitMergeUtil
import git4idea.repo.GitConflict
import git4idea.repo.GitConflict.ConflictSide
import git4idea.repo.GitConflict.Status
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.status.GitStagingAreaHolder
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultTreeModel
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class GitConflictsPanel(
  private val project: Project,
  private val mergeHandler: GitMergeHandler
) : Disposable {

  private val panel: JComponent
  private val conflictsTree: MyChangesTree

  private val conflicts: MutableList<GitConflict> = ArrayList()
  private val reversedRoots: MutableSet<VirtualFile> = HashSet()

  private val updateQueue: MergingUpdateQueue

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  init {
    conflictsTree = MyChangesTree(project)
    conflictsTree.setKeepTreeState(true)

    updateQueue = MergingUpdateQueue("GitConflictsView", 300, true, conflictsTree, this, null, Alarm.ThreadToUse.POOLED_THREAD)

    panel = MainPanel().apply {
      add(ScrollPaneFactory.createScrollPane(conflictsTree), BorderLayout.CENTER)
    }

    conflictsTree.setDoubleClickHandler { e ->
      when {
        EditSourceOnDoubleClickHandler.isToggleEvent(conflictsTree, e) -> false
        else -> {
          showMergeWindowForSelection()
          true
        }
      }
    }
    conflictsTree.setEnterKeyHandler {
      showMergeWindowForSelection()
      true
    }

    val connection = project.messageBus.connect(this)
    connection.subscribe(GitStagingAreaHolder.TOPIC,
                         GitStagingAreaHolder.StagingAreaListener { updateConflicts() })
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { updateConflicts() })

    updateConflicts()
    updateQueue.sendFlush()
  }

  val component: JComponent get() = panel
  val preferredFocusableComponent: JComponent get() = conflictsTree

  override fun dispose() {
  }

  fun addListener(listener: Listener) {
    eventDispatcher.addListener(listener)
  }

  private fun getSelectedConflicts(): List<GitConflict> {
    return conflictsTree.selectedChanges
  }

  private fun updateConflicts() {
    updateQueue.queue(DisposableUpdate.createDisposable(this, "update", Runnable {
      val description = mergeHandler.loadMergeDescription()

      val newConflicts = ArrayList<GitConflict>()
      val newReversedRoots = ArrayList<VirtualFile>()

      val repos = GitUtil.getRepositories(project)
      for (repo in repos) {
        if (GitMergeUtil.isReverseRoot(repo)) newReversedRoots.add(repo.root)
        newConflicts.addAll(repo.stagingAreaHolder.allConflicts)
      }

      runInEdt {
        eventDispatcher.multicaster.onDescriptionChange(description)

        conflicts.clear()
        conflicts.addAll(newConflicts)

        reversedRoots.clear()
        reversedRoots.addAll(newReversedRoots)

        conflictsTree.setChangesToDisplay(conflicts)

        if (conflictsTree.selectionCount == 0) {
          TreeUtil.promiseSelectFirstLeaf(conflictsTree)
        }
      }
    }))
  }

  fun canShowMergeWindowForSelection(): Boolean {
    val selectedConflicts = getSelectedConflicts()
    return selectedConflicts.any { mergeHandler.canResolveConflict(it) && !getConflictOperationLock(it).isLocked }
  }

  fun showMergeWindowForSelection() {
    val reversed = HashSet(reversedRoots)
    showMergeWindow(project, mergeHandler, getSelectedConflicts(), reversed::contains)
  }

  fun canAcceptConflictSideForSelection(): Boolean {
    val selectedConflicts = getSelectedConflicts()
    return selectedConflicts.any { !getConflictOperationLock(it).isLocked }
  }

  fun acceptConflictSideForSelection(takeTheirs: Boolean) {
    val reversed = HashSet(reversedRoots)
    acceptConflictSide(project, mergeHandler, getSelectedConflicts(), takeTheirs, reversed::contains)
  }

  private fun getConflictOperationLock(conflict: GitConflict) = getConflictOperationLock(project, conflict)


  private inner class MainPanel : JPanel(BorderLayout()), DataProvider {
    override fun getData(dataId: String): Any? {
      if (CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId)) {
        return ChangesUtil.getNavigatableArray(project, getSelectedConflicts().mapNotNull { it.filePath.virtualFile }.stream())
      }
      return null
    }
  }

  interface Listener : EventListener {
    fun onDescriptionChange(description: @Nls String) {}
  }
}

internal fun getConflictOperationLock(project: Project, conflict: GitConflict): BackgroundableActionLock {
  return BackgroundableActionLock.getLock(project, conflict.filePath)
}

internal fun acceptConflictSide(project: Project, handler: GitMergeHandler, selectedConflicts: List<GitConflict>, takeTheirs: Boolean,
                                isReversed: (VirtualFile) -> Boolean) {
  val conflicts = selectedConflicts.filterNot { getConflictOperationLock(project, it).isLocked }.toList()
  if (conflicts.isEmpty()) return

  val locks = conflicts.map { getConflictOperationLock(project, it) }
  locks.forEach { it.lock() }

  object : Task.Backgroundable(project, GitBundle.message("conflicts.accept.progress", conflicts.size), true) {
    override fun run(indicator: ProgressIndicator) {
      val reversedRoots = conflicts.mapTo(mutableSetOf()) { it.root }.filter(isReversed)
      handler.acceptOneVersion(conflicts, reversedRoots, takeTheirs)
    }

    override fun onFinished() {
      locks.forEach { it.unlock() }
    }
  }.queue()
}

internal fun showMergeWindow(project: Project, handler: GitMergeHandler, selectedConflicts: List<GitConflict>, isReversed: (VirtualFile) -> Boolean) {
  val conflicts = selectedConflicts.filter { handler.canResolveConflict(it) && !getConflictOperationLock(project, it).isLocked }.toList()
  if (conflicts.isEmpty()) return

  for (conflict in conflicts) {
    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(conflict.filePath.path)
    if (file == null) {
      VcsNotifier.getInstance(project).notifyError(CANNOT_RESOLVE_CONFLICT,
                                                   GitBundle.message("conflicts.merge.window.error.title"),
                                                   GitBundle.message("conflicts.merge.window.error.message", conflict.filePath))
      continue
    }

    val lock = getConflictOperationLock(project, conflict)
    MergeConflictResolveUtil.showMergeWindow(project, file, lock) {
      handler.resolveConflict(conflict, file, isReversed(conflict.root))
    }
  }
}

@Nls
internal fun getConflictType(conflict: GitConflict): String {
  val oursStatus = conflict.getStatus(ConflictSide.OURS, true)
  val theirsStatus = conflict.getStatus(ConflictSide.THEIRS, true)
  return when {
    oursStatus == Status.DELETED && theirsStatus == Status.DELETED -> GitBundle.message("conflicts.type.both.deleted")
    oursStatus == Status.ADDED && theirsStatus == Status.ADDED -> GitBundle.message("conflicts.type.both.added")
    oursStatus == Status.MODIFIED && theirsStatus == Status.MODIFIED -> GitBundle.message("conflicts.type.both.modified")
    oursStatus == Status.DELETED -> GitBundle.message("conflicts.type.deleted.by.you")
    theirsStatus == Status.DELETED -> GitBundle.message("conflicts.type.deleted.by.them")
    oursStatus == Status.ADDED -> GitBundle.message("conflicts.type.added.by.you")
    theirsStatus == Status.ADDED -> GitBundle.message("conflicts.type.added.by.them")
    else -> throw IllegalStateException("ours: $oursStatus; theirs: $theirsStatus")
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

    val conflictType = getConflictType(conflict)
    renderer.append(spaceAndThinSpace() + conflictType, SimpleTextAttributes.GRAYED_ATTRIBUTES)

    renderer.setIcon(filePath, filePath.isDirectory || !isLeaf)
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
    installGroupingSupport(this, groupingSupport, GROUPING_KEYS_PROPERTY, DIRECTORY_GROUPING)
    return groupingSupport
  }

  override fun getToggleClickCount(): Int = 2
}