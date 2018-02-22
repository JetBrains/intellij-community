// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyModifiersUtil")

package org.jetbrains.plugins.groovy.lang.psi.util

import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

fun isDefUnnecessary(method: GrMethod): Boolean {
  return when {
    method.isConstructor -> true
    method.hasTypeParameters() -> method.modifierList.modifiers.any { it.node.elementType != GroovyTokenTypes.kDEF }
    else -> method.returnTypeElementGroovy != null
  }
}