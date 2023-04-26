// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreview

interface DiffPreviewController {
  val activePreview: DiffPreview
}

abstract class DiffPreviewControllerBase : DiffPreviewController {

  protected abstract val simplePreview: DiffPreview

  @Suppress("LeakingThis")
  private val combinedPreview: CombinedDiffPreview? =
    if (CombinedDiffRegistry.isEnabled()) createCombinedDiffPreview() else null

  protected abstract fun createCombinedDiffPreview(): CombinedDiffPreview

  override val activePreview get() = chooseActivePreview()

  private fun chooseActivePreview(): DiffPreview {
    val combinedDiffLimit = CombinedDiffRegistry.getFilesLimit()
    val combinedDiffPreview = combinedPreview

    return if (combinedDiffPreview != null && (combinedDiffLimit == -1 || combinedDiffPreview.getFileSize() <= combinedDiffLimit)) {
      combinedDiffPreview
    }
    else {
      simplePreview
    }
  }
}
