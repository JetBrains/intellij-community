// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.groovy.service.resolve

import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.AbstractClosureParameterEnhancer

/**
 * Provides type for parameters of closures that were obtained after transformation of `Action`-accepting methods
 * @See GradleActionToClosureMemberContributor
 */
class GradleClosureParameterEnhancer : AbstractClosureParameterEnhancer() {
  override fun getClosureParameterType(closure: GrFunctionalExpression, index: Int): PsiType? {
    val callResult = closure.parentOfType<GrMethodCall>()?.advancedResolve() as? GroovyMethodResult ?: return null
    val callMethod = callResult.element
    val fromActionDelegate = callMethod.getUserData(GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY) ?: return null
    return callResult.substitutor.substitute(fromActionDelegate)
  }
}