// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("VariableDescriptorFactory")

package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable

fun GrReferenceExpression.createDescriptor(): VariableDescriptor? {
  val referenceName = this.referenceName ?: return null
  val resolved = staticReference.resolve() as? GrVariable
  if (resolved != null) return ResolvedVariableDescriptor(resolved)
  return VariableNameDescriptor(referenceName)
}

fun GrVariable.createDescriptor(): VariableDescriptor {
  return when(this) {
    is GrBindingVariable -> VariableNameDescriptor(this.name)
    else -> ResolvedVariableDescriptor(this)
  }
}
