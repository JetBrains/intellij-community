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

    val (prevNode, nextNode) = findPreviousAndNextDiffableNodes(selectedDiffableNode)

    return ChangesViewDiffableSelection(
      selectedChange = selectedNodePath,
      previousChange = getPathOrLog(prevNode) { LOG.warn("Could not create path for previous node: $it") },
      nextChange = getPathOrLog(nextNode) { LOG.warn("Could not create path for next node: $it") })
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

  private fun getPathOrLog(node: Any?, log: (Any) -> Unit): ChangesTreePath? = node?.let {
    ChangesTreePath.create(it).also { path -> if (path == null) log(it) }
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

  private fun ChangesTreePath.matches(userObject: Any): Boolean = when (userObject) {
    is Change -> ChangesUtil.matches(userObject, filePath.filePath) && ChangeId.getId(userObject) == changeId
    is FilePath -> userObject == filePath.filePath
    else -> false
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
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
