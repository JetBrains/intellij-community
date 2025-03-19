// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import com.intellij.platform.kernel.withLastKnownDb
import com.intellij.platform.vcs.impl.shared.rhizome.NodeEntity
import com.jetbrains.rhizomedb.exists
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JTree

@ApiStatus.Internal
@Suppress("SSBasedInspection")
abstract class EntityChangesBrowserNode<T : NodeEntity>(entity: T) : ChangesBrowserNode<T>(entity) {
  override fun render(tree: JTree, renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    withLastKnownDb {
      if (!getUserObject().exists()) return@withLastKnownDb
      render(renderer, selected, expanded, hasFocus)
    }
  }

  override fun getFileCount(): Int {
    return (userObject as NodeEntity).children.size
  }

  override fun getTextPresentation(): @Nls String? {
    return withLastKnownDb {
      doGetTextPresentation()
    }
  }

  abstract fun doGetTextPresentation(): @Nls String?
}