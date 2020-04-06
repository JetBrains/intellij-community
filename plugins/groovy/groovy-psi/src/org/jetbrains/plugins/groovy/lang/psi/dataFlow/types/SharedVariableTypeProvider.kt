// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor
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
class SharedVariableTypeProvider(val scope: GrControlFlowOwner) {

  val sharedVariableDescriptors: Set<VariableDescriptor> by lazyPub { doGetSharedVariables() }
  private val writeInstructions: List<Pair<ReadWriteVariableInstruction, GrControlFlowOwner>> by lazyPub {
    getWriteInstructionsFromNestedFlows(scope).filter { it.first.descriptor in sharedVariableDescriptors }
  }
  private val flowTypes: AtomicReferenceArray<PsiType?> = AtomicReferenceArray(writeInstructions.size)
  private val finalTypes: AtomicReferenceArray<PsiType?> = AtomicReferenceArray(sharedVariableDescriptors.size)

  fun getSharedVariableType(descriptor: VariableDescriptor): PsiType? {
    if (descriptor !in sharedVariableDescriptors) {
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
    for (index: Int in writeInstructions.indices) {
      val (instruction: ReadWriteVariableInstruction, scope: GrControlFlowOwner) = writeInstructions[index]
      val currentType: PsiType? = flowTypes.get(index)
      if (currentType == null) {
        val inferredType: PsiType = TypeInferenceHelper.getInferredType(instruction.descriptor, instruction, scope) ?: PsiType.NULL
        if (!flowTypes.compareAndSet(index, null, inferredType)) {
          val actual: PsiType? = flowTypes.get(index)
          assert(inferredType == actual)
          { "Incompatible types detected during shared variable processing: found $actual while inferred $inferredType" }
        }
      }
    }
    for (variable: VariableDescriptor in sharedVariableDescriptors) {
      val indexInFinalTypes: Int = sharedVariableDescriptors.indexOf(variable)
      val inferredTypesForVariable: List<PsiType?> = writeInstructions.indices
        .filter { writeInstructions[it].first.descriptor == variable }
        .map { flowTypes.get(it) }
      val finalType: PsiType? = TypesUtil.getLeastUpperBoundNullable(inferredTypesForVariable, scope.manager)
      if (!finalTypes.compareAndSet(indexInFinalTypes, null, finalType)) {
        val actualFinalType = finalTypes.get(indexInFinalTypes)
        assert(finalType == actualFinalType)
        { "Incompatible types detected during final type computation for shared variable: found $actualFinalType while inferred $finalType" }
      }
    }
  }

  @Suppress("UnnecessaryVariable")
  private fun doGetSharedVariables(): Set<VariableDescriptor> {
    if (!PsiUtil.isCompileStatic(scope)) {
      return emptySet()
    }
    val flow = scope.controlFlow
    val foreignDescriptors: List<String> = flow
      .mapNotNull { it?.element as? GrControlFlowOwner }
      .flatMap { ControlFlowUtils.getForeignVariableIdentifiers(it) { true } }
    val sharedVariables: Set<VariableDescriptor> = flow
      .asSequence()
      .filterIsInstance<ReadWriteVariableInstruction>()
      .mapNotNull { instruction -> instruction.descriptor.takeIf { it.getName() in foreignDescriptors } }
      // fields have their own inference rules
      .filter { if (it is ResolvedVariableDescriptor) it.variable !is GrField else true }
      // do not bother to process variable if it was assigned only once
      .filter { descriptor ->
        mergeInnerFlows(scope)
          .map(Pair<Instruction, GrControlFlowOwner>::first)
          .filterIsInstance<ReadWriteVariableInstruction>()
          .filter(ReadWriteVariableInstruction::isWrite)
          .count { it.descriptor == descriptor } >= 2
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

    internal fun getWriteInstructionsFromNestedFlows(scope: GrControlFlowOwner): List<Pair<ReadWriteVariableInstruction, GrControlFlowOwner>> {
      return mergeInnerFlows(scope)
        .filter { (instruction, _) ->
          instruction is ReadWriteVariableInstruction && instruction.isWrite
        }
        .map { (instruction, scope) -> instruction as ReadWriteVariableInstruction to scope }
    }
  }

}