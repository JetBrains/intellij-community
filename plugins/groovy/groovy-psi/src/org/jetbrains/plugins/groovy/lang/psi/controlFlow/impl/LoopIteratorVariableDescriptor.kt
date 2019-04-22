// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor

data class LoopIteratorVariableDescriptor(private val clause: GrForInClause) : VariableDescriptor {

  override fun getName(): String = "iterator of ${(clause.iteratedExpression ?: clause).text}"

  override fun toString(): String = getName()
}
