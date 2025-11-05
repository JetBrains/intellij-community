// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface VcsLogChangesTreeModifier {
  companion object {
    private val EP = ExtensionPointName.create<VcsLogChangesTreeModifier>("com.intellij.vcsLogChangesTreeBuilder")

    @RequiresBackgroundThread
    fun modifyTreeModelBuilder(treeModel: TreeModelBuilder, state: VcsLogAsyncChangesTreeModel.ChangesState.Changes): TreeModelBuilder {
      EP.forEachExtensionSafe { extension ->
        if (extension.isApplicable(treeModel)) {
          extension.modifyTreeModelBuilder(treeModel, state)
        }
      }
      return treeModel
    }
  }

  fun isApplicable(model: TreeModelBuilder): Boolean

  @RequiresBackgroundThread
  fun modifyTreeModelBuilder(treeModel: TreeModelBuilder, state: VcsLogAsyncChangesTreeModel.ChangesState.Changes): TreeModelBuilder
}