// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isExpressionStatement

internal fun GrControlFlowOwner.getVarIndexes(): Object2IntMap<VariableDescriptor> {
  return CachedValuesManager.getCachedValue(this) {
    Result.create(doGetVarIndexes(this), PsiModificationTracker.MODIFICATION_COUNT)
  }
}

private fun doGetVarIndexes(owner: GrControlFlowOwner): Object2IntMap<VariableDescriptor> {
  val result = Object2IntOpenHashMap<VariableDescriptor>()
  var num = 1
  for (instruction in owner.controlFlow) {
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

internal fun findReadDependencies(writeInstruction: Instruction, instructionsByElement: InstructionsByElement): ReadInstructions {
  require(
    writeInstruction is ReadWriteVariableInstruction && writeInstruction.isWrite ||
    writeInstruction is MixinTypeInstruction ||
    writeInstruction is ArgumentsInstruction
  )
  val element = writeInstruction.element ?: return emptyList()
  val scope = findDependencyScope(element) ?: return emptyList()
  return findReadsInside(scope, instructionsByElement)
}

private fun findDependencyScope(element: PsiElement): PsiElement? {
  if (element is GrVariable) {
    val parent = element.parent
    if (parent is GrVariableDeclaration && parent.isTuple) {
      return parent
    }
  }
  val lValue = if (element.parent is GrTuple) element.parent else element
  return PsiTreeUtil.findFirstParent(lValue) {
    (it.parent !is GrExpression || it is GrMethodCallExpression || it is GrBinaryExpression || it is GrInstanceOfExpression || isExpressionStatement(it))
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
      super.visitElement(element)
    }
  })
  return result
}
