// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor

class InitialTypeProvider(val start: GrControlFlowOwner) {

  fun initialType(descriptor: VariableDescriptor): PsiType? {
    var prevPlace = start
    val parent = start.parent ?: return null
    var place: GrControlFlowOwner? = ControlFlowUtils.findControlFlowOwner(parent)
    while(place != null) {

      val instruction = ControlFlowUtils.findNearestInstruction(prevPlace, place.controlFlow) ?: break
      val type = TypeInferenceHelper.getInferredType(descriptor, instruction, place)
      if (type != null) return type

      prevPlace = place
      place = ControlFlowUtils.findControlFlowOwner(place)
    }

    val resolvedDescriptor = descriptor as? ResolvedVariableDescriptor ?: return null
    val field = resolvedDescriptor.variable as? GrField ?: return null
    return field.typeGroovy
  }
}
