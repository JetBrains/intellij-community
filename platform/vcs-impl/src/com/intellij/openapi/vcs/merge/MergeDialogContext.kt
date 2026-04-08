// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Context exposed to actions contributed to the merge dialog toolbar via
 * the `Merge.Dialog.Iterative.ResolveActions` action group.
 */
@Internal
class MergeDialogContext(
  val project: Project,
  val mergeProvider: MergeProvider,
  val mergeDialogCustomizer: MergeDialogCustomizer,
  private val getUnresolvedFiles: () -> List<VirtualFile>,
  private val isModalDialogProvider: () -> Boolean,
  private val closeDialogForAgentHandoffHandler: (() -> Unit)?,
) {
  val unresolvedFiles: List<VirtualFile> get() = getUnresolvedFiles()

  fun isModalDialog(): Boolean = isModalDialogProvider()

  fun closeDialogForAgentHandoff() {
    closeDialogForAgentHandoffHandler?.invoke()
  }

  companion object {
    @JvmField
    val KEY: DataKey<MergeDialogContext> = DataKey.create("merge.dialog.context")
  }
}
