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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReferenceArray

class SharedVariableTypeProvider(val scope: GrControlFlowOwner) {

  private val sharedVariables: List<VariableDescriptor> by lazyPub { doGetSharedVariables() }
  private val writeInstructions: List<ReadWriteVariableInstruction> by lazyPub {
    scope.controlFlow
      .filter { it is ReadWriteVariableInstruction && it.isWrite && it.descriptor in sharedVariables }
      .map { it as ReadWriteVariableInstruction }
  }
  private val incrementalTypes: AtomicReferenceArray<PsiType?> = AtomicReferenceArray(scope.controlFlow.size)
  private val finalTypes: AtomicReferenceArray<PsiType?> = AtomicReferenceArray(sharedVariables.size)
  private val finishedInference: AtomicBoolean = AtomicBoolean(false)
  private val isInInferenceMode: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

  fun getSharedVariableType(variable: VariableDescriptor, instruction: Instruction): PsiType? {
    if (isInInferenceMode.get()) {
      val num: Int = findNearestWriteInstruction(variable, instruction) ?: return null
      return incrementalTypes.get(num)
             ?: throw AssertionError("Type for ${variable.getName()} should be computed before entering inference mode")
    }
    else if (sharedVariables.isNotEmpty()) {
      runSharedVariableInferencePhase()
    }
    if (variable in sharedVariables) {
      val indexInFinalTypes: Int = sharedVariables.indexOf(variable)
      return finalTypes.get(indexInFinalTypes)
    }
    else {
      return null
    }
  }

  private fun runSharedVariableInferencePhase() {
    if (finishedInference.get()) {
      return
    }
    isInInferenceMode.set(true)
    for (instruction: ReadWriteVariableInstruction in writeInstructions) {
      val currentType = incrementalTypes.get(instruction.num())
      if (currentType == null) {
        val inferredType: PsiType? = TypeInferenceHelper.getInferredType(instruction.descriptor, instruction, scope)
        if (!incrementalTypes.compareAndSet(instruction.num(), null, inferredType)) {
          val actual = incrementalTypes.get(instruction.num())
          assert(inferredType == actual)
          { "Incompatible types detected during shared variable processing: found $actual while inferred $inferredType" }
        }
      }
    }
    isInInferenceMode.set(false)
    for (variable in sharedVariables) {
      val indexInFinalTypes: Int = sharedVariables.indexOf(variable)
      val inferredTypesForVariable = writeInstructions.filter { it.descriptor == variable }.map { incrementalTypes.get(it.num()) }
      val finalType = TypesUtil.getLeastUpperBoundNullable(inferredTypesForVariable, scope.manager)
      if (!finalTypes.compareAndSet(indexInFinalTypes, null, finalType)) {
        val actualFinalType = finalTypes.get(indexInFinalTypes)
        assert(finalType == actualFinalType)
      }
    }
    finishedInference.set(true)
  }

  private fun findNearestWriteInstruction(variable: VariableDescriptor, instruction: Instruction): Int? {
    return writeInstructions.lastOrNull { it.descriptor == variable && it.num() <= instruction.num() }?.num()
  }

  @Suppress("UnnecessaryVariable")
  private fun doGetSharedVariables(): List<VariableDescriptor> {
    if (!PsiUtil.isCompileStatic(scope)) {
      return emptyList()
    }
    val flow = scope.controlFlow
    val foreignDescriptors: List<String> = flow
      .mapNotNull { it?.element as? GrControlFlowOwner }
      .flatMap { ControlFlowUtils.getForeignVariableIdentifiers(it) { true } }
    val sharedVariables: List<VariableDescriptor> = flow
      .filterIsInstance<ReadWriteVariableInstruction>()
      .mapNotNull { instruction -> instruction.descriptor.takeIf { it.getName() in foreignDescriptors } }
      // fields have their own inference rules
      .filter { if (it is ResolvedVariableDescriptor) it.variable !is GrField else true }
      // do not bother to process variable if it was assigned only once
      .filter { descriptor ->
        flow
          .filterIsInstance<ReadWriteVariableInstruction>()
          .filter(ReadWriteVariableInstruction::isWrite)
          .count { it.descriptor == descriptor } >= 2
      }
      .toSet().toList()
    return sharedVariables
  }

}