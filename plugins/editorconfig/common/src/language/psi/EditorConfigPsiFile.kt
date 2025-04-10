// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import com.intellij.psi.util.childrenOfType
import org.editorconfig.language.EditorConfigLanguage
import org.editorconfig.language.filetype.EditorConfigFileType

class EditorConfigPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, EditorConfigLanguage) {
  override fun getFileType(): EditorConfigFileType = EditorConfigFileType
  override fun toString(): String = "EditorConfig file"

  val sections: List<EditorConfigSection>
    get() = this.childrenOfType<EditorConfigSection>()
}
