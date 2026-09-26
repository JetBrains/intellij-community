package org.editorconfig.language.util

import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.editorconfig.common.syntax.psi.EditorConfigRootDeclaration
import com.intellij.psi.util.childrenOfType
import org.editorconfig.language.filetype.EditorConfigFileConstants.ROOT_KEY
import org.editorconfig.language.filetype.EditorConfigFileConstants.ROOT_VALUE
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.containsErrors
import org.editorconfig.language.util.EditorConfigTextMatchingUtil.textMatchesToIgnoreCase

val EditorConfigPsiFile.hasValidRootDeclaration: Boolean
  get() = this.childrenOfType<EditorConfigRootDeclaration>()
    .any(EditorConfigRootDeclaration::isValidRootDeclaration)

val EditorConfigRootDeclaration.isValidRootDeclaration: Boolean
  get() {
    if (containsErrors(this)) return false
    if (!textMatchesToIgnoreCase(rootDeclarationKey, ROOT_KEY)) return false
    val value = rootDeclarationValueList.singleOrNull() ?: return false
    return textMatchesToIgnoreCase(value, ROOT_VALUE)
  }
