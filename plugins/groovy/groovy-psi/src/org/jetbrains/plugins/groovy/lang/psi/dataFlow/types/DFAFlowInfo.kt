// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction

internal data class DFAFlowInfo(
  val interestingInstructions: Set<Instruction>,
  val acyclicInstructions: Set<Instruction>,
  val interestingDescriptors: Set<Int>,
)