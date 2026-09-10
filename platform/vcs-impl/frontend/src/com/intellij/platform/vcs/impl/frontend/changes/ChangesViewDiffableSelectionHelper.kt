// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.vcs.changes.ui.findDiffGroupNode
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
  fun updateSelection() {
    _diffableSelection.update { currentValue -> getDiffableSelection(currentValue?.selectedChange) }
  }

  /**
   * Recomputes the selection even if the same change is still selected.
   *
   * The file counter values change when the tree model changes (files added or removed, changelist edited), so
   * [updateSelection] would keep stale numbers.
   */
  @RequiresEdt
  fun updateSelectionAfterModelChange() {
    if (changesView.selectedDiffableNode != null) {
      _diffableSelection.value = getDiffableSelection(_diffableSelection.value?.selectedChange)
    }
  }

  /**
   * The position of the selected file comes from [getGroupOrSelectionScopedPosition] or from [getTreeScopedPosition],
   * depending on [ChangesUtil.isScopeNavigationToGroupEnabled]. This mirrors
   * `ChangeViewDiffRequestProcessor.createGoToChangeAction`, which picks between
   * `getGroupOrMultiSelectionListSelection` and `getChanges` the same way.
   *
   * @param currentChange the change that is currently shown in the diff preview, if any. It is kept as the selected one
   * as long as it is still selected, so that selecting a parent node (e.g. a changelist) doesn't switch the previewed
   * file. Everything else depends on the whole selection and is always recalculated.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun getDiffableSelection(currentChange: ChangesTreePath?): ChangesViewDiffableSelection? {
    val selectedDiffableNode = findSelectedDiffableNode(currentChange) ?: return null
    val selectedNodePath =
      createPathOrLog(selectedDiffableNode) { LOG.warn("Could not create path for selected node: $it") } ?: return null

    val explicitSelection = selectedDiffableObjects().toList()
    val position =
      if (ChangesUtil.isScopeNavigationToGroupEnabled()) getGroupOrSelectionScopedPosition(selectedDiffableNode, explicitSelection)
      else getTreeScopedPosition(selectedDiffableNode, explicitSelection)

    return ChangesViewDiffableSelection(
      selectedChange = selectedNodePath,
      previousChange = createPathOrLog(position.neighbours.previous) { LOG.warn("Could not create path for previous node: $it") },
      nextChange = createPathOrLog(position.neighbours.next) { LOG.warn("Could not create path for next node: $it") },
      selectedIndex = position.counter.selectedIndex,
      changesCount = position.counter.changesCount)
  }

  /**
   * Position with the counter scoped to the explicit selection of 2+ diffable files, or to the group of the selected
   * file.
   *
   * Mirrors `ChangeViewDiffRequestProcessor.getGroupOrMultiSelectionListSelection`, so that the counter shows the same
   * values in monolith and split mode: an explicit multiple selection scopes the counter to itself, a single selection
   * to the group of the selected file.
   *
   * Only the resulting numbers are sent to the backend, which has no notion of the selection being explicit.
   *
   * The previous and the next file stay scoped to the whole tree, as they are in monolith mode, so navigation can leave
   * the group.
   */
  @RequiresEdt
  private fun getGroupOrSelectionScopedPosition(selectedObject: Any, explicitSelection: List<Any>): SelectionPosition {
    // An explicit selection of 2+ diffable files scopes navigation to itself, as monolith mode does, so that
    // "Compare Previous/Next File" walks the selected files only. Moving inside it must not change the tree selection,
    // see [moveWithinExplicitSelection]. The tree is not visited at all, because both values come from the selection.
    if (explicitSelection.size > 1) {
      return SelectionPosition(neighboursWithin(explicitSelection, selectedObject),
                               counterWithin(explicitSelection, selectedObject))
    }

    val nodeWithNeighbours = findNodeWithNeighbours(selectedObject)
                             ?: return SelectionPosition(Neighbours(null, null), FilesCounter.UNKNOWN)
    val (selectedNode, neighbours) = nodeWithNeighbours
    return SelectionPosition(neighbours, getGroupCounter(selectedNode, explicitSelection))
  }

  /**
   * Position with the counter scoped to the whole tree.
   *
   * Mirrors `ChangeViewDiffRequestProcessor.getChanges`. An explicit selection of 2+ diffable files still scopes the
   * previous and the next file to itself, as it does in monolith mode.
   */
  @RequiresEdt
  private fun getTreeScopedPosition(selectedObject: Any, explicitSelection: List<Any>): SelectionPosition {
    var previousObject: Any? = null
    var nextObject: Any? = null
    var selectedIndex: Int? = null
    var diffableNodesCount = 0

    for (node in diffableNodes()) {
      val userObject = node.userObject

      when {
        userObject === selectedObject -> {
          selectedIndex = diffableNodesCount
        }
        // The previous file is set to the current node until the selected one is found
        selectedIndex == null -> {
          previousObject = userObject
        }
        // The next file is set only after the selected one is found and the walk moves one step further
        nextObject == null -> {
          nextObject = userObject
        }
      }
      diffableNodesCount++
    }

    val neighbours = if (explicitSelection.size > 1) {
      neighboursWithin(explicitSelection, selectedObject)
    } else {
      Neighbours(previousObject, nextObject)
    }
    val counter = if (selectedIndex == null) FilesCounter.UNKNOWN else FilesCounter(selectedIndex, diffableNodesCount)
    return SelectionPosition(neighbours, counter)
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

  private data class SelectionPosition(val neighbours: Neighbours, val counter: FilesCounter)

  private data class FilesCounter(val selectedIndex: Int?, val changesCount: Int) {
    companion object {
      val UNKNOWN: FilesCounter = FilesCounter(null, 0)
    }
  }

  /**
   * File counter values within the group of the selected node, in the tree display order.
   *
   * The group is defined by [findDiffGroupNode], which is shared with
   * `ChangesViewDiffPreviewHandler.iterateChangesFromSameGroup`. Falls back to the current selection for a node that
   * belongs to no group, as the monolith does.
   */
  @RequiresEdt
  private fun getGroupCounter(selectedNode: ChangesBrowserNode<*>, selectedObjects: List<Any>): FilesCounter {
    val groupNode = selectedNode.findDiffGroupNode() ?: return counterWithin(selectedObjects, selectedNode.userObject)

    val groupObjects = VcsTreeModelData.allUnder(groupNode).iterateNodes()
      .asSequence()
      .filter { isDiffableNode(it) }
      .map { it.userObject }
      .toList()
    return counterWithin(groupObjects, selectedNode.userObject)
  }

  private fun counterWithin(scope: List<Any>, selectedObject: Any): FilesCounter =
    FilesCounter(scope.indexOfFirst { it === selectedObject }.takeIf { it >= 0 }, scope.size)

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
   * All diffable nodes of the tree, in the display order. Lazy, so that a caller can stop the walk.
   */
  @RequiresEdt
  private fun diffableNodes(): Sequence<ChangesBrowserNode<*>> =
    VcsTreeModelData.all(changesView).iterateNodes().asSequence().filter { isDiffableNode(it) }

  /**
   * The node of [selectedObject] and the diffable objects around it, or `null` when the tree holds no node for it.
   *
   * [Sequence.windowed] is lazy, so the walk stops one node past the selected one.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun findNodeWithNeighbours(selectedObject: Any): Pair<ChangesBrowserNode<*>, Neighbours>? {
    // The padding gives the first and the last node a window too, and marks their missing neighbour as absent.
    val padding = sequenceOf<ChangesBrowserNode<*>?>(null)
    val window = (padding + diffableNodes() + padding)
      .windowed(size = 3)
      .firstOrNull { (_, selected, _) -> selected?.userObject === selectedObject } ?: return null

    val (previous, selected, next) = window
    // The window matched, so its middle node is present.
    return checkNotNull(selected) to Neighbours(previous?.userObject, next?.userObject)
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

private fun createPathOrLog(node: Any?, log: (Any) -> Unit): ChangesTreePath? = node?.let {
  ChangesTreePath.create(it).also { path -> if (path == null) log(it) }
}