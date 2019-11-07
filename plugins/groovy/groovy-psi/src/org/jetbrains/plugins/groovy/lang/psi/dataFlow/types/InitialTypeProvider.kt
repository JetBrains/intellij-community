// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiType
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InvocationKind
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.getInvocationKind


class InitialTypeProvider(val start: GrControlFlowOwner) {

  private val parentFlowOwner by lazyPub {
    val parent = start.parent
    if (parent != null) ControlFlowUtils.findControlFlowOwner(parent) else null
  }

  private val parentInstruction by lazyPub {
    val flow = parentFlowOwner
    if (flow != null) ControlFlowUtils.findNearestInstruction(start, flow.controlFlow) else null
  }

  fun initialType(descriptor: VariableDescriptor): PsiType? {
    if (start is GrFunctionalExpression && getInvocationKind(start) != InvocationKind.UNKNOWN && parentInstruction != null) {
      val type = RecursionManager.doPreventingRecursion(start, false) {
        val resolvedDescriptor = TypeInferenceHelper.extractResolvedDescriptor(parentFlowOwner, descriptor) ?: descriptor
        TypeInferenceHelper.getInferredType(resolvedDescriptor, parentInstruction, parentFlowOwner)
      }
      if (type != null) return type
    }
    val resolvedDescriptor = descriptor as? ResolvedVariableDescriptor ?: return null
    val field = resolvedDescriptor.variable as? GrField ?: return null
    return field.typeGroovy
  }
}
