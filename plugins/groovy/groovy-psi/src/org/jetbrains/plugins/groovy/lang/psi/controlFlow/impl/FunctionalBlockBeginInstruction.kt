// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression

class FunctionalBlockBeginInstruction(element: GrFunctionalExpression) : InstructionImpl(element) {

  override fun getElement(): GrFunctionalExpression {
    return super.getElement() as GrFunctionalExpression
  }

  override fun toString(): String {
    return super.toString() + " FBegin: $element"
  }
}