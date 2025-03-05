// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.java.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.RenameVisitorBase
import com.intellij.psi.*

class JavaRenameVisitor : RenameVisitorBase(Language.JAVA) {
  override fun createPsiVisitor(codeFragment: CodeFragment): PsiElementVisitor {
    return JavaRenamePsiVisitor(codeFragment)
  }

  private class JavaRenamePsiVisitor(private val codeFragment: CodeFragment): JavaRecursiveElementVisitor() {
    override fun visitMethod(method: PsiMethod) {
      val token = createCodeToken(method, TypeProperty.METHOD)
      if (token != null)
        codeFragment.addChild(token)
      super.visitMethod(method)
    }

    override fun visitClass(aClass: PsiClass) {
      val token = createCodeToken(aClass, TypeProperty.CLASS)
      if (token != null)
        codeFragment.addChild(token)
      super.visitClass(aClass)
    }
    override fun visitLocalVariable(variable: PsiLocalVariable) {
      val token = createCodeToken(variable, TypeProperty.LOCAL_VARIABLE)
      if (token != null)
        codeFragment.addChild(token)
      super.visitLocalVariable(variable)
    }

    override fun visitField(field: PsiField) {
      val token = createCodeToken(field, TypeProperty.FIELD)
      if (token != null)
        codeFragment.addChild(token)
      super.visitField(field)
    }

    override fun visitParameter(parameter: PsiParameter) {
      val token = createCodeToken(parameter, TypeProperty.PARAMETER)
      if (token != null)
        codeFragment.addChild(token)
      super.visitParameter(parameter)
    }

    private fun createCodeToken(namedElement: PsiNamedElement, tokenType: TypeProperty): CodeToken? {
      val name = namedElement.name ?: return null
      return CodeToken(name, namedElement.textOffset, properties(tokenType, SymbolLocation.PROJECT) {})
    }

    private fun properties(tokenType: TypeProperty, location: SymbolLocation, init: JvmProperties.Builder.() -> Unit)
      : TokenProperties {
      return JvmProperties.create(tokenType, location) {
        init(this)
      }
    }
  }
}