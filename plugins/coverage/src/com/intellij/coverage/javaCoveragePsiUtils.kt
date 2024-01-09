// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents

internal data class SwitchExpression(val expression: String, val cases: List<String>, val hasDefault: Boolean)
internal data class ConditionExpression(val expression: String, val isReversed: Boolean)

internal fun getSwitches(psiFile: PsiFile, range: TextRange): List<SwitchExpression> {
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
    val cases = extractCaseLabels(block).mapNotNull { if (it is PsiExpression) it.withoutParentheses() else it }.map(PsiElement::getText)
    SwitchExpression(expression, cases, hasDefaultLabel(block))
  }
}

internal fun getConditions(psiFile: PsiFile, range: TextRange): List<ConditionExpression> {
  val parent = getEnclosingParent(psiFile, range) ?: return emptyList()
  fun PsiElement.startsInRange() = textOffset in range

  val conditionalExpressions = LinkedHashSet<PsiExpression>()
  parent.accept(object : RangePsiVisitor(range) {
    override fun visitElement(element: PsiElement) {
      if (element in conditionalExpressions) return
      if (element is PsiConditionalLoopStatement) {
        val condition = element.condition
        if (condition != null && condition.startsInRange()) {
          conditionalExpressions.add(condition)
        }
      }
      super.visitElement(element)
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
        val operands = expression.operands
        conditionalExpressions.addAll(operands.take(operands.size - 1)) // only expression in the left operator creates a branch
      }
      super.visitPolyadicExpression(expression)
    }

    override fun visitConditionalExpression(expression: PsiConditionalExpression) {
      expression.condition.takeIf(PsiElement::startsInRange)?.also { conditionalExpressions.add(it) }
      super.visitConditionalExpression(expression)
    }
  })

  return conditionalExpressions
    .flatMap(PsiExpression::breakIntoConditions)
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

private fun PsiExpression.breakIntoConditions(): List<ConditionExpression> {
  val expression = this.withoutParentheses() ?: return emptyList()
  if (expression is PsiPolyadicExpression && expression.isBoolOperator()) {
    return expression.operands.flatMap { it.breakIntoConditions() }
  }
  else {
    return listOf(ConditionExpression(expression.text, this.isReversedCondition()))
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

