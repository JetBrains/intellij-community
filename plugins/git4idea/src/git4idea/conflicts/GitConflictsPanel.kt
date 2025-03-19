// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.conflicts

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.DIRECTORY_GROUPING
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
import git4idea.GitUtil
import git4idea.conflicts.GitConflictsUtil.acceptConflictSide
import git4idea.conflicts.GitConflictsUtil.getConflictOperationLock
import git4idea.conflicts.GitConflictsUtil.getConflictType
import git4idea.conflicts.GitConflictsUtil.showMergeWindow
import git4idea.merge.GitMergeUtil
import git4idea.repo.GitConflict
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.status.GitStagingAreaHolder
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultTreeModel

internal class GitConflictsPanel(
  private val project: Project,
  private val mergeHandler: GitMergeHandler
) : Disposable {
  private val panel: JComponent
  private val conflictsTree = MyChangesTree(project)

  private val conflicts: MutableList<GitConflict> = ArrayList()
  private val reversedRoots: MutableSet<VirtualFile> = HashSet()

  private val updateQueue: MergingUpdateQueue

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  init {
    conflictsTree.isKeepTreeState = true

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
        conflictsTree.invokeAfterRefresh {
          if (conflictsTree.selectionCount == 0) {
            TreeUtil.promiseSelectFirstLeaf(conflictsTree)
          }
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


  private inner class MainPanel : JPanel(BorderLayout()), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.NAVIGATABLE_ARRAY] = ChangesUtil.getNavigatableArray(
        project, getSelectedConflicts().mapNotNull { it.filePath.virtualFile })
    }
  }

  interface Listener : EventListener {
    fun onDescriptionChange(description: @Nls String) {}
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
  : AsyncChangesTreeImpl<GitConflict>(project, false, true, GitConflict::class.java) {

  companion object {
    private const val GROUPING_KEYS_PROPERTY = "GitConflictsView.GroupingKeys"
  }

  override fun buildTreeModel(grouping: ChangesGroupingPolicyFactory, conflicts: MutableList<out GitConflict>): DefaultTreeModel {
    val builder = MyTreeModelBuilder(project, grouping)
    builder.addConflicts(conflicts)
    return builder.build()
  }

  override fun installGroupingSupport(): ChangesGroupingSupport {
    val groupingSupport = ChangesGroupingSupport(myProject, this, false)
    installGroupingSupport(this, groupingSupport, GROUPING_KEYS_PROPERTY, listOf(DIRECTORY_GROUPING))
    return groupingSupport
  }

  override fun getToggleClickCount(): Int = 2
}