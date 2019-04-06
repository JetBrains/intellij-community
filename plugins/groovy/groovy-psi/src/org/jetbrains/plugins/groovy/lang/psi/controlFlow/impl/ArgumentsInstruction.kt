// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.util.recursionAwareLazy

class ArgumentsInstruction(call: GrCall) : InstructionImpl(call) {

  override fun getElement(): GrCall = super.getElement() as GrCall

  override fun getElementPresentation(): String = "ARGUMENTS " + super.getElementPresentation()

  val variableDescriptors: Collection<VariableDescriptor> get() = arguments.keys

  val arguments: Map<VariableDescriptor, Collection<Argument>> by recursionAwareLazy(this::obtainArguments)

  private fun obtainArguments(): Map<VariableDescriptor, Collection<Argument>> {
    // don't use GrCall#getArguments() because it calls #getType() on GrSpreadArgument operand
    val argumentList = element.argumentList ?: return emptyMap()
    val result = ArrayList<Pair<VariableDescriptor, ExpressionArgument>>()
    for (expression in argumentList.expressionArguments) {
      if (expression !is GrReferenceExpression) continue
      if (expression.isQualified) continue
      val descriptor = expression.createDescriptor() ?: continue
      result += Pair(descriptor, ExpressionArgument(expression))
    }
    if (result.isEmpty()) return emptyMap()
    return result.groupByTo(LinkedHashMap(), { it.first }, { it.second })
  }
}
