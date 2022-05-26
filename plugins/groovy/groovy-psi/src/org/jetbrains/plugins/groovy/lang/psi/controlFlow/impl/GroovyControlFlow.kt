// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor

data class GroovyControlFlow(
  val flow: Array<Instruction>,
  val varIndices: Array<VariableDescriptor>,
) {

  fun getIndex(descriptor : VariableDescriptor?) : Int {
    return if (descriptor == null) 0 else varIndices.indices.find { varIndices[it] == descriptor } ?: 0
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GroovyControlFlow) return false

    if (!flow.contentEquals(other.flow)) return false
    if (!varIndices.contentEquals(other.varIndices)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = flow.contentHashCode()
    result = 31 * result + varIndices.contentHashCode()
    return result
  }
}