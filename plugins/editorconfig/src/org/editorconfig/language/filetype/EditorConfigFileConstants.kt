// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.filetype

import com.intellij.application.options.CodeStyle
import com.intellij.psi.PsiFile
import org.editorconfig.language.EditorConfigLanguage

object EditorConfigFileConstants {
  const val FILE_EXTENSION = "editorconfig"
  const val FILE_NAME_WITHOUT_EXTENSION = ""
  const val FILE_NAME = "$FILE_NAME_WITHOUT_EXTENSION.$FILE_EXTENSION"
  const val PSI_FILE_NAME = "EditorConfig file"
  const val FILETYPE_NAME = "EditorConfig"
  const val ROOT_KEY = "root"
  const val ROOT_VALUE = "true"

  private const val ROOT_DECLARATION_WITH_SPACES = "$ROOT_KEY = $ROOT_VALUE"
  private const val ROOT_DECLARATION_WITHOUT_SPACES = "$ROOT_KEY=$ROOT_VALUE"

  fun getRootDeclarationFor(file: PsiFile): String {
    val settings = CodeStyle.getLanguageSettings(file, EditorConfigLanguage)
    val needSpace = settings.SPACE_AROUND_ASSIGNMENT_OPERATORS
    return if (needSpace) ROOT_DECLARATION_WITH_SPACES
    else ROOT_DECLARATION_WITHOUT_SPACES
  }
}
