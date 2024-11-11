package com.intellij.cce.kotlin.visitor

import com.intellij.cce.core.*
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class KotlinCompletionContextEvaluationVisitor: EvaluationVisitor, KtTreeVisitorVoid() {
  private var codeFragment: CodeFragment? = null

  override val language: Language = Language.KOTLIN

  override val feature: String = "completion-context"

  override fun getFile(): CodeFragment {
    return codeFragment ?: throw PsiConverterException("Invoke 'accept' with visitor on PSI first")
  }

  override fun visitKtFile(file: KtFile) {
    codeFragment = CodeFragmentWithPsi(
      offset = file.textOffset,
      length = file.textLength,
      element = file
    ).apply { text = file.text }
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
  }
}

private val BODY = SimpleTokenProperties.create(TypeProperty.UNKNOWN, SymbolLocation.UNKNOWN) {}
