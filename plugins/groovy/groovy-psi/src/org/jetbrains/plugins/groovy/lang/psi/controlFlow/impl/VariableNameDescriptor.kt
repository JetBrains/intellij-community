// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor

data class VariableNameDescriptor(val variableName: String): VariableDescriptor {
  override fun getName(): String = variableName

  override fun toString(): String {
    return variableName
  }
}
