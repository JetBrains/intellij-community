// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.*

class KotlinRenameVisitor : EvaluationVisitor, KtTreeVisitorVoid(){
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.KOTLIN
  override val feature: String = "rename"

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitKtFile(file: KtFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitKtFile(file)
  }

  fun visitFunction(function: KtNamedFunction) {
    val token = createCodeToken(function, TypeProperty.FUNCTION)
    if (token != null)
      codeFragment?.addChild(token)
    super.visitNamedFunction(function)
  }

  fun visitMethod(method: KtNamedFunction) {
    val token = createCodeToken(method, TypeProperty.METHOD)
    if (token != null)
      codeFragment?.addChild(token)
    super.visitNamedFunction(method)
  }

  fun visitVariable(variable: KtObjectDeclaration) {
    val token = createCodeToken(variable, TypeProperty.VARIABLE)
    if (token != null)
      codeFragment?.addChild(token)
    super.visitObjectDeclaration(variable)
  }

  fun visitLocalVariable(variable: KtObjectDeclaration) {
    val token = createCodeToken(variable, TypeProperty.LOCAL_VARIABLE)
    if (token != null)
      codeFragment?.addChild(token)
    super.visitObjectDeclaration(variable)
  }

  override fun visitClass(aClass: KtClass) {
    val token = createCodeToken(aClass, TypeProperty.CLASS)
    if (token != null)
      codeFragment?.addChild(token)
    super.visitClass(aClass)
  }

  fun visitField(field: KtProperty) {
    val token = createCodeToken(field, TypeProperty.FIELD)
    if (token != null)
      codeFragment?.addChild(token)
    super.visitProperty(field)
  }

  override fun visitParameter(parameter: KtParameter) {
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