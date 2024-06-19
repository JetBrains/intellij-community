// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.exceptions.PsiConverterException
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinCodeGenerationVisitor : EvaluationVisitor, KtTreeVisitorVoid() {
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.KOTLIN
  override val feature: String = "code-generation"
  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")

  override fun visitKtFile(file: KtFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply { text = file.text }
    super.visitKtFile(file)
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    val body = function.bodyExpression ?: return
    codeFragment?.let { file ->
      if (body is KtBlockExpression && body.children.isNotEmpty()) {
        val startOffset = body.children.first().startOffset
        val endOffset = body.children.last().endOffset
        val text = file.text.substring(startOffset, endOffset)
        file.addChild(CodeTokenWithPsi(text, startOffset, BODY, element = body))
      } else {
        file.addChild(CodeTokenWithPsi(body.text, body.textOffset, BODY, element = body))
      }
    }
  }}


private val BODY = SimpleTokenProperties.create(TypeProperty.UNKNOWN, SymbolLocation.UNKNOWN) {}
