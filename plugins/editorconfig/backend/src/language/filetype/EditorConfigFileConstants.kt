// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.filetype

import com.intellij.application.options.CodeStyle
import com.intellij.psi.PsiFile
import org.editorconfig.language.EditorConfigLanguage

object EditorConfigFileConstants {
  const val FILE_EXTENSION: String = "editorconfig"
  const val FILE_NAME_WITHOUT_EXTENSION: String = ""
  const val FILE_NAME: String = "$FILE_NAME_WITHOUT_EXTENSION.$FILE_EXTENSION"
  const val PSI_FILE_NAME: String = "EditorConfig file"
  const val FILETYPE_NAME: String = "EditorConfig"
  const val ROOT_KEY: String = "root"
  const val ROOT_VALUE: String = "true"

  private const val ROOT_DECLARATION_WITH_SPACES = "$ROOT_KEY = $ROOT_VALUE"
  private const val ROOT_DECLARATION_WITHOUT_SPACES = "$ROOT_KEY=$ROOT_VALUE"

  fun getRootDeclarationFor(file: PsiFile): String {
    val settings = CodeStyle.getLanguageSettings(file, EditorConfigLanguage)
    val needSpace = settings.SPACE_AROUND_ASSIGNMENT_OPERATORS
    return if (needSpace) ROOT_DECLARATION_WITH_SPACES
    else ROOT_DECLARATION_WITHOUT_SPACES
  }
}
