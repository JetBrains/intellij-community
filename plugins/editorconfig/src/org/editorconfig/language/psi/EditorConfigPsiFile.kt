// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import com.intellij.psi.util.childrenOfType
import org.editorconfig.language.EditorConfigLanguage
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.editorconfig.language.filetype.EditorConfigFileType
import org.editorconfig.language.util.matches

class EditorConfigPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, EditorConfigLanguage) {
  override fun getFileType() = EditorConfigFileType
  override fun toString() = EditorConfigFileConstants.PSI_FILE_NAME

  val hasValidRootDeclaration: Boolean
    get() = this.childrenOfType<EditorConfigRootDeclaration>()
      .any(EditorConfigRootDeclaration::isValidRootDeclaration)

  val sections
    get() = this.childrenOfType<EditorConfigSection>()

  fun findRelevantNavigatable() =
    sections.lastOrNull { section ->
      val header = section.header
      if (header.isValidGlob) header matches virtualFile
      else false
    } ?: this
}
