package com.intellij.cce.evaluable.chat

import com.intellij.cce.core.Language
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.metric.ApiCallExtractor
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.application.smartReadActionBlocking
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.lang.Language as PlatformLanguage


class JavaApiCallExtractor : ApiCallExtractor {
  override val language = Language.JAVA

  companion object {
    private const val PLATFORM_LANG_ID = "JAVA"
  }

  override suspend fun extractApiCalls(code: String, project: Project, tokenProperties: TokenProperties): List<String> {
    val psiFile = writeAction { createPsiFile(code, project) }
    val methodNameThatMustBeGenerated = extractMethodNameThatMustBeGenerated(tokenProperties)
    return smartReadActionBlocking(project) {
      val method = findMethodWhichMustBeGenerated(psiFile, methodNameThatMustBeGenerated) ?: return@smartReadActionBlocking emptyList()
      extractCalledApiMethods(method).mapNotNull { QualifiedNameProviderUtil.getQualifiedName(it) }
    }
  }

  private fun extractMethodNameThatMustBeGenerated(tokenProperties: TokenProperties): String {
    return tokenProperties.additionalProperty(METHOD_NAME_PROPERTY)!!
  }

  private fun createPsiFile(code: String, project: Project): PsiFile {
    return PsiFileFactory.getInstance(project).createFileFromText(
      "dummy.java",
      PlatformLanguage.findLanguageByID(PLATFORM_LANG_ID)!!,
      code,
    )
  }

  private fun findMethodWhichMustBeGenerated(psiFile: PsiFile, methodName: String): PsiMethod? {
    var foundMethod: PsiMethod? = null
    psiFile.accept(object : JavaRecursiveElementVisitor() {
      override fun visitMethod(method: PsiMethod) {
        if (foundMethod != null) return
        if (methodName == method.name) {
          foundMethod = method
          return
        }
        super.visitMethod(method)
      }
    })
    return foundMethod
  }
}

fun extractCalledApiMethods(psiElement: PsiElement): List<PsiMethod> {
  return extractMethodCallExpressionsFromMethods(psiElement) {
    !isSuperCall(it)
  }.mapNotNull { it.resolveMethod() }
}

private fun isSuperCall(callExpression: PsiCallExpression): Boolean {
  return (callExpression is PsiMethodCallExpression)
         && (callExpression.methodExpression.qualifierExpression is PsiSuperExpression)
}

fun extractCalledInternalApiMethods(psiElement: PsiElement): List<PsiMethod> {
  val apiMethods = extractCalledApiMethods(psiElement)
  return apiMethods.filter { isInternalApiMethod(it) }
}

private fun isInternalApiMethod(method: PsiMethod): Boolean {
  val project = method.project
  val containingFile = method.containingFile?.virtualFile ?: return false
  val projectFileIndex = ProjectFileIndex.getInstance(project)
  return projectFileIndex.isInContent(containingFile)
}

private fun extractMethodCallExpressionsFromMethods(
  psiElement: PsiElement,
  filter: (PsiCallExpression) -> Boolean = { true },
): List<PsiCallExpression> {
  val result: MutableList<PsiCallExpression> = mutableListOf()
  val visitor = object : JavaRecursiveElementVisitor() {
    override fun visitMethod(method: PsiMethod) {
      if (method.isConstructor) return
      super.visitMethod(method)
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
      if (!filter(expression)) return
      result.add(expression)
      super.visitMethodCallExpression(expression)
    }
  }
  psiElement.accept(visitor)
  return result
}