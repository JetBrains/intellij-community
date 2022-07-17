// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

class FunctionalBlockEndInstruction(private val startNode : FunctionalBlockBeginInstruction) : InstructionImpl(null) {
  override fun toString(): String {
    return super.toString() + " Rel: ${startNode.num()}"
  }
}