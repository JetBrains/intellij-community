// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.evaluable.chat.INTERNAL_API_CALLS_PROPERTY
import com.intellij.cce.evaluable.chat.METHOD_NAME_PROPERTY
import com.intellij.cce.evaluable.chat.extractCalledInternalApiMethods
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.application.smartReadActionBlocking
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.startOffset

class JavaChatCodeGenerationVisitor : EvaluationVisitor, JavaRecursiveElementVisitor() {
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.JAVA
  override val feature: String = "chat-code-generation"

  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitJavaFile(file: PsiJavaFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitJavaFile(file)
  }

  override fun visitMethod(method: PsiMethod) {
    val internalApiCalls = runBlockingCancellable { extractInternalApiCalls(method) }
    codeFragment?.addChild(
      CodeToken(
        method.text,
        method.startOffset,
        SimpleTokenProperties.create(tokenType = TypeProperty.METHOD, SymbolLocation.PROJECT) {
          put(INTERNAL_API_CALLS_PROPERTY, internalApiCalls.joinToString("\n"))
          put(METHOD_NAME_PROPERTY, method.name)
        })
    )
  }

  private suspend fun extractInternalApiCalls(method: PsiMethod): List<String> {
    return smartReadActionBlocking(method.project) {
      extractCalledInternalApiMethods(method).mapNotNull {
        QualifiedNameProviderUtil.getQualifiedName(it)
      }
    }
  }
}