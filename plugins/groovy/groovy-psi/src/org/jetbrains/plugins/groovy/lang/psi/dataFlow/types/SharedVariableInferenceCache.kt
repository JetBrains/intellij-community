// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.getControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.isNestedFlowProcessingAllowed
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * A variable is considered `shared` if it satisfies the following conditions:
 * 1) It is under @CompileStatic.
 * 2) It is local variable.
 * 3) It has multiple assignments.
 * 4) There exists an inner closure/lambda where this variable is used.
 *
 * Type of shared variable is a LUB of all its initializer types.
 *
 * Static compiler in Groovy performs two phases of type checking:
 * First pass: type of every variable determined according to the rules of flow typing.
 * Second pass: type of every shared variable is recalculated using flow typing results.
 */
class SharedVariableInferenceCache(val scope: GrControlFlowOwner) {

  val sharedVariableDescriptors: Set<VariableDescriptor>
  private val writeInstructions: List<Pair<ReadWriteVariableInstruction, GrControlFlowOwner>>

  init {
    val mergedInnerFlows: List<Pair<ReadWriteVariableInstruction, GrControlFlowOwner>> =
      mergeInnerFlows(scope)
        .mapNotNull { (instruction, owner) ->
          (instruction as? ReadWriteVariableInstruction)?.takeIf { instruction.isWrite }?.run { instruction to owner }
        }
    sharedVariableDescriptors = doGetSharedVariables(mergedInnerFlows.map(Pair<ReadWriteVariableInstruction, GrControlFlowOwner>::first))
    writeInstructions = mergedInnerFlows.filter { pair -> pair.first.descriptor in sharedVariableDescriptors }
  }

  private val finalTypes: AtomicReferenceArray<PsiType?> = AtomicReferenceArray(sharedVariableDescriptors.size)

  fun getSharedVariableType(descriptor: VariableDescriptor): PsiType? {
    if (descriptor !in sharedVariableDescriptors || !isNestedFlowProcessingAllowed()) {
      return null
    }
    val indexInFinalTypes: Int = sharedVariableDescriptors.indexOf(descriptor)
    val finalType = finalTypes.get(indexInFinalTypes)
    if (finalType == null) {
      runSharedVariableInferencePhase()
      return finalTypes.get(indexInFinalTypes)
    }
    else {
      return finalType
    }
  }


  /**
   * Sequentially tries to compute all intermediate types for every shared variable.
   * The result type of a shared variable is considered to be a LUB of all its intermediate types.
   *
   * This method is not supposed to be reentrant.
   * If DFA would have to query the type of shared variable, then DFA will be self-invoked with a nested context.
   * Such behavior is consistent with @CompileStatic approach: all variables inside DFA should see the type that was achieved via flow typing.
   * @see DFAFlowInfo.dependentOnSharedVariables
   */
  private fun runSharedVariableInferencePhase() {
    val flowTypes: Array<PsiType?> = Array(writeInstructions.size) { null }
    for (index: Int in writeInstructions.indices) {
      val (instruction: ReadWriteVariableInstruction, scope : GrControlFlowOwner) = writeInstructions[index]
      val inferenceCache = TypeInferenceHelper.getInferenceCache(scope)
      val inferredType: PsiType? = inferenceCache.getInferredType(instruction.descriptor, instruction, false) ?: PsiType.NULL
      flowTypes[index] = inferredType
    }
    for (variable: VariableDescriptor in sharedVariableDescriptors) {
      val indexInFinalTypes: Int = sharedVariableDescriptors.indexOf(variable)
      val inferredTypesForVariable: List<PsiType?> = writeInstructions
        .mapIndexedNotNull { index, pair -> flowTypes[index]?.takeIf { pair.first.descriptor == variable } }
      val finalType: PsiType = TypesUtil.getLeastUpperBoundNullable(inferredTypesForVariable, scope.manager) ?: PsiType.NULL
      if (!finalTypes.compareAndSet(indexInFinalTypes, null, finalType)) {
        val actualFinalType = finalTypes.get(indexInFinalTypes)
        assert(finalType == actualFinalType)
        { "Incompatible types detected during final type computation for shared variable: found $actualFinalType while inferred $finalType" }
      }
    }
  }

  @Suppress("UnnecessaryVariable")
  private fun doGetSharedVariables(mergedInnerFlows: List<ReadWriteVariableInstruction>): Set<VariableDescriptor> {
    if (!PsiUtil.isCompileStatic(scope)) {
      return emptySet()
    }
    val flow = scope.controlFlow
    val foreignDescriptors: List<ResolvedVariableDescriptor> = flow
      .mapNotNull { (it?.element as? GrFunctionalExpression)?.getControlFlowOwner() }
      .flatMap { ControlFlowUtils.getForeignVariableDescriptors(it) { true } }
    val sharedVariables: Set<VariableDescriptor> = flow
      .asSequence()
      .filterIsInstance<ReadWriteVariableInstruction>()
      .mapNotNull { instruction -> instruction.descriptor.takeIf { it in foreignDescriptors } as? ResolvedVariableDescriptor }
      // fields have their own inference rules
      .filter { it.variable !is GrField }
      // do not bother to process variable if it was assigned only once
      .filter { descriptor ->
        mergedInnerFlows.count { it.descriptor == descriptor } >= 2
      }
      .toSet()
    return sharedVariables
  }

  companion object {
    private fun mergeInnerFlows(owner: GrControlFlowOwner): List<Pair<Instruction, GrControlFlowOwner>> {
      return owner.controlFlow.flatMap {
        val element = it.element
        if (it !is ReadWriteVariableInstruction && element is GrControlFlowOwner) {
          mergeInnerFlows(element)
        }
        else {
          listOf<Pair<Instruction, GrControlFlowOwner>>(it to owner)
        }
      }
    }
  }

}