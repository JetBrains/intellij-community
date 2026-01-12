// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.platform.vcs.changes.ChangesUtil
import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffableSelection
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private val LOG = logger<ChangesViewDiffableSelectionHelper>()

// TODO support hijacked changes
internal class ChangesViewDiffableSelectionHelper(private val changesView: ChangesListView) {
  private val _diffableSelection = MutableStateFlow<ChangesViewDiffableSelection?>(null)
  val diffableSelection = _diffableSelection.asStateFlow()

  @RequiresEdt
  fun tryUpdateSelection() {
    _diffableSelection.update { currentValue ->
      if (shouldUpdateSelection(currentValue)) getDiffableSelection() else currentValue
    }
  }

  @RequiresEdt
  private fun shouldUpdateSelection(previousSelection: ChangesViewDiffableSelection?): Boolean {
    if (previousSelection == null) return true

    val currentValueIsSelected = changesView.selectedChangesNodes.any { node ->
      when (val obj = node.userObject) {
        is Change -> ChangesUtil.matches(obj, previousSelection.selectedChange.filePath.filePath) &&
                     ChangeId.getId(obj) == previousSelection.selectedChange.changeId
        is FilePath -> obj == previousSelection.selectedChange.filePath.filePath
        else -> false
      }
    }

    return !currentValueIsSelected
  }

  @RequiresEdt
  private fun getDiffableSelection(): ChangesViewDiffableSelection? {
    val selectedDiffableNode = changesView.selectedDiffableNode ?: return null
    val selectedNodePath =
      getPathOrLog(selectedDiffableNode) { LOG.warn("Could not create path for selected node: $it") } ?: return null


    val (prevNode, nextNode) = findPreviousAndNextDiffableNodes(selectedDiffableNode)

    return ChangesViewDiffableSelection(
      selectedChange = selectedNodePath,
      previousChange = getPathOrLog(prevNode) { LOG.warn("Could not create path for previous node: $it") },
      nextChange = getPathOrLog(nextNode) { LOG.warn("Could not create path for next node: $it") })
  }

  private fun getPathOrLog(prevNode: Any?, log: (Any) -> Unit): ChangesTreePath? = prevNode?.let { node ->
    ChangesTreePath.create(node).also { path -> if (path == null) log(node) }
  }

  @RequiresEdt
  private fun findPreviousAndNextDiffableNodes(selectedDiffableNode: Any): Pair<Any?, Any?> {
    var previousNode: Any? = null
    var nextNode: Any? = null

    var selectedNodeWasFound = false
    for (node in VcsTreeModelData.all(changesView).iterateNodes()) {
      if (nextNode != null) break

      val userObject = node.userObject
      if (userObject === selectedDiffableNode) {
        selectedNodeWasFound = true
      }
      else {
        if (isDiffableNode(node)) {
          if (selectedNodeWasFound) {
            nextNode = userObject
          }
          else {
            previousNode = userObject
          }
        }
      }
    }

    return previousNode to nextNode
  }

  private fun isDiffableNode(node: ChangesBrowserNode<*>): Boolean = when (node.userObject) {
    is Change -> true
    is FilePath -> node.isUnderTag(ChangesBrowserNode.UNVERSIONED_FILES_TAG)
    else -> false
  }
}