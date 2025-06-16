// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOption
import com.intellij.editorconfig.common.syntax.psi.EditorConfigSection
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.psi.PsiReference

abstract class EditorConfigDescribableElementBase(node: ASTNode) : ASTWrapperPsiElement(node), EditorConfigDescribableElement {
  final override val option: EditorConfigOption
    get() = requiredParentOfType()

  final override val section: EditorConfigSection
    get() = requiredParentOfType()

  override val describableParent: EditorConfigDescribableElement?
    get() = parent as? EditorConfigDescribableElement

  override val declarationSite: String
    get() {
      val header = section.header.text
      val virtualFile = containingFile.virtualFile ?: return header
      val fileName = virtualFile.presentableName
      return "$header ($fileName)"
    }


  override fun getReference(): PsiReference? {
    return ApplicationManager.getApplication().service<EditorConfigReferenceSupport>().getReference(this)
  }

  final override fun toString(): String = text
}
