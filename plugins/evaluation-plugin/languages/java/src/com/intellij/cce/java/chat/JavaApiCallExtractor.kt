package com.intellij.cce.java.chat

import com.intellij.cce.core.Language
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.evaluable.METHOD_NAME_PROPERTY
import com.intellij.cce.metric.ApiCallExtractor
import com.intellij.cce.metric.ApiCallExtractorProvider
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadActionBlocking
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.lang.Language as PlatformLanguage

fun interface GeneratedCodeIntegrator {
  suspend fun integrate(project: Project, method: String): String
}

class InEditorGeneratedCodeIntegrator : GeneratedCodeIntegrator {
  override suspend fun integrate(project: Project, method: String): String {
    return readAction {
      val editorManager = FileEditorManager.getInstance(project)
      val editor = editorManager.selectedTextEditor!!
      val text = editor.document.text
      val caret = editor.caretModel.currentCaret.offset
      text.substring(0, caret) + method + "\n" + text.substring(caret)
    }
  }
}

class JavaApiCallExtractor(private val generatedCodeIntegrator: GeneratedCodeIntegrator) : ApiCallExtractor {
  override suspend fun extractApiCalls(code: String, project: Project, tokenProperties: TokenProperties): List<String> {
    val methodName = tokenProperties.additionalProperty(METHOD_NAME_PROPERTY)!!
    val psiFileWithGeneratedCode = writeAction { createPsiFile(code, project, "dummy1.java") }
    val method = extractMethodFromGeneratedSnippet(project, psiFileWithGeneratedCode, methodName) ?: return emptyList()

    val integratedCode = generatedCodeIntegrator.integrate(project, method)
    val psiFileWithIntegratedCode = writeAction { createPsiFile(integratedCode, project, "dummy2.java") }

    return smartReadActionBlocking(project) {
      val method = psiFileWithIntegratedCode.findMethodsByName(methodName).firstOrNull { it.text == method }
                   ?: return@smartReadActionBlocking emptyList()
      extractCalledApiMethods(method).mapNotNull { QualifiedNameProviderUtil.getQualifiedName(it) }
    }
  }

  private fun createPsiFile(code: String, project: Project, name: String): PsiFile {
    return PsiFileFactory.getInstance(project).createFileFromText(
      name,
      PlatformLanguage.findLanguageByID("JAVA")!!,
      code,
    )
  }

  private suspend fun extractMethodFromGeneratedSnippet(
    project: Project,
    psiFileWithGeneratedCode: PsiFile,
    methodName: String,
  ): String? {
    return smartReadActionBlocking(project) { psiFileWithGeneratedCode.findMethodsByName(methodName).firstOrNull()?.text }
  }
}

class JavaApiCallExtractorProvider : ApiCallExtractorProvider {
  override val language: Language = Language.JAVA
  override fun provide(): ApiCallExtractor {
    return JavaApiCallExtractor(InEditorGeneratedCodeIntegrator())
  }
}


private fun PsiFile.findMethodsByName(methodName: String): List<PsiMethod> {
  val foundMethods = mutableListOf<PsiMethod>()

  this.accept(object : JavaRecursiveElementVisitor() {
    override fun visitMethod(method: PsiMethod) {
      if (methodName == method.name) {
        foundMethods.add(method)
      }
      super.visitMethod(method)
    }
  })
  return foundMethods.toList()
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

fun extractCalledApiMethods(psiElement: PsiElement): List<PsiMethod> {
  return extractMethodCallExpressionsFromMethods(psiElement) {
    !isSuperCall(it)
  }.mapNotNull { it.resolveMethod() }
}

private fun isSuperCall(callExpression: PsiCallExpression): Boolean {
  return (callExpression is PsiMethodCallExpression)
         && (callExpression.methodExpression.qualifierExpression is PsiSuperExpression)
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
