package com.intellij.cce.python.chat

import com.intellij.cce.core.Language
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.metric.ApiCallExtractor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyRecursiveElementVisitor
import com.intellij.lang.Language as PlatformLanguage

class PythonApiCallExtractor : ApiCallExtractor {
  override val language: Language = Language.PYTHON

  override suspend fun extractApiCalls(code: String, project: Project, tokenProperties: TokenProperties): List<String> {
    val psiFile = writeAction { parsePsiFile(project, code) }
    return readAction { extractApiCalls(psiFile) }
  }


  private fun parsePsiFile(project: Project, code: String): PsiFile {
    return PsiFileFactory.getInstance(project)!!.createFileFromText(
      "temp.py", PlatformLanguage.findLanguageByID("Python")!!, code
    )
  }

  private fun extractApiCalls(psiFile: PsiFile): List<String> {
    val callExpressions = mutableListOf<PyCallExpression>()
    val visitor = object : PyRecursiveElementVisitor() {
      override fun visitPyCallExpression(node: PyCallExpression) {
        callExpressions.add(node)
        super.visitPyCallExpression(node)
      }
    }
    psiFile.accept(visitor)
    return callExpressions.mapNotNull { it.callee?.name }
  }
}