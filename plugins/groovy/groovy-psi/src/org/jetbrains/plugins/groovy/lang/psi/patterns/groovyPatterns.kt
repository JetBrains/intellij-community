// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.patterns

import com.intellij.openapi.util.Key
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

val closureCallKey = Key.create<GrCall>("groovy.pattern.closure.call")

inline fun <reified T : GroovyPsiElement> groovyElement() = GroovyElementPattern.Capture(T::class.java)

inline fun <reified T : GrExpression> groovyExpression() = GroovyExpressionPattern.Capture(T::class.java)

fun groovyList() = groovyExpression<GrListOrMap>().with(object : PatternCondition<GrListOrMap>("isList") {
  override fun accepts(t: GrListOrMap, context: ProcessingContext?) = !t.isMap
})

fun psiMethod(containingClass: String, vararg name: String) = GroovyPatterns.psiMethod().withName(*name).definedInClass(containingClass)

fun groovyClosure() = GroovyClosurePattern()

val groovyAnnotationArgumentValue = groovyElement<GrAnnotationMemberValue>()
val groovyAnnotationArgument = GroovyAnnotationArgumentPattern.Capture()
val groovyAnnotationArgumentList = groovyElement<GrAnnotationArgumentList>()
