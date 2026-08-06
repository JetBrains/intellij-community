// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.treeAssertion

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.JTree
import javax.swing.tree.TreePath

val TreePath.userObject: Any?
  get() = TreeUtil.getUserObject(lastPathComponent)

fun JTree.isSelected(treePath: TreePath): Boolean =
  selectionPath == treePath

fun buildTreePathTree(tree: JTree): SimpleTree<TreePath> {
  return invokeAndWaitIfNeeded {
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    PlatformTestUtil.waitWhileBusy(tree)

    buildTree(
      listOf(TreePath(tree.model.root)),
      nameGetter = {
        PlatformTestUtil.toString(userObject, null) ?: ""
      },
      childrenGetter = {
        (0 until tree.model.getChildCount(lastPathComponent))
          .map { tree.model.getChild(lastPathComponent, it) }
          .map { pathByAddingChild(it) }
      }
    )
  }
}