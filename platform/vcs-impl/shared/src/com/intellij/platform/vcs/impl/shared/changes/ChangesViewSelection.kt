// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.JBIterable
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

/**
 * Note that only changes from change lists can be currently restored
 */
@ApiStatus.Internal
@Serializable
data class ChangesViewSelection(
  val selectedChangeListsIds: Set<String>,
  val selectedPaths: List<PathSelection>,
  /**
   * Set to not-null value if [javax.swing.JTree.getLeadSelectionPath] is the file path
   */
  val leadSelection: FilePathDto?,
) {
  @ApiStatus.Internal
  @Serializable
  data class PathSelection(
    val filePath: FilePathDto,
    val changeId: ChangeId?,
    val exactlySelected: Boolean,
  )

  companion object {
    @JvmStatic
    @RequiresEdt
    fun create(nodesIterator: JBIterable<ChangesBrowserNode<*>>, tree: ChangesTree): ChangesViewSelection {
      val selectedChangeListsIds = mutableSetOf<String>()
      val selectedPaths = mutableListOf<PathSelection>()

      nodesIterator.forEach { node ->
        val userObject = node.userObject
        if (userObject is LocalChangeList) {
          selectedChangeListsIds.add(userObject.id)
        }
        else if (node.isMeaningfulNode) {
          val filePath = VcsTreeModelData.mapUserObjectToFilePath(userObject)
          if (filePath != null) {
            val exactlySelected = tree.isPathSelected(TreePath(node.path))
            selectedPaths.add(PathSelection(
              filePath = convertToDto(filePath),
              changeId = (userObject as? Change)?.let { ChangeId.getId(it) },
              exactlySelected = exactlySelected,
            ))
          }
        }
      }

      return ChangesViewSelection(
        selectedChangeListsIds = selectedChangeListsIds,
        selectedPaths = selectedPaths,
        leadSelection = getLeadSelectionFilePath(tree)?.let { convertToDto(it) },
      )
    }

    private fun getLeadSelectionFilePath(tree: ChangesTree): FilePath? =
      (tree.leadSelectionPath?.lastPathComponent as? ChangesBrowserNode<*>)?.userObject.let {
        VcsTreeModelData.mapUserObjectToFilePath(it)
      }

    /**
     * [ChangesViewSelection] is used to transfer data context from the frontend to the backend, so there is no need to
     * set [FilePathDto.virtualFileId], as it will be restored anyway.
     */
    private fun convertToDto(path: FilePath): FilePathDto = FilePathDto(null, path.path, path.isDirectory)
  }
}