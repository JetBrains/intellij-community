// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.kotlin.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.MultiLineVisitorUtils
import com.intellij.cce.visitor.exceptions.PsiConverterException
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid


class KotlinMultiLineEvaluationVisitor : EvaluationVisitor, KtTreeVisitorVoid() {
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.KOTLIN

  override val feature: String = "multi-line-completion"

  override fun getFile(): CodeFragment {
    return codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")
  }

  override fun visitKtFile(file: KtFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength).apply { text = file.text }
    super.visitKtFile(file)
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    val body = function.bodyExpression ?: return
    codeFragment?.let { file ->
      val splits = MultiLineVisitorUtils.splitElementByIndents(body)
      splits.forEach { file.addChild(it) }
    }
  }
}
