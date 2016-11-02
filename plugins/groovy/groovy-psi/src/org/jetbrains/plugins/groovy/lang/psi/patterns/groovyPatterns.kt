/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.patterns

import com.intellij.openapi.util.Key
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

val closureCallKey = Key.create<GrCall>("groovy.pattern.closure.call")

inline fun <reified T : GrExpression> groovyExpression() = GroovyExpressionPattern.Capture(T::class.java)

fun groovyList() = groovyExpression<GrListOrMap>().with(object : PatternCondition<GrListOrMap>("isList") {
  override fun accepts(t: GrListOrMap, context: ProcessingContext?) = !t.isMap
})

fun psiMethod(containingClass: String, vararg name: String) = GroovyPatterns.psiMethod().withName(*name).definedInClass(containingClass)

fun groovyClosure() = GroovyClosurePattern()
