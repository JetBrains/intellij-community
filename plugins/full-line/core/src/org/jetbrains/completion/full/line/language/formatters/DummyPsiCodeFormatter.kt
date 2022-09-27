package org.jetbrains.completion.full.line.language.formatters

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class DummyPsiCodeFormatter : PsiCodeFormatterBase() {
  override fun cutTree(psiFile: PsiFile, position: PsiElement, offset: Int): List<String> = emptyList()
}
