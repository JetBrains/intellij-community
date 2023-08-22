// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreview

interface DiffPreviewController {
  val activePreview: DiffPreview
}

/**
 * Controller to choose between regular single-file diff preview ("simple") and combined diff preview ("combined").
 *
 * Implementors should provide builders for the simple diff preview and combined preview if possible.
 * [CombinedDiffPreview] should be created together with the corresponding [ChangesTree] creation or customization,
 * but before its [javax.swing.JTree.TREE_MODEL_PROPERTY] change.
 */
class DiffPreviewControllerImpl(
  private val simpleDiffPreviewBuilder: () -> DiffPreview,
  private val combinedDiffPreviewBuilder: () -> CombinedDiffPreview? = { null }
) : DiffPreviewController {

  override val activePreview: DiffPreview
    get() = chooseActivePreview()

  private val simpleDiffPreview: DiffPreview by lazy { simpleDiffPreviewBuilder() }

  private val combinedDiffPreview: CombinedDiffPreview? by lazy { combinedDiffPreviewBuilder() }

  private fun chooseActivePreview(): DiffPreview {
    if (!CombinedDiffRegistry.isEnabled()) return simpleDiffPreview
    val combinedDiff = combinedDiffPreview ?: return simpleDiffPreview

    val combinedDiffLimit = CombinedDiffRegistry.getFilesLimit()
    if (combinedDiffLimit == -1 || combinedDiff.getFileSize() <= combinedDiffLimit) {
      return combinedDiff
    }
    return simpleDiffPreview
  }
}