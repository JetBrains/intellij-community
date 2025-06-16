package com.intellij.cce.java.chat

import com.intellij.cce.core.Language
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.evaluable.METHOD_NAME_PROPERTY
import com.intellij.cce.metric.ApiCallExtractor
import com.intellij.cce.metric.ApiCallExtractorProvider
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadActionBlocking
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import java.util.*
import com.intellij.lang.Language as PlatformLanguage

fun interface GeneratedCodeIntegrator {
  suspend fun integrate(project: Project, method: String, imports: List<String>): String
}

class InEditorGeneratedCodeIntegrator : GeneratedCodeIntegrator {
  override suspend fun integrate(
    project: Project,
    method: String,
    imports: List<String>,
  ): String {
    return readAction {
      val editorManager = FileEditorManager.getInstance(project)
      val editor = editorManager.selectedTextEditor!!
      val text = editor.document.text
      val caret = editor.caretModel.currentCaret.offset

      val packageEnd = text.indexOf("package").let { if (it >= 0) text.indexOf('\n', it) + 1 else 0 }
      val importSection = if (imports.isNotEmpty()) imports.joinToString("\n") + "\n\n" else ""

      text.substring(0, packageEnd) + importSection + text.substring(packageEnd, caret) + method + "\n" + text.substring(caret)
    }
  }
}

class JavaApiCallExtractor(private val generatedCodeIntegrator: GeneratedCodeIntegrator) : ApiCallExtractor {
  override suspend fun extractApiCalls(code: String, allCodeSnippets: List<String>, project: Project, tokenProperties: TokenProperties): List<String> {
    val method = pasteGeneratedCode(code, allCodeSnippets, project, tokenProperties) ?: return emptyList()
    return smartReadActionBlocking(project) {
      extractCalledInternalApiMethods(method).mapNotNull { QualifiedNameProviderUtil.getQualifiedName(it) }
    }
  }

  override suspend fun extractExternalApiCalls(code: String, allCodeSnippets: List<String>, project: Project, tokenProperties: TokenProperties): List<String> {
    val method = pasteGeneratedCode(code, allCodeSnippets, project, tokenProperties) ?: return emptyList()
    return smartReadActionBlocking(project) { extractCalledExternalApiMethodsQualifiedNames(method) }
  }

  private suspend fun pasteGeneratedCode(code: String, allCodeSnippets: List<String>, project: Project, tokenProperties: TokenProperties): PsiElement? {
    val methodName = tokenProperties.additionalProperty(METHOD_NAME_PROPERTY) ?: return null
    val psiFileWithGeneratedCode = edtWriteAction { createPsiFile(code, project, "dummy1.java") }
    val method = extractMethodFromGeneratedSnippet(project, psiFileWithGeneratedCode, methodName) ?: return null

    val imports = allCodeSnippets.flatMap {
      val tempPsifileWithImports = edtWriteAction { createPsiFile(it, project, UUID.randomUUID().toString() + ".java") }
      extractImportsFromGeneratedSnippet(project, tempPsifileWithImports)
    }
    val integratedCode = generatedCodeIntegrator.integrate(project, method, imports)
    val psiFileWithIntegratedCode = edtWriteAction { createPsiFile(integratedCode, project, "dummy2.java") }

    return smartReadActionBlocking(project) {
      psiFileWithIntegratedCode.findMethodsByName(methodName).firstOrNull { it.text == method }
    }
  }


  private fun createPsiFile(code: String, project: Project, name: String): PsiFile {
    return PsiFileFactory.getInstance(project).createFileFromText(
      name,
      PlatformLanguage.findLanguageByID("JAVA")!!,
      code,
    )
  }

  private suspend fun extractImportsFromGeneratedSnippet(project: Project, psiFileWithGeneratedCode: PsiFile): List<String> {
    return smartReadActionBlocking(project) {
      val importList = psiFileWithGeneratedCode.children.filterIsInstance<PsiImportList>().firstOrNull()
      importList?.children?.filterIsInstance<PsiImportStatement>()?.mapNotNull { it.text } ?: emptyList()
    }
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

fun extractCalledExternalApiMethodsQualifiedNames(psiElement: PsiElement): List<String> {
  val externalApiMethodsQualifiedNames = mutableListOf<String>()
  extractMethodCallExpressionsFromMethods(psiElement).forEach {
    val psiMethodCall = (it as? PsiMethodCallExpression) ?: return@forEach
    val referenceName = psiMethodCall.methodExpression.referenceName ?: return@forEach
    val method = it.resolveMethod()
    if (method != null && (isInternalApiMethod(method, psiElement) ||
                           isFromStandardLibrary(method))) {
      return@forEach
    }
    externalApiMethodsQualifiedNames.add(referenceName)
  }
  return externalApiMethodsQualifiedNames.toList()
}

fun isFromStandardLibrary(method: PsiMethod): Boolean {
  val containingClass = method.containingClass ?: return false
  val qualifiedName = containingClass.qualifiedName ?: return false
  return qualifiedName.startsWith("java.") ||
         qualifiedName.startsWith("javax.") ||
         qualifiedName.startsWith("sun.") ||
         qualifiedName.startsWith("com.sun.") ||
         qualifiedName.startsWith("jdk.") ||
         qualifiedName.startsWith("org.w3c.dom") ||
         qualifiedName.startsWith("org.xml.sax") ||
         qualifiedName.startsWith("org.ietf.jgss") ||
         qualifiedName.startsWith("org.omg") ||
         qualifiedName.startsWith("netscape.javascript")
}

fun extractCalledInternalApiMethods(psiElement: PsiElement): List<PsiMethod> {
  val apiMethods = extractMethodCallExpressionsFromMethods(psiElement).mapNotNull { it.resolveMethod() }
  return apiMethods.filter { isInternalApiMethod(it, psiElement) }
}

private fun isInternalApiMethod(method: PsiMethod, fromWhereCalled: PsiElement): Boolean {
  if (isInTheSameFile(method, fromWhereCalled)) return true
  val project = method.project
  val containingFile = method.containingFile?.virtualFile ?: return false
  val projectFileIndex = ProjectFileIndex.getInstance(project)
  return projectFileIndex.isInContent(containingFile)
}

private fun isInTheSameFile(method: PsiMethod, fromWhereCalled: PsiElement): Boolean {
  val containingFile = method.containingFile
  return containingFile != null && containingFile == fromWhereCalled.containingFile
}


private fun extractMethodCallExpressionsFromMethods(
  psiElement: PsiElement,
): List<PsiCallExpression> {
  val result: MutableList<PsiCallExpression> = mutableListOf()
  val visitor = object : JavaRecursiveElementVisitor() {
    override fun visitMethod(method: PsiMethod) {
      if (method.isConstructor) return
      super.visitMethod(method)
    }

    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
      if (isSuperCall(expression)) return
      result.add(expression)
      super.visitMethodCallExpression(expression)
    }
  }
  psiElement.accept(visitor)
  return result
}

private fun isSuperCall(callExpression: PsiCallExpression): Boolean {
  return (callExpression is PsiMethodCallExpression)
         && (callExpression.methodExpression.qualifierExpression is PsiSuperExpression)
}