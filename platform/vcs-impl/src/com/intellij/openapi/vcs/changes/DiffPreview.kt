// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.AnActionEvent

interface DiffPreview {
  fun updatePreview(fromModelRefresh: Boolean) = Unit

  fun openPreview(requestFocus: Boolean): Boolean
  fun closePreview()

  /**
   * Allows overriding 'Show Diff' action availability and presentation
   */
  fun updateDiffAction(event: AnActionEvent) = Unit

  /**
   * Allows overriding 'Show Diff' action behavior.
   * For example, by using External Diff Tools when applicable.
   */
  fun performDiffAction(): Boolean = openPreview(true)

  companion object {
    @JvmStatic
    fun setPreviewVisible(preview: DiffPreview, value: Boolean) {
      if (value) {
        preview.openPreview(false)
      }
      else {
        preview.closePreview()
      }
    }
  }
}
