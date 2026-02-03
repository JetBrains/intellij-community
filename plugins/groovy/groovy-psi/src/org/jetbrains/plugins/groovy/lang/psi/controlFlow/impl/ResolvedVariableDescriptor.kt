// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor

data class ResolvedVariableDescriptor(val variable: GrVariable): VariableDescriptor {
  override fun getName(): String {
    return variable.name
  }

  override fun toString(): String {
    return getName()
  }
}
