// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.psi.base

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.psi.PsiReference
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.psi.EditorConfigReferenceSupport

abstract class EditorConfigHeaderBase(node: ASTNode) : EditorConfigHeaderElementBase(node), EditorConfigHeader {

  final override fun getReference(): PsiReference {
    return ApplicationManager.getApplication().service<EditorConfigReferenceSupport>().getReference(this)
  }
}
