// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.base

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import org.editorconfig.language.psi.EditorConfigRootDeclaration

abstract class EditorConfigRootDeclarationBase(node: ASTNode) : ASTWrapperPsiElement(node), EditorConfigRootDeclaration {

  private val declarationSite: String
    get() {
      return containingFile.virtualFile?.presentableName ?: return ""
    }

  final override fun getPresentation(): PresentationData = PresentationData(text, declarationSite, AllIcons.Nodes.HomeFolder, null)
  final override fun getName(): String = rootDeclarationKey.text
}
