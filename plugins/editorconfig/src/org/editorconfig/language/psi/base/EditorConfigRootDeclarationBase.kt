// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import org.editorconfig.language.filetype.EditorConfigFileConstants.ROOT_KEY
import org.editorconfig.language.filetype.EditorConfigFileConstants.ROOT_VALUE
import org.editorconfig.language.psi.EditorConfigRootDeclaration
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.containsErrors
import org.editorconfig.language.util.EditorConfigTextMatchingUtil.textMatchesToIgnoreCase

abstract class EditorConfigRootDeclarationBase(node: ASTNode) : ASTWrapperPsiElement(node), EditorConfigRootDeclaration {
  final override fun isValidRootDeclaration(): Boolean {
    if (containsErrors(this)) return false
    if (!textMatchesToIgnoreCase(rootDeclarationKey, ROOT_KEY)) return false
    val value = rootDeclarationValueList.singleOrNull() ?: return false
    return textMatchesToIgnoreCase(value, ROOT_VALUE)
  }

  private val declarationSite: String
    get() {
      return containingFile.virtualFile?.presentableName ?: return ""
    }

  final override fun getPresentation() = PresentationData(text, declarationSite, AllIcons.Nodes.HomeFolder, null)
  final override fun getName(): String = rootDeclarationKey.text
}
