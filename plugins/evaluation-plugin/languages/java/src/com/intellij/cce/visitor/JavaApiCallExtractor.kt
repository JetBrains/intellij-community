package com.intellij.cce.visitor

import com.intellij.cce.metric.ApiCallExtractor
import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.lang.Language
import com.intellij.openapi.application.smartReadActionBlocking
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*

fun extractCallExpressions(
  psiElement: PsiElement,
  filter: (PsiCallExpression) -> Boolean = { true },
): List<PsiCallExpression> {
  val result: MutableList<PsiCallExpression> = mutableListOf()
  val visitor = object : JavaRecursiveElementVisitor() {
    override fun visitCallExpression(callExpression: PsiCallExpression) {
      if (!filter(callExpression)) return
      result.add(callExpression)
      super.visitCallExpression(callExpression)
    }
  }
  psiElement.accept(visitor)
  return result
}

fun PsiElement.getQualifiedName(): String? {
  QualifiedNameProvider.EP_NAME.extensionList.forEach { provider ->
    val qualifiedName = provider.getQualifiedName(this)
    if (qualifiedName != null) return qualifiedName
  }
  return null
}


class JavaApiCallExtractor : ApiCallExtractor {
  override val language = Language.findLanguageByID("JAVA")!!

  override suspend fun extractForGeneratedCode(code: String, project: Project): List<String> {
    val psiFile = writeAction { createPsiFile(code, project) }
    return smartReadActionBlocking(project) {
      val callExpressions = extractCallExpressions(psiFile)
      callExpressions.mapNotNull { it.resolveMethod()?.getQualifiedName() }
    }
  }

  private fun createPsiFile(code: String, project: Project): PsiFile {
    return PsiFileFactory.getInstance(project).createFileFromText(
      "dummy.java",
      language,
      code
    )
  }
}