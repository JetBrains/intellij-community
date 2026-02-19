package com.intellij.cce.java.evaluable

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.ExposedApiExtractor
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.application.smartReadActionBlocking
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod

class JavaExposedApiExtractor : ExposedApiExtractor {
  override val language: Language = Language.JAVA

  override suspend fun extractExposedApi(psiFile: PsiFile): List<String> {
    val result = mutableListOf<String>()

    val visitor = object : JavaRecursiveElementVisitor() {
      override fun visitClass(aClass: PsiClass) {
        if (aClass.hasModifier(JvmModifier.PUBLIC)) {
          super.visitClass(aClass)
        }
      }

      override fun visitMethod(method: PsiMethod) {
        if (method.hasModifier(JvmModifier.PUBLIC)) {
          QualifiedNameProviderUtil.getQualifiedName(method)?.let {
            result.add(it)
          }
        }
      }
    }

    smartReadActionBlocking(psiFile.project) {
      psiFile.accept(visitor)
    }

    return result
  }
}