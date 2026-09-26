// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.LocallyDeletedChange
import com.intellij.openapi.vcs.changes.LogicalLock
import com.intellij.openapi.vcs.changes.RemoteRevisionsCache
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.changes.TreeModelBuilderEx
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.vcsUtil.VcsUtil

internal class BackendChangesViewModelBuilderService(private val project: Project) : TreeModelBuilderEx {
  private val revisionsCache = RemoteRevisionsCache.getInstance(project)

  override fun getChangeNodeInChangelistBaseDecorator(
    listRemoteState: ChangeListRemoteState,
    change: Change,
    index: Int,
  ): ChangeNodeDecorator =
    RemoteStatusChangeNodeDecorator(revisionsCache, listRemoteState, index)

  override fun modifyTreeModelBuilder(modelBuilder: TreeModelBuilder) {
    val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
    val shouldShowUntrackedLoading = changeListManager.unversionedFilesPaths.isEmpty() &&
                                     !project.getService(InitialVfsRefreshService::class.java).isInitialVfsRefreshFinished() &&
                                     changeListManager.isUnversionedInUpdateMode
    if (shouldShowUntrackedLoading) {
      modelBuilder.insertSubtreeRoot(ChangesBrowserUnversionedLoadingPendingNode())
    }

    modelBuilder.setLocallyDeletedPaths(changeListManager.deletedFiles)
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
}
