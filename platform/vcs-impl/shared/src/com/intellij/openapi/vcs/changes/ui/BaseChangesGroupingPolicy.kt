// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.changes.ui.TreeModelBuilderKeys.IS_CACHING_ROOT
import com.intellij.openapi.vfs.VirtualFile

abstract class BaseChangesGroupingPolicy : ChangesGroupingPolicy {
  protected var nextPolicy: ChangesGroupingPolicy? = null

  final override fun setNextGroupingPolicy(policy: ChangesGroupingPolicy?) {
    nextPolicy = policy
  }

  @Deprecated("Prefer using [VcsImplUtil.findValidParentAccurately]",
              ReplaceWith("VcsImplUtil.findValidParentAccurately(nodePath.filePath)",
                          "com.intellij.vcsUtil.VcsImplUtil"))
  protected fun resolveVirtualFile(nodePath: StaticFilePath): VirtualFile? =
    generateSequence(nodePath) { it.parent }.mapNotNull { it.resolve() }.firstOrNull()

  companion object {
    @JvmStatic
    fun markCachingRoot(node: ChangesBrowserNode<*>) {
      IS_CACHING_ROOT.set(node, true)
    }

    @JvmStatic
    fun getCachingRoot(node: ChangesBrowserNode<*>?, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> {
      var parent = node
      while (parent != null) {
        if (IS_CACHING_ROOT.get(parent) == true) {
          return parent
        }
        parent = parent.parent
      }

      return subtreeRoot
    }
  }
}