// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.*

class JavaRenameVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.JAVA
  override val feature: String = "rename"

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitJavaFile(file)
  }

  override fun visitLocalVariable(variable: PsiLocalVariable) {
    val token = createCodeToken(variable, TypeProperty.LOCAL_VARIABLE)
    if (token != null)
      codeFragment?.addChild(token)
    super.visitLocalVariable(variable)
  }

  override fun visitField(field: PsiField) {
    val token = createCodeToken(field, TypeProperty.FIELD)
    if (token != null)
      codeFragment?.addChild(token)
    super.visitField(field)
  }

  override fun visitParameter(parameter: PsiParameter) {
    val token = createCodeToken(parameter, TypeProperty.PARAMETER)
    if (token != null)
      codeFragment?.addChild(token)
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