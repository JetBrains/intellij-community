package com.intellij.cce.java.visitor

import com.intellij.cce.core.*
import com.intellij.cce.evaluable.METHOD_BODY_PROPERTY
import com.intellij.cce.evaluable.METHOD_NAME_PROPERTY
import com.intellij.cce.visitor.SandboxFeaturesVisitorBase
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.startOffset

class JavaSandboxFeatureVisitor : SandboxFeaturesVisitorBase(Language.JAVA) {
  override fun createPsiVisitor(codeFragment: CodeFragment): PsiElementVisitor {
    return JavaSandboxFeaturePsiVisitor(codeFragment)
  }

  private class JavaSandboxFeaturePsiVisitor(private val codeFragment: CodeFragment): JavaRecursiveElementVisitor() {
    override fun visitMethod(method: PsiMethod) {
      val token = createCodeToken(method, TypeProperty.METHOD)
      if (token != null)
        codeFragment.addChild(token)
      super.visitMethod(method)
    }

    private fun createCodeToken(method: PsiMethod, tokenType: TypeProperty): CodeToken? {
      val name = method.name
      val body = method.body
      if (body != null) {
        val meaningfullBodyChildren = body.trim()
        if (meaningfullBodyChildren.any()) {
          val meaningfullBodyText = meaningfullBodyChildren.map { it.text }.joinToString("")

          val props = SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {
            put(METHOD_NAME_PROPERTY, name)
            put(METHOD_BODY_PROPERTY, meaningfullBodyText)
          }

          return CodeToken(name, method.textOffset, props)
        }
      }

      return null
    }

    private fun PsiCodeBlock.trim(): List<PsiElement> {
      val firstIndex = children.indexOfFirst { it.isMeaningful()}
      val lastIndex = children.indexOfLast { it.isMeaningful() }
      val indexRange = (firstIndex.. lastIndex)
      return children.filterIndexed { index, it ->
        it is PsiExpressionStatement
        index in indexRange
      }
    }

    private fun PsiElement.isMeaningful(): Boolean {
      if (this is PsiWhiteSpace) {
        return false
      }
      if (this is PsiJavaToken) {
        return tokenType != JavaTokenType.LBRACE && tokenType != JavaTokenType.RBRACE
      }
      return true
    }
  }
}