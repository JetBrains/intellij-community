// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreview
import com.intellij.openapi.vcs.changes.ui.ChangesTree

interface DiffPreviewController {
  /**
   * see [DiffPreviewControllerBase]
   */
  val activePreview: DiffPreview
}

/**
 * Base controller to choose between regular single-file diff preview ("simple") and combined diff preview ("combined").
 *
 * Implementors should decide when to create the combined diff preview by calling [activePreview].
 * [CombinedDiffPreview] should be created together with the corresponding [ChangesTree] creation or customization,
 * but before its [javax.swing.JTree.TREE_MODEL_PROPERTY] change.
 */
abstract class DiffPreviewControllerBase : DiffPreviewController {

  protected abstract val simplePreview: DiffPreview

  private val combinedPreview: CombinedDiffPreview? by lazy {
    if (CombinedDiffRegistry.isEnabled()) createCombinedDiffPreview() else null
  }

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
