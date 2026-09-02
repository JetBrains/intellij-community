// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.vcs.changes.ui.isUnderTag
import com.intellij.openapi.vcs.changes.ui.selectedDiffableNode
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

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun tryUpdateSelection() {
    _diffableSelection.update { currentValue -> getDiffableSelection(currentValue?.selectedChange) }
  }

  /**
   * @param currentChange the change that is currently shown in the diff preview, if any. It is kept as the selected one
   * as long as it is still selected, so that selecting a parent node (e.g. a changelist) doesn't switch the previewed
   * file. Everything else depends on the whole selection and is always recalculated.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun getDiffableSelection(currentChange: ChangesTreePath?): ChangesViewDiffableSelection? {
    val selectedDiffableNode = findSelectedDiffableNode(currentChange) ?: return null
    val selectedNodePath =
      getPathOrLog(selectedDiffableNode) { LOG.warn("Could not create path for selected node: $it") } ?: return null

    // An explicit selection of 2+ diffable files scopes navigation to itself, as monolith mode does, so that
    // "Compare Previous/Next File" walks the selected files only. Moving inside it must not change the tree selection,
    // see [moveWithinExplicitSelection].
    val explicitSelection = selectedDiffableObjects().toList().takeIf { it.size > 1 }

    val neighbours = explicitSelection?.let { neighboursWithin(it, selectedDiffableNode) }
                     ?: findNeighboursInTree(selectedDiffableNode)

    return ChangesViewDiffableSelection(
      selectedChange = selectedNodePath,
      previousChange = getPathOrLog(neighbours.previous) { LOG.warn("Could not create path for previous node: $it") },
      nextChange = getPathOrLog(neighbours.next) { LOG.warn("Could not create path for next node: $it") })
  }

  /**
   * Moves the previewed change to [path] without touching the tree selection.
   *
   * Navigating inside an explicit selection of 2+ diffable files changes only the previewed file in monolith mode, so
   * the selection has to be preserved here too.
   * @return false when [path] does not belong to such a selection, so that the caller selects the corresponding node in the tree instead.
   */
  @RequiresEdt
  fun moveWithinExplicitSelection(path: ChangesTreePath): Boolean {
    val selectedObjects = selectedDiffableObjects()
    if (selectedObjects.take(2).count() < 2) return false
    if (selectedObjects.none { path.matches(it) }) return false

    _diffableSelection.value = getDiffableSelection(path)
    return true
  }

  /**
   * Diffable objects in the current tree selection. Lazy, so that callers looking for a single object don't have to
   * traverse a possibly huge selection.
   *
   * Ordered like `ChangesViewDiffPreviewHandler.iterateSelectedChanges` in monolith mode – selected changes in the tree
   * display order first, then selected unversioned files – so that navigating inside a selection visits files in the
   * same order in both modes.
   */
  @RequiresEdt
  private fun selectedDiffableObjects(): Sequence<Any> {
    val changes = changesView.selectedChangesNodes.asSequence().map { it.userObject }.filterIsInstance<Change>()
    val unversionedFiles = changesView.selectedUnversionedFiles.asSequence()
    return changes + unversionedFiles
  }

  /**
   * [currentChange] if it is still selected, the first selected diffable object otherwise.
   */
  @RequiresEdt
  private fun findSelectedDiffableNode(currentChange: ChangesTreePath?): Any? {
    if (currentChange != null) {
      val stillSelected = selectedDiffableObjects().firstOrNull { currentChange.matches(it) }
      if (stillSelected != null) return stillSelected
    }
    return changesView.selectedDiffableNode
  }

  /**
   * Diffable objects around [selectedDiffableNode] in the whole tree, in the tree display order.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun findNeighboursInTree(selectedDiffableNode: Any): Neighbours {
    var previousNode: Any? = null
    var nextNode: Any? = null

    var selectedNodeWasFound = false
    for (node in VcsTreeModelData.all(changesView).iterateNodes()) {
      if (!isDiffableNode(node)) continue
      val userObject = node.userObject

      when {
        userObject === selectedDiffableNode -> selectedNodeWasFound = true
        !selectedNodeWasFound -> previousNode = userObject
        else -> {
          nextNode = userObject
          break
        }
      }
    }

    return Neighbours(previousNode, nextNode)
  }

  private fun isDiffableNode(node: ChangesBrowserNode<*>): Boolean = when (node.userObject) {
    is Change -> true
    is FilePath -> node.isUnderTag(ChangesBrowserNode.UNVERSIONED_FILES_TAG)
    else -> false
  }
}

private data class Neighbours(val previous: Any?, val next: Any?)

/**
 * Diffable objects around [selectedObject] within [scope], in the tree display order.
 */
private fun neighboursWithin(scope: List<Any>, selectedObject: Any): Neighbours {
  val index = scope.indexOfFirst { it === selectedObject }
  if (index < 0) return Neighbours(null, null)
  return Neighbours(scope.getOrNull(index - 1), scope.getOrNull(index + 1))
}

private fun ChangesTreePath.matches(userObject: Any): Boolean = when (userObject) {
  is Change -> ChangesUtil.matches(userObject, filePath.filePath) && ChangeId.getId(userObject) == changeId
  is FilePath -> userObject == filePath.filePath
  else -> false
}

private fun getPathOrLog(node: Any?, log: (Any) -> Unit): ChangesTreePath? = node?.let {
  ChangesTreePath.create(it).also { path -> if (path == null) log(it) }
}