// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.kernel.withKernel
import com.intellij.vcs.impl.shared.rhizome.NodeEntity
import kotlinx.coroutines.runBlocking
import javax.swing.JTree

@Suppress("SSBasedInspection")
open class EntityChangesBrowserNode<T : NodeEntity>(entity: T) : ChangesBrowserNode<T>(entity) {
  override fun render(tree: JTree, renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    runBlocking {
      withKernel {
        render(renderer, selected, expanded, hasFocus)
      }
    }
  }

  override fun getFileCount(): Int {
    return (userObject as NodeEntity).children.size
  }

}