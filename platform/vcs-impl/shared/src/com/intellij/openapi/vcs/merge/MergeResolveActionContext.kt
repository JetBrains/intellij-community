// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class MergeResolveActionContext(
  val project: Project,
  private val selectionHintFilesProvider: () -> List<VirtualFile>,
  private val closeSourceUiHandler: (() -> Unit)? = null,
  private val isContextValidHandler: (() -> Boolean)? = null,
) {
  val selectionHintFiles: List<VirtualFile>
    get() = selectionHintFilesProvider()

  fun closeSourceUi() {
    closeSourceUiHandler?.invoke()
  }

  fun isContextValid(): Boolean {
    return isContextValidHandler?.invoke() != false
  }

  companion object {
    @JvmField
    val KEY: DataKey<MergeResolveActionContext> = DataKey.create("merge.resolve.action.context")
  }
}
