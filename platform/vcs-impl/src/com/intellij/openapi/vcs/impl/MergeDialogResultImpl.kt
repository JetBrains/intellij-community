// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MergeDialogResultImpl(
  private val processedFiles: List<VirtualFile>,
  private val shouldFinishMerge: Boolean,
) : AbstractVcsHelper.MergeDialogResult {
  override fun getProcessedFiles(): List<VirtualFile> = processedFiles
  override fun shouldFinishMerge(): Boolean = shouldFinishMerge
}
