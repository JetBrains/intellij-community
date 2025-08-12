// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.merge.MergeConflictManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewModelBuilderService
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.vcsUtil.VcsUtil
import java.util.function.Function

internal class BackendChangesViewModelBuilderService(private val project: Project) : ChangesViewModelBuilderService {
  override fun TreeModelBuilder.setChangeList(
    changeLists: Collection<ChangeList>,
    skipSingleDefaultChangeList: Boolean,
    changeDecoratorProvider: Function<in ChangeNodeDecorator, out ChangeNodeDecorator>?,
  ) {
    val resolvedUnchangedFiles = MergeConflictManager.getInstance(project).getResolvedConflictPaths().toMutableList()
    val revisionsCache = RemoteRevisionsCache.getInstance(project)
    val skipChangeListNode = skipSingleDefaultChangeList && TreeModelBuilder.isSingleBlankChangeList(changeLists)
    var conflictsRoot: ChangesBrowserConflictsNode? = null

    for (list in changeLists) {
      val changes = list.getChanges().sortedWith(TreeModelBuilder.CHANGE_COMPARATOR)
      val listRemoteState = ChangeListRemoteState()

      val changesParent =
        if (skipChangeListNode) myRoot
        else ChangesBrowserChangeListNode(project, list, listRemoteState).also { listNode ->
          listNode.markAsHelperNode()
          insertSubtreeRoot(listNode)
        }

      changes.forEachIndexed { i, change ->
        val baseDecorator = RemoteStatusChangeNodeDecorator(revisionsCache, listRemoteState, i)
        val decorator = changeDecoratorProvider?.apply(baseDecorator) ?: baseDecorator
        val path = ChangesUtil.getFilePath(change)
        if (MergeConflictManager.getInstance(project).isResolvedConflict(path)) {
          resolvedUnchangedFiles.remove(path)
          insertChangeNode(change, changesParent, createChangeNode(change, decorator))
        }
        else if (MergeConflictManager.isMergeConflict(change.fileStatus)) {
          if (conflictsRoot == null) {
            conflictsRoot = ChangesBrowserConflictsNode(project)
            conflictsRoot.markAsHelperNode()
            myModel.insertNodeInto(conflictsRoot, myRoot, myModel.getChildCount(myRoot))
          }
          insertChangeNode(change, conflictsRoot, createChangeNode(change, decorator))
        }
        else {
          insertChangeNode(change, changesParent, createChangeNode(change, decorator))
        }
      }

      insertResolvedUnchangedNodes(list, resolvedUnchangedFiles, changesParent)
    }
  }

  override fun TreeModelBuilder.createNodes() {
    val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)

    setLocallyDeletedPaths(changeListManager.deletedFiles)
      .setModifiedWithoutEditing(changeListManager.modifiedWithoutEditing)
      .setSwitchedFiles(changeListManager.switchedFilesMap)
      .setSwitchedRoots(changeListManager.switchedRoots)
      .setLockedFolders(changeListManager.lockedFolders)
      .setLogicallyLockedFiles(changeListManager.logicallyLockedFolders)
  }

  fun TreeModelBuilder.setModifiedWithoutEditing(modifiedWithoutEditing: List<VirtualFile>): TreeModelBuilder {
    if (ContainerUtil.isEmpty(modifiedWithoutEditing)) return this
    val node = ModifiedWithoutEditingNode(project, modifiedWithoutEditing)
    return insertSpecificFileNodeToModel(modifiedWithoutEditing, node)
  }

  fun TreeModelBuilder.setSwitchedFiles(switchedFiles: MultiMap<String, VirtualFile>): TreeModelBuilder {
    if (switchedFiles.isEmpty()) return this

    val subtreeRoot = createTagNode(ChangesBrowserNode.SWITCHED_FILES_TAG)
    for (branchName in switchedFiles.keySet()) {
      val switchedFileList = switchedFiles.get(branchName).sortedWith(TreeModelBuilder.FILE_COMPARATOR)
      if (!switchedFileList.isEmpty()) {
        val branchNode = ChangesBrowserStringNode(branchName)
        branchNode.markAsHelperNode()
        insertSubtreeRoot(branchNode, subtreeRoot)
        for (file in switchedFileList) {
          insertChangeNode(file, branchNode, ChangesBrowserNode.createFile(project, file))
        }
      }
    }
    return this
  }

  fun TreeModelBuilder.setLocallyDeletedPaths(locallyDeletedChanges: Collection<LocallyDeletedChange>): TreeModelBuilder {
    if (locallyDeletedChanges.isEmpty()) return this

    val subtreeRoot = createTagNode(ChangesBrowserNode.LOCALLY_DELETED_NODE_TAG)
    for (change in locallyDeletedChanges.sortedWith(compareBy(TreeModelBuilder.PATH_COMPARATOR) { it.path })) {
      insertChangeNode(change.path, subtreeRoot, ChangesBrowserLocallyDeletedNode(change))
    }
    return this
  }

  fun TreeModelBuilder.setSwitchedRoots(switchedRoots: Map<VirtualFile, String>?): TreeModelBuilder {
    if (switchedRoots == null || switchedRoots.isEmpty()) return this

    val rootsHeadNode = createTagNode(ChangesBrowserNode.SWITCHED_ROOTS_TAG, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES, true)
    for ((vf, branchName) in switchedRoots.toSortedMap(TreeModelBuilder.FILE_COMPARATOR)) {
      val cr = CurrentContentRevision(VcsUtil.getFilePath(vf))
      val change = Change(cr, cr, FileStatus.NOT_CHANGED)
      insertChangeNode(vf, rootsHeadNode, createChangeNode(change, object : ChangeNodeDecorator {
        override fun decorate(change1: Change, component: SimpleColoredComponent, isShowFlatten: Boolean) {
        }

        override fun preDecorate(change1: Change, renderer: ChangesBrowserNodeRenderer, showFlatten: Boolean) {
          renderer.append("[$branchName] ", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
        }
      }))
    }
    return this
  }

  fun TreeModelBuilder.setLockedFolders(lockedFolders: List<VirtualFile>): TreeModelBuilder {
    if (lockedFolders.isEmpty()) return this
    insertFilesIntoNode(lockedFolders, ChangesBrowserLockedFoldersNode(project))
    return this
  }

  fun TreeModelBuilder.setLogicallyLockedFiles(logicallyLockedFiles: Map<VirtualFile, LogicalLock>): TreeModelBuilder {
    if (logicallyLockedFiles.isEmpty()) return this
    val subtreeRoot = createTagNode(ChangesBrowserNode.LOGICALLY_LOCKED_TAG)

    for ((file, lock) in logicallyLockedFiles.toSortedMap(TreeModelBuilder.FILE_COMPARATOR)) {
      insertChangeNode(file, subtreeRoot, ChangesBrowserLogicallyLockedFile(project, file, lock))
    }
    return this
  }

  private fun TreeModelBuilder.insertResolvedUnchangedNodes(
    list: ChangeList,
    resolvedUnchangedFilePaths: List<FilePath>,
    changesParent: ChangesBrowserNode<*>,
  ) {
    if (resolvedUnchangedFilePaths.isEmpty()) return

    if (list is LocalChangeList && list.isDefault()) {
      for (resolvedConflictPath in resolvedUnchangedFilePaths) {
        insertChangeNode(resolvedConflictPath, changesParent, ChangesBrowserNode.createFilePath(resolvedConflictPath, FileStatus.MERGE))
      }
    }
  }
}
