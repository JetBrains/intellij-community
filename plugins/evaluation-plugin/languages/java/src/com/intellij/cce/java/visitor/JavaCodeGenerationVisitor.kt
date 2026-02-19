// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.java.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TokenLocationProperty
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.evaluable.INTERNAL_API_CALLS_PROPERTY
import com.intellij.cce.evaluable.INTERNAL_RELEVANT_FILES_PROPERTY
import com.intellij.cce.evaluable.METHOD_NAME_PROPERTY
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.startOffset

class JavaCodeGenerationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.JAVA
  override val feature: String = "code-generation"

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitJavaFile(file)
  }

  override fun visitMethod(method: PsiMethod) {
    val methodProperties = SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {
      put(METHOD_NAME_PROPERTY, method.name)
      QualifiedNameProviderUtil.getQualifiedName(method)?.let {
        put(TokenLocationProperty.METHOD_QUALIFIED_NAME.key, it)
      }
      method.containingClass?.name?.let {
        put(TokenLocationProperty.CLASS.key, it)
      }
      if (method.containingClass?.name?.endsWith("Test") == true) { // TODO normal condition
        put(TokenLocationProperty.TEST_SOURCE.key, true.toString())
      }

      val (internalApiCalls, internalRelevantFiles) = runBlockingCancellable {
        extractInternalApiCallsAndRelevantFiles(method)
      }

      put(INTERNAL_API_CALLS_PROPERTY, internalApiCalls.sorted().joinToString("\n"))
      put(INTERNAL_RELEVANT_FILES_PROPERTY, internalRelevantFiles.sorted().joinToString("\n"))
    }

    codeFragment?.addChild(CodeToken(method.text, method.startOffset, methodProperties))

    val body = method.body
    if (body != null) {
      val meaningfullBodyChildren = body.trim()
      if (meaningfullBodyChildren.any()) {
        val firstMeaningfulChildren = meaningfullBodyChildren.first()
        val meaningfullBodyText = meaningfullBodyChildren.map { it.text }.joinToString("")

        codeFragment?.addChild(
          CodeToken(meaningfullBodyText, firstMeaningfulChildren.startOffset, SimpleTokenProperties.create(TypeProperty.METHOD_BODY, SymbolLocation.PROJECT) {})
        )
      }
    }
  }

  private fun PsiCodeBlock.trim(): List<PsiElement> {
    val firstIndex = children.indexOfFirst { it.isMeaningful()}
    val lastIndex = children.indexOfLast { it.isMeaningful() }
    val indexRange = (firstIndex.. lastIndex)
    return children.filterIndexed { index, it ->
      it is PsiExpressionStatement
      index in indexRange
    }
  }

  private fun PsiElement.isMeaningful(): Boolean {
    if (this is PsiWhiteSpace) {
      return false
    }
    if (this is PsiJavaToken) {
      return tokenType != JavaTokenType.LBRACE && tokenType != JavaTokenType.RBRACE
    }
    return true
  }
}