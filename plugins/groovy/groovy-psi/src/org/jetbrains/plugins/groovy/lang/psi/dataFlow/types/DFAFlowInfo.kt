// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType

internal data class DFAFlowInfo(val initialTypes: Map<VariableDescriptor, DFAType>,
                                val interestingInstructions: Set<Instruction>,
                                val acyclicInstructions: Set<Instruction>,
                                val interestingDescriptors: Set<VariableDescriptor>,
                                val dependentOnSharedVariables: Set<Instruction>)