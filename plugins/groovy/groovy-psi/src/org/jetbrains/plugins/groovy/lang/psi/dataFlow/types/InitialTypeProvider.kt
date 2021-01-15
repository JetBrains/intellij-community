// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.isNestedFlowProcessingAllowed
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic


internal class InitialTypeProvider(private val start: GrControlFlowOwner, private val initialTypes: Map<VariableDescriptor, DFAType>) {

  private val parentFlowOwner by lazyPub {
    val parent = start.parent
    if (parent != null) ControlFlowUtils.findControlFlowOwner(parent) else null
  }

  private val parentInstruction by lazyPub {
    val flow = parentFlowOwner
    if (flow != null) ControlFlowUtils.findNearestInstruction(start, flow.controlFlow) else null
  }

  fun initialType(descriptor: VariableDescriptor): DFAType? {
    if (isCompileStatic(start)) return DFAType.create(null)
    if (isNestedFlowProcessingAllowed()) {
      val typeFromInitialContext = initialTypes[descriptor]
      if (typeFromInitialContext != null) return typeFromInitialContext
      val type = getTypeFromParentDFA(descriptor)
      if (type != null) return DFAType.create(type)
    }
    val resolvedDescriptor = descriptor as? ResolvedVariableDescriptor ?: return null
    val field = resolvedDescriptor.variable as? GrField ?: return null
    return field.typeGroovy?.run(DFAType::create)
  }

  private fun getTypeFromParentDFA(descriptor: VariableDescriptor): PsiType? {
    val parentFlowOwner = this.parentFlowOwner ?: return null
    val parentCache = TypeInferenceHelper.getInferenceCache(parentFlowOwner)
    val instruction = parentInstruction ?: return null
    if (instruction !is ReadWriteVariableInstruction) {
      // If we want to query type of the variable in outer flow, then this variable is foreign to current flow.
      // It means that there must be a bunch of read instructions preceding the instruction with closable block.
      // If there are no such read instructions, then the code is incorrect.
      return null
    }
    return parentCache.getInferredType(descriptor, instruction, false)
  }
}
