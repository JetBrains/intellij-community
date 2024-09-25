// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.changes

import com.intellij.util.containers.JBIterable
import com.intellij.vcs.impl.frontend.shelf.tree.ChangesBrowserNode

abstract class ChangesTreeModelData {
  fun iterateUserObjects(): JBIterable<*> {
    return iterateRawNodes().map(ChangesBrowserNode<*>::getUserObject);
  }

  fun <T> iterateUserObjects(clazz: Class<T>): JBIterable<T> {
    return iterateUserObjects().filter(clazz)
  }

  abstract fun iterateRawNodes(): JBIterable<ChangesBrowserNode<*>>
}

open class ExactlySelectedData(tree: ChangesTree) : ChangesTreeModelData() {
  private val selectionPaths = tree.selectionPaths
  override fun iterateRawNodes(): JBIterable<ChangesBrowserNode<*>> {
    return JBIterable.of(*selectionPaths).map { it.lastPathComponent as ChangesBrowserNode<*> }
  }
}

class SelectedData(tree: ChangesTree) : ExactlySelectedData(tree) {
  override fun iterateRawNodes(): JBIterable<ChangesBrowserNode<*>> {
    return super.iterateRawNodes().flatMap(ChangesBrowserNode<*>::traverse).unique()
  }
}

