// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.psi.PsiReference

abstract class EditorConfigHeaderBase(node: ASTNode) : EditorConfigHeaderElementBase(node), EditorConfigHeader {

  final override fun getReference(): PsiReference {
    return ApplicationManager.getApplication().service<EditorConfigReferenceSupport>().getReference(this)
  }
}
