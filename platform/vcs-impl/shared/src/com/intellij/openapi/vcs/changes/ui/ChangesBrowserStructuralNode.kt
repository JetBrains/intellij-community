// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * A structural element below a changed file. File actions use its enclosing change; navigation uses the element.
 */
@ApiStatus.Internal
abstract class ChangesBrowserStructuralNode<T>(userObject: T) : ChangesBrowserNode<T>(userObject) {
  init {
    markAsHelperNode()
    putUserData(ChangesBrowserNodeKeys.HIDE_INCLUSION_CHECKBOX, true)
  }

  abstract fun getNavigatable(): Navigatable?

  @RequiresReadLock
  @RequiresBackgroundThread
  open fun getPreviewNavigationRequest(): NavigationRequest? =
    getNavigatable()?.takeIf { it.canNavigateToSource() }?.navigationRequest()

  final override fun getFileCount(): Int = 0

  final override fun getDirectoryCount(): Int = 0

  fun getChangeNode(): ChangesBrowserNode<*>? {
    var node: ChangesBrowserNode<*>? = getParent()
    while (node != null) {
      if (node.userObject is Change) return node
      node = node.getParent()
    }
    return null
  }
}
