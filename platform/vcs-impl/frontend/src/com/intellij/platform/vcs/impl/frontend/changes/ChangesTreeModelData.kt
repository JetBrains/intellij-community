// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserNode
import com.intellij.util.containers.JBIterable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ChangesTreeModelData {
  fun iterateUserObjects(): JBIterable<*> {
    return iterateRawNodes().map(ChangesBrowserNode<*>::getUserObject);
  }

  fun <T> iterateUserObjects(clazz: Class<T>): JBIterable<T> {
    return iterateUserObjects().filter(clazz)
  }

  abstract fun iterateRawNodes(): JBIterable<ChangesBrowserNode<*>>
}

@ApiStatus.Internal
open class ExactlySelectedData(tree: ChangesTree) : ChangesTreeModelData() {
  private val selectionPaths = tree.selectionPaths
  override fun iterateRawNodes(): JBIterable<ChangesBrowserNode<*>> {
    val paths = selectionPaths ?: return JBIterable.empty()
    return JBIterable.of(*paths).map { it.lastPathComponent as ChangesBrowserNode<*> }
  }
}

@ApiStatus.Internal
class SelectedData(tree: ChangesTree) : ExactlySelectedData(tree) {
  override fun iterateRawNodes(): JBIterable<ChangesBrowserNode<*>> {
    return super.iterateRawNodes().flatMap(ChangesBrowserNode<*>::traverse).unique()
  }
}

