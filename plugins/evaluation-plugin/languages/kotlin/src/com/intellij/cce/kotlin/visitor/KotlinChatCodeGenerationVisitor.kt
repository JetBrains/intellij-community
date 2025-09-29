package com.intellij.cce.kotlin.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.visitor.EvaluationVisitor
import com.intellij.cce.visitor.exceptions.PsiConverterException
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType


class KotlinChatCodeGenerationVisitor : EvaluationVisitor, KtTreeVisitorVoid() {
  override val language: Language
    get() = Language.KOTLIN
  override val feature: String
    get() = "chat-code-generation"

  private var codeFragment: CodeFragment? = null

  override fun visitKtFile(file: KtFile) {
    codeFragment = CodeFragment(file.textOffset, file.textLength)
    super.visitKtFile(file)
  }

  override fun visitNamedFunction(function: KtNamedFunction) {
    codeFragment?.addChild(
      CodeToken(
        function.text,
        function.textOffset,
      )
    )
  }

  private fun extractInternalApiCalls(function: KtNamedFunction): List<String> {
    val internalApiCalls = mutableListOf<String>()
    function.getChildrenOfType<KtCallExpression>().forEach {
      val calledFunction = it.calleeExpression?.mainReference?.resolve()?.asSafely<KtNamedFunction>() ?: return@forEach
      if (isInternalFunction(calledFunction, function)) {
        val qualifiedName = QualifiedNameProviderUtil.getQualifiedName(calledFunction)!!
      }
    }
  }


  private fun isInternalFunction(function: KtNamedFunction, fromWhereCalled: PsiElement): Boolean {
    val containingFile = function.containingFile
    if (containingFile == fromWhereCalled.containingFile) {
      return true
    }
    return ProjectFileIndex.getInstance(function.project).isInContent(function.containingFile.virtualFile)
  }


  override fun getFile(): CodeFragment = codeFragment
                                         ?: throw PsiConverterException("Invoke 'getFile' with visitor on PSI first")


}