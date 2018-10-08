// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.widget

import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.util.EditorConfigPresentationUtil
import org.editorconfig.language.util.matches

class EditorConfigPopupStep(
  files: List<EditorConfigPsiFile>,
  private val virtualFile: VirtualFile
) : BaseListPopupStep<EditorConfigPsiFile>(null, files) {
  override fun getTextFor(value: EditorConfigPsiFile) =
    EditorConfigPresentationUtil.getFileName(value, true)

  override fun onChosen(selectedValue: EditorConfigPsiFile, finalChoice: Boolean): PopupStep<*>? {
    if (finalChoice) {
      return doFinalStep {
        findRelevantNavigatable(selectedValue).navigate(true)
      }
    }

    return PopupStep.FINAL_CHOICE
  }

  private fun findRelevantNavigatable(file: EditorConfigPsiFile) =
    file.sections.lastOrNull { section ->
      val header = section.header
      if (header.isValidGlob) header matches virtualFile
      else false
    } ?: file
}
