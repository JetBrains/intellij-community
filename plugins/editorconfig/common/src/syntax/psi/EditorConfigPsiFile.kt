package com.intellij.editorconfig.common.syntax.psi

import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.util.childrenOfType

class EditorConfigPsiFile internal constructor(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, EditorConfigLanguage) {
  override fun getFileType(): FileType = viewProvider.fileType
  override fun toString(): String = "EditorConfig file"

  val sections: List<EditorConfigSection>
    get() = this.childrenOfType<EditorConfigSection>()
}
