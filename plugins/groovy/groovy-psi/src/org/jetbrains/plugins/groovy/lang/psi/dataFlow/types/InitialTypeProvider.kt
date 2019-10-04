// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor

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
    val resolvedDescriptor = descriptor as? ResolvedVariableDescriptor ?: return null
    val field = resolvedDescriptor.variable as? GrField ?: return null
    return field.typeGroovy
  }
}
