// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.platform.vcs.impl.shared.commit.EditedCommitNode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun ChangesBrowserNode<*>.isUnderTag(tag: ChangesBrowserNode.Tag): Boolean = findParent { it.userObject == tag } != null

@ApiStatus.Internal
fun ChangesBrowserNode<*>.findChangeListNode(): ChangesBrowserChangeListNode? = findParentOfType()
@ApiStatus.Internal
fun ChangesBrowserNode<*>.findAmendNode(): EditedCommitNode? = findParentOfType()

val ChangesListView.selectedDiffableNode: Any?
  @ApiStatus.Internal
  get() = selectedChanges.firstOrNull() ?: selectedUnversionedFiles.firstOrNull()

private fun ChangesBrowserNode<*>.findParent(predicate: (ChangesBrowserNode<*>) -> Boolean): ChangesBrowserNode<*>? {
  var currentNode: ChangesBrowserNode<*>? = this
  while (currentNode != null) {
    if (predicate(currentNode)) return currentNode
    currentNode = currentNode.parent
  }

  return null
}

private inline fun <reified T : ChangesBrowserNode<*>> ChangesBrowserNode<*>.findParentOfType(): T? = findParent { it is T } as? T