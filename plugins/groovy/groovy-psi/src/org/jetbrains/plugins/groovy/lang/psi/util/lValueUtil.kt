/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("GroovyLValueUtil")

package org.jetbrains.plugins.groovy.lang.psi.util

import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTuple

/**
 * The expression is a rValue when it is in rValue position or it's a lValue of operator assignment.
 */
fun GrExpression.isRValue(): Boolean {
  val (parent, lastParent) = skipParentsOfType<GrParenthesizedExpression>() ?: return true
  return parent !is GrAssignmentExpression || lastParent != parent.lValue || parent.operationTokenType != GroovyTokenTypes.mASSIGN
}

/**
 * The expression is a lValue when it's on the left of whatever assignment.
 */
fun GrExpression.isLValue(): Boolean {
  val parent = parent
  return when (parent) {
    is GrTuple -> true
    is GrAssignmentExpression -> this == parent.lValue
    else -> false
  }
}
