package org.jetbrains.completion.full.line.java.formatters

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfTypes
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.completion.full.line.language.formatters.PsiCodeFormatterBase

class JavaPsiCodeFormatter : PsiCodeFormatterBase() {

  override fun cutTree(psiFile: PsiFile, position: PsiElement, offset: Int): List<String> {
    val rollbackElement = findRollbackElement(position)

    val preOrder = SyntaxTraverser.psiTraverser(psiFile)
      .preOrderDfsTraversal()
      .toList()
    val index = preOrder.indexOf(rollbackElement)
    if (index == -1) {
      throw IllegalArgumentException("File must contain rollback element")
    }
    preOrder.take(index)
      .filter { shouldSkip(it) }
      .forEach { it.isHidden = true }

    preOrder.drop(index).forEach {
      it.isHidden = true
    }

    val rollbackSuffixRange = TextRange(rollbackElement.startOffset, offset)
    val psiFileText = psiFile.text
    return SyntaxTraverser.psiTraverser(psiFile)
      .onRange(rollbackSuffixRange)
      .filter { !shouldSkip(it) && it.firstChild == null && it.parent !is PsiDocComment }
      .toList()
      .mapNotNull { it.textRange.intersection(rollbackSuffixRange) }
      .filter { !it.isEmpty }
      .map { it.substring(psiFileText) }
  }

  /**
   * Returns first (in dfs order) subtree need to be hidden
   */
  private fun findRollbackElement(position: PsiElement): PsiElement {
    val rollbackScope = findRollbackScope(position)
    val rollbackCandidates = findRollbackCandidates(rollbackScope, position)
    if (rollbackCandidates.isEmpty()) {
      return when (rollbackScope) {
        is PsiCodeBlock -> rollbackScope.firstBodyElement!!
        is PsiClass -> rollbackScope.lBrace!!.nextSibling
        else -> rollbackScope.firstChild
      }
    }
    val errorEndElements = rollbackCandidates
      .takeLastWhile { PsiTreeUtil.hasErrorElements(it) }
      .size
    if (errorEndElements == 0) {
      return rollbackCandidates.last().nextSibling
    }
    return rollbackCandidates[rollbackCandidates.size - errorEndElements]
  }

  private fun findRollbackCandidates(rollbackScope: PsiElement, position: PsiElement): List<PsiElement> =
    PsiTreeUtil.getChildrenOfAnyType(rollbackScope, *rollbackCandidatesTypes)
      .filter {
        val range = it.textRange
        range.endOffset < position.startOffset
      }

  private fun findRollbackScope(position: PsiElement): PsiElement {
    val rollbackScope = position.parentOfTypes(*rollbackScopeTypes)!!
    if (rollbackScope is PsiClass) {
      val lbraceOffset = rollbackScope.lBrace?.startOffset
      if (lbraceOffset == null || lbraceOffset >= position.startOffset) {
        return findRollbackScope(rollbackScope)
      }
    }
    return rollbackScope
  }

  companion object {
    private val rollbackScopeTypes = arrayOf(PsiCodeBlock::class, PsiClass::class, PsiImportList::class, PsiFile::class)
    private val rollbackCandidatesTypes = arrayOf(
      PsiStatement::class.java, PsiMethod::class.java, PsiClass::class.java, PsiPackageStatement::class.java,
      PsiImportList::class.java, PsiImportStatement::class.java, PsiField::class.java
    )
  }

  private fun shouldSkip(it: PsiElement) = it is PsiWhiteSpace ||
                                           (it is PsiComment && it !is PsiDocComment) ||
                                           it.node.toString() == "PsiDocToken:DOC_COMMENT_LEADING_ASTERISKS"
}
