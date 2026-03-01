package com.intellij.cce.kotlin.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.visitor.RenameVisitorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class KotlinRenameVisitor : RenameVisitorBase(Language.KOTLIN) {
  override fun createPsiVisitor(codeFragment: CodeFragment): PsiElementVisitor {
    return KotlinRenamePsiVisitor(codeFragment)
  }

  private class KotlinRenamePsiVisitor(private val codeFragment: CodeFragment) : KtTreeVisitorVoid() {
    override fun visitElement(element: PsiElement) {
      val parent = element.parent
      when (element.elementType) {
        org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER -> {
          if (parent is KtClass) {
            addToken(element, TypeProperty.CLASS)
          }
          if (parent is KtFunction) {
            addToken(element, TypeProperty.METHOD)
          }
          if (parent is KtProperty) { // same for variable, field and property
            addToken(element, TypeProperty.TOKEN)
          }
          if (parent is KtParameter) {
            val isInProperty = checkParents(parent) {
              it is KtPropertyAccessor
            }
            if (!isInProperty) {
              addToken(element, TypeProperty.PARAMETER)
            }
          }
        }
      }
      super.visitElement(element)
    }

    private fun addToken(element: PsiElement?, tokenType: TypeProperty) {
      if (element != null)  {
        codeFragment.addChild(
          CodeToken(element.text, element.startOffset, SimpleTokenProperties.create(tokenType, SymbolLocation.PROJECT) {})
        )
      }
    }

    private fun checkParents(element: PsiElement, check: (PsiElement) -> Boolean): Boolean {
      element.parents(true).forEach {
        if (check(it)) return true
      }
      return false
    }
  }
}