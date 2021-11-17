// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.*
import com.intellij.psi.util.CachedValueProvider.Result
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
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
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isExpressionStatement

private typealias VariablesKey = Key<CachedValue<Object2IntMap<VariableDescriptor>>>
private val smallFlowKey: VariablesKey = Key.create("groovy.dfa.small.flow.var.indexes")
private val largeFlowKey: VariablesKey = Key.create("groovy.dfa.large.flow.var.indexes")


internal fun GrControlFlowOwner.getVarIndexes(large : Boolean): Object2IntMap<VariableDescriptor> {
  return CachedValuesManager.getCachedValue(this, if (large) largeFlowKey else smallFlowKey) {
    Result.create(doGetVarIndexes(this, large), PsiModificationTracker.MODIFICATION_COUNT)
  }
}

private fun doGetVarIndexes(owner: GrControlFlowOwner, isLarge : Boolean): Object2IntMap<VariableDescriptor> {
  val result = Object2IntOpenHashMap<VariableDescriptor>()
  var num = 1
  val flow = if (isLarge) TypeInferenceHelper.getLargeControlFlow(owner) else owner.controlFlow
  for (instruction in flow) {
    if (instruction !is ReadWriteVariableInstruction) continue
    val descriptor = instruction.descriptor
    if (!result.containsKey(descriptor)) {
      result.put(descriptor, num++)
    }
  }
  return result
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
