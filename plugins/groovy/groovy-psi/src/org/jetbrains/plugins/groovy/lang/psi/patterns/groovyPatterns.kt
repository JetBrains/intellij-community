// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.patterns

import com.intellij.openapi.util.Key
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PsiMethodPattern
import com.intellij.psi.PsiMethod
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder.checkKind

val closureCallKey: Key<GrCall> = Key.create("groovy.pattern.closure.call")

inline fun <reified T : GroovyPsiElement> groovyElement(): GroovyElementPattern.Capture<T> {
  return GroovyElementPattern.Capture(T::class.java)
}

inline fun <reified T : GrExpression> groovyExpression(): GroovyExpressionPattern.Capture<T> {
  return GroovyExpressionPattern.Capture(T::class.java)
}

fun groovyList(): GroovyExpressionPattern.Capture<GrListOrMap> {
  return groovyExpression<GrListOrMap>().with(object : PatternCondition<GrListOrMap>("isList") {
    override fun accepts(t: GrListOrMap, context: ProcessingContext?) = !t.isMap
  })
}

fun psiMethod(containingClass: String, vararg names: String): PsiMethodPattern {
  val methodPattern = GroovyPatterns.psiMethod()
  val withName = if (names.isEmpty()) methodPattern else methodPattern.withName(*names)
  return withName.definedInClass(containingClass)
}

fun PsiMethodPattern.withKind(kind: Any): PsiMethodPattern {
  return with(object : PatternCondition<PsiMethod>("withKind") {
    override fun accepts(t: PsiMethod, context: ProcessingContext?): Boolean = checkKind(t, kind)
  })
}

fun groovyClosure(): GroovyClosurePattern = GroovyClosurePattern()

val groovyAnnotationArgumentValue: GroovyElementPattern.Capture<GrAnnotationMemberValue> = groovyElement()
val groovyAnnotationArgument: GroovyAnnotationArgumentPattern.Capture = GroovyAnnotationArgumentPattern.Capture()
val groovyAnnotationArgumentList: GroovyElementPattern.Capture<GrAnnotationArgumentList> = groovyElement()
