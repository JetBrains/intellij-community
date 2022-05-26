// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isExpressionStatement
import org.jetbrains.plugins.groovy.util.findNodesOutsideCycles
import org.jetbrains.plugins.groovy.util.mapGraph
import java.util.*

internal fun getSimpleInstructions(flow: Array<Instruction>): BitSet =
  findNodesOutsideCycles(mapGraph(flow.associateWith { it.allSuccessors().toList() })).fold(BitSet()) { bitSet, instr ->
    bitSet.set(instr.num()); bitSet
  }

private typealias InstructionsByElement = (PsiElement) -> Collection<Instruction>
private typealias ReadInstructions = Collection<ReadWriteVariableInstruction>
private val READS = Key<ReadInstructions>("groovy.dfa.read.instructon.in.scope")

internal fun findReadDependencies(writeInstruction: Instruction, instructionsByElement: InstructionsByElement): ReadInstructions {
  require(
    writeInstruction is ReadWriteVariableInstruction && writeInstruction.isWrite ||
    writeInstruction is MixinTypeInstruction ||
    writeInstruction is ArgumentsInstruction
  )
  val element = writeInstruction.element ?: return emptyList()
  val scope = findDependencyScope(element) ?: return emptyList()
  return findReadsInsideCacheable(scope, instructionsByElement)
}

private fun findDependencyScope(element: PsiElement): PsiElement? {
  if (element is GrVariable) {
    val parent = element.parent
    if (parent is GrVariableDeclaration && parent.isTuple) {
      return parent
    }
  }
  if (element is GrParameter && element.parent?.parent is GrFunctionalExpression) {
    val funExpr = element.parent.parent as GrFunctionalExpression
    val enclosingCall = funExpr.parentOfType<GrMethodCall>()?.takeIf { it.closureArguments.any { it === funExpr } }
    if (enclosingCall != null) {
      return enclosingCall
    }
  }
  val lValue = if (element.parent is GrTuple) element.parent else element
  return PsiTreeUtil.findFirstParent(lValue) {
    (it.parent !is GrExpression || it is GrMethodCallExpression || it is GrBinaryExpression || it is GrInstanceOfExpression || isExpressionStatement(it))
  }
}

private fun findReadsInsideCacheable(scope : PsiElement, instructionsByElement: InstructionsByElement): ReadInstructions {
  val existing = scope.getUserData(READS)
  if (existing == null) {
    val newReads = findReadsInside(scope, instructionsByElement)
    scope.putUserData(READS, newReads)
    return newReads
  } else {
    return existing
  }
}

private fun findReadsInside(scope: PsiElement, instructionsByElement: InstructionsByElement): ReadInstructions {
  if (scope is GrForInClause) {
    val expression = scope.iteratedExpression ?: return emptyList()
    return findReadsInside(expression, instructionsByElement)
  }
  val result = ArrayList<ReadWriteVariableInstruction>()
  scope.accept(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement) {
      if (element is GrReferenceExpression && !element.isQualified || element is GrParameter && element.parent is GrForInClause) {
        val instructions = instructionsByElement(element)
        for (instruction in instructions) {
          if (instruction !is ReadWriteVariableInstruction || instruction.isWrite) continue
          result += instruction
        }
      }
      if (element is GrClosableBlock) {
        return
      }
      super.visitElement(element)
    }
  })
  return result
}
