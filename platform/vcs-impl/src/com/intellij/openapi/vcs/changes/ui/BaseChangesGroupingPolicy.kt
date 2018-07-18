// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder.IS_CACHING_ROOT
import com.intellij.openapi.vfs.VirtualFile

abstract class BaseChangesGroupingPolicy : ChangesGroupingPolicy {
  protected var nextPolicy: ChangesGroupingPolicy? = null

  override fun setNextGroupingPolicy(policy: ChangesGroupingPolicy?) {
    nextPolicy = policy
  }

  protected fun resolveVirtualFile(nodePath: StaticFilePath): VirtualFile? =
    generateSequence(nodePath) { it.parent }.mapNotNull { it.resolve() }.firstOrNull()

  companion object {
    @JvmStatic
    fun getCachingRoot(node: ChangesBrowserNode<*>?, subtreeRoot: ChangesBrowserNode<*>): ChangesBrowserNode<*> =
      generateSequence(node) { it.parent }.find { IS_CACHING_ROOT.get(it) == true } ?: subtreeRoot
  }
}