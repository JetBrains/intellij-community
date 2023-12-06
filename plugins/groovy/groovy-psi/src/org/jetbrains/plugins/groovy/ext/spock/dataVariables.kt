// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.ext.spock.SpockUtils.getNameByReference
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

fun createVariableMap(method: GrMethod): Map<String, SpockVariableDescriptor> {
  return method.block?.let(::createVariableMap) ?: emptyMap()
}

private fun createVariableMap(methodBlock: GrOpenBlock): Map<String, SpockVariableDescriptor> {
  val statements: LinkedList<GrStatement> = LinkedList(methodBlock.statements.asList())
  val whereBlockStart = findWhereBlockStart(statements) ?: return emptyMap()

  val result = HashMap<String, SpockVariableDescriptor>()
  fun consume(variable: SpockVariableDescriptor) = result.set(variable.name, variable)

  extractVariablesFromStatement(whereBlockStart, statements, ::consume)
  while (!statements.isEmpty()) {
    extractVariablesFromStatement(statements.remove(), statements, ::consume)
  }

  return result
}

private fun findWhereBlockStart(statements: Queue<GrStatement>): GrStatement? {
  while (!statements.isEmpty()) {
    val topStatement = statements.remove() as? GrLabeledStatement ?: continue
    return findWhereLabeledStatement(topStatement) ?: continue
  }
  return null
}

private fun extractVariablesFromStatement(current: GrStatement,
                                          statements: Queue<GrStatement>,
                                          consumer: (SpockVariableDescriptor) -> Unit) {
  if (current is GrAssignmentExpression) {
    extractVariableFromAssignment(current)?.let(consumer)
  }
  else if (current is GrBinaryExpression && current.isLeftShift()) {
    extractVariablesFromParameterization(current).forEach(consumer)
  }
  else if (current is GrBinaryExpression && current.isOr()) {
    extractVariablesFromTable(current, statements).forEach(consumer)
  }
  else if (current is GrLabeledStatement && current.name == "and") {
    val labeledStatement = current.statement ?: return
    extractVariablesFromStatement(labeledStatement, statements, consumer)
  }
}

private fun extractVariableFromAssignment(assignment: GrAssignmentExpression): SpockVariableDescriptor? {
  return createVariableDescriptor(assignment.lValue)?.addExpression(assignment.rValue)
}

private fun extractVariablesFromParameterization(parameterization: GrBinaryExpression): Collection<SpockVariableDescriptor> {
  val leftOperand = parameterization.leftOperand
  val rightOperand = parameterization.rightOperand
  when (leftOperand) {
    is GrReferenceExpression -> {
      val variable = createVariableDescriptor(leftOperand)?.addExpressionOfCollection(rightOperand)
      return variable?.let(::listOf) ?: emptyList()
    }
    is GrListOrMap -> {
      val variables = leftOperand.initializers.map(::createVariableDescriptor)
      if (rightOperand is GrListOrMap) {
        addParameterizationInitializers(variables, rightOperand)
      }
      return variables.filterNotNull()
    }
    else -> return emptyList()
  }
}

private fun addParameterizationInitializers(variables: List<SpockVariableDescriptor?>, list: GrListOrMap) {
  for (expression in list.initializers) {
    if (expression is GrListOrMap) {
      addInitializers(variables, expression.initializers.toList())
    }
    else {
      for (variable in variables) {
        variable?.addExpressionOfCollection(expression)
      }
    }
  }
}

private fun extractVariablesFromTable(header: GrBinaryExpression, statements: Queue<GrStatement>): Collection<SpockVariableDescriptor> {
  val variables = extractColumns(header).map(::createVariableDescriptor)
  while (statements.isNotEmpty()) {
    val statement = statements.peek()
    if (addInitializersFromTableRow(variables, statement)) {
      statements.remove()
    }
    else {
      break
    }
  }
  return variables.filterNotNull()
}

private fun addInitializersFromTableRow(variables: List<SpockVariableDescriptor?>, row: GrStatement): Boolean {
  if (row is GrBinaryExpression && row.isOr()) {
    addInitializers(variables, extractColumns(row))
    return true
  }
  else if (row is GrLabeledStatement && row.name == "and") {
    val labeledStatement = row.statement
    return labeledStatement != null && addInitializersFromTableRow(variables, labeledStatement)
  }
  else {
    return false
  }
}

private fun extractColumns(element: GrExpression): List<GrExpression?> {
  val result = ArrayList<GrExpression?>()
  splitOr(result, element)
  return result
}

// See org.spockframework.compiler.WhereBlockRewriter#splitRow()
private fun splitOr(res: MutableList<GrExpression?>, expression: GrExpression?) {
  if (expression is GrBinaryExpression && expression.isOr()) {
    splitOr(res, expression.leftOperand)
    splitOr(res, expression.rightOperand)
  }
  else {
    res.add(expression)
  }
}

private fun createVariableDescriptor(element: PsiElement?): SpockVariableDescriptor? {
  val name = getNameByReference(element) ?: return null
  if (name == "_") return null
  return SpockVariableDescriptor(element, name)
}

private fun addInitializers(variables: List<SpockVariableDescriptor?>, initializers: Iterable<GrExpression?>) {
  for ((variable, initializer) in variables.zip(initializers)) {
    variable?.addExpression(initializer)
  }
}

private fun GrBinaryExpression.isLeftShift(): Boolean {
  return operationTokenType === GroovyElementTypes.LEFT_SHIFT_SIGN
}
