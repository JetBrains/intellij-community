// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType

internal data class ClosureFrame
@JvmOverloads constructor(val startState: TypeDfaState,
                          private val localReassignments: MutableList<Pair<VariableDescriptor, DFAType>> = SmartList()) {
  fun addReassignment(descriptor: VariableDescriptor, type: DFAType) {
    localReassignments.add(descriptor to type)
  }

  fun getReassignments() : Map<VariableDescriptor, List<DFAType>> = localReassignments.groupBy({ it.first }, { it.second })
}