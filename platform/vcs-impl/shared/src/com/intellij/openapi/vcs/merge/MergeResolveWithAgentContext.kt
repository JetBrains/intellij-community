// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class MergeResolveWithAgentContext(
  val project: Project,
  val files: List<VirtualFile>,
  val closeDialogForAgentHandoffHandler: (() -> Unit)? = null,
  val isLaunchContextValidHandler: (() -> Boolean)? = null,
) {
  fun closeDialogForAgentHandoff() {
    closeDialogForAgentHandoffHandler?.invoke()
  }

  fun isLaunchContextValid(): Boolean {
    return isLaunchContextValidHandler?.invoke() != false
  }

  companion object {
    @JvmField
    val KEY: DataKey<MergeResolveWithAgentContext> = DataKey.create("merge.resolve.with.agent.context")
  }
}
