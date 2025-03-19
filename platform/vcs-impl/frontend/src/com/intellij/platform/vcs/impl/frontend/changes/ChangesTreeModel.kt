// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserNode
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

@ApiStatus.Internal
class ChangesTreeModel(rootNode: ChangesBrowserNode<*>) : DefaultTreeModel(rootNode) {
  override fun valueForPathChanged(path: TreePath, newValue: Any?) {
    nodeChanged(path.lastPathComponent as TreeNode)
  }
}