// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents

// when a case list is null, it means that the order of cases may be unstable,
// so it is better to rely on keys from the report
data class SwitchCoverageExpression(val expression: String, val cases: List<String>?, val hasDefault: Boolean)
data class ConditionCoverageExpression(val expression: String, val isReversed: Boolean)

internal fun getSwitches(psiFile: PsiFile, range: TextRange): List<SwitchCoverageExpression> {
  val parent = getEnclosingParent(psiFile, range) ?: return emptyList()
  val switchBlocks = mutableListOf<PsiSwitchBlock>()
  parent.accept(object : RangePsiVisitor(range) {
    override fun visitSwitchStatement(statement: PsiSwitchStatement) {
      super.visitSwitchStatement(statement)
      visitSwitch(statement)
    }

    override fun visitSwitchExpression(expression: PsiSwitchExpression) {
      super.visitSwitchExpression(expression)
      visitSwitch(expression)
    }

    private fun visitSwitch(switchBlock: PsiSwitchBlock) {
      if (switchBlock.textOffset in range) {
        switchBlocks.add(switchBlock)
      }
    }
  })
  return switchBlocks.mapNotNull { block ->
    val expression = block.expression?.withoutParentheses()?.text ?: return@mapNotNull null
    val cases = extractCaseLabels(block).mapNotNull { if (it is PsiExpression) it.withoutParentheses() else it }
      // we know for sure that switch by string works correctly in Java
      // is we see at least one string literal, we can keep string labels
      .takeIf { cases -> cases.any { it.children.singleOrNull()?.elementType == JavaTokenType.STRING_LITERAL } }
    SwitchCoverageExpression(expression, cases?.map(PsiElement::getText), hasDefaultLabel(block))
  }
}

internal fun getConditions(psiFile: PsiFile, range: TextRange): List<ConditionCoverageExpression> {
  fun PsiElement.startsInRange() = textOffset in range
  fun PsiElement.startsNotBefore() = textOffset >= range.startOffset

  val enclosingParent = getEnclosingParent(psiFile, range) ?: return emptyList()
  val parent = enclosingParent.parents(withSelf = true).firstOrNull {
    it is PsiStatement || it is PsiMethod || it is PsiClass || it is PsiFile
  } ?: return emptyList()
  val conditionalExpressions = LinkedHashSet<PsiExpression>()
  parent.accept(object : RangePsiVisitor(range) {
    override fun visitElement(element: PsiElement) {
      if (element in conditionalExpressions) return
      super.visitElement(element)
    }

    override fun visitForStatement(statement: PsiForStatement) {
      statement.condition?.takeIf { it.textRange.intersects(range) }?.also { conditionalExpressions.add(it) }
      super.visitForStatement(statement)
    }

    override fun visitWhileStatement(statement: PsiWhileStatement) {
      statement.condition?.takeIf { statement.lParenth?.startsInRange() == true }?.also { conditionalExpressions.add(it) }
      super.visitWhileStatement(statement)
    }

    override fun visitDoWhileStatement(statement: PsiDoWhileStatement) {
      statement.condition?.takeIf { statement.lParenth?.startsInRange() == true }?.also { conditionalExpressions.add(it) }
      super.visitDoWhileStatement(statement)
    }

    override fun visitIfStatement(statement: PsiIfStatement) {
      statement.takeIf(PsiElement::startsInRange)?.condition?.also { conditionalExpressions.add(it) }
      super.visitIfStatement(statement)
    }

    override fun visitForeachStatementBase(statement: PsiForeachStatementBase) {
      statement.iteratedValue?.takeIf(PsiElement::startsInRange)?.also { conditionalExpressions.add(it) }
      super.visitForeachStatementBase(statement)
    }

    override fun visitAssertStatement(statement: PsiAssertStatement) {
      statement.assertCondition?.takeIf(PsiElement::startsInRange)?.also { conditionalExpressions.add(it) }
      super.visitAssertStatement(statement)
    }

    override fun visitPolyadicExpression(expression: PsiPolyadicExpression) {
      if (expression.isBoolOperator() && expression !in conditionalExpressions) {
        val ifParent = expression.parentOfType<PsiIfStatement>()?.takeIf { it.condition == expression }
        val operands = if (ifParent != null) expression.operands.toList()
        // only expression in the left operator creates a branch
        else expression.operands.take(expression.operands.size - 1)
        operands.filter(PsiElement::startsNotBefore).forEach { conditionalExpressions.add(it) }
      }
      super.visitPolyadicExpression(expression)
    }

    override fun visitConditionalExpression(expression: PsiConditionalExpression) {
      expression.condition.takeIf(PsiElement::startsInRange)?.also { conditionalExpressions.add(it) }
      super.visitConditionalExpression(expression)
    }
  })

  return conditionalExpressions
    .flatMap { it.breakIntoConditions(range.startOffset) }
    .map { ConditionCoverageExpression(it.withoutParentheses()!!.text, it.isReversedCondition()) }
}

private open class RangePsiVisitor(private val range: TextRange) : JavaRecursiveElementVisitor() {
  override fun visitElement(element: PsiElement) {
    if (element.textOffset >= range.endOffset) return
    if (element.textOffset + element.textLength <= range.startOffset) return
    super.visitElement(element)
  }
}

private fun PsiPolyadicExpression.isBoolOperator(): Boolean {
  val tokenType = operationTokenType
  return tokenType == JavaTokenType.OROR || tokenType == JavaTokenType.ANDAND
}

private fun PsiExpression.breakIntoConditions(offset: Int): List<PsiExpression> {
  val expression = this.withoutParentheses() ?: return emptyList()
  return if (expression is PsiPolyadicExpression && expression.isBoolOperator()) {
    expression.operands.flatMap { it.breakIntoConditions(offset) }.filter { it.textOffset >= offset }
  }
  else {
    listOf(this)
  }
}

private fun PsiExpression.withoutParentheses(): PsiExpression? {
  var expression = this
  while (expression is PsiParenthesizedExpression) {
    expression = expression.expression ?: return null
  }
  return expression
}

private fun PsiExpression.isReversedCondition(): Boolean {
  val parent = this.parent ?: return false
  return parent is PsiDoWhileStatement
         || parent is PsiAssertStatement
         || parent is PsiPolyadicExpression && parent.operationTokenType == JavaTokenType.OROR && parent.operands.last() !== this
}

private fun getEnclosingParent(psiFile: PsiFile, range: TextRange): PsiElement? {
  val elementAt = psiFile.findElementAt(range.startOffset) ?: return null
  return elementAt.parents(false).firstOrNull { it.textRange.contains(range) }
}

private fun extractCaseLabels(expression: PsiSwitchBlock): List<PsiElement> =
  PsiTreeUtil.getChildrenOfTypeAsList(expression.body, PsiSwitchLabelStatementBase::class.java)
    .flatMap { label: PsiSwitchLabelStatementBase ->
      val list = label.caseLabelElementList
      if (list == null || list.elementCount == 0) return@flatMap emptyList()
      list.elements.toList()
    }

private fun hasDefaultLabel(switchBlock: PsiSwitchBlock): Boolean =
  PsiTreeUtil.getChildrenOfTypeAsList(switchBlock.body, PsiSwitchLabelStatementBase::class.java)
    .any(PsiSwitchLabelStatementBase::isDefaultCase)

