// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiWildcardType
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.transformation.GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToProvider
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getContainingCall

// Intentionally not registered as extension, we need to preserve a strict order and call this before [org.jetbrains.plugins.gradle.service.resolve.GradleDelegatesToProvider]
class GradleActionDelegatesToProvider : GrDelegatesToProvider {
  override fun getDelegatesToInfo(expression: GrFunctionalExpression): DelegatesToInfo? {
    val call = getContainingCall(expression) ?: return null
    val result = call.advancedResolve() as? GroovyMethodResult ?: return null
    val bridgeDelegate = result.element.getUserData(GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY)
    if (bridgeDelegate != null) {
      val actualBridgeDelegate = result.substitutor.substitute(bridgeDelegate)
      return DelegatesToInfo(actualBridgeDelegate, Closure.DELEGATE_FIRST)
    }
    val argumentMapping = result.candidate?.argumentMapping ?: return null
    val type = argumentMapping.expectedType(ExpressionArgument(expression)) as? PsiClassType ?: return null
    val clazz = type.resolve() ?: return null
    if (clazz.qualifiedName != GradleCommonClassNames.GRADLE_API_ACTION && !clazz.hasAnnotation("org.gradle.api.HasImplicitReceiver")) {
      return null
    }
    val substitutedType = result.substitutor.substitute(type) as? PsiClassType ?: return null
    val typeArgument = substitutedType.parameters.singleOrNull() ?: return null
    val delegateType = if (typeArgument is PsiWildcardType && typeArgument.isSuper) {
      typeArgument.bound
    }
    else {
      typeArgument
    }
    return DelegatesToInfo(delegateType, Closure.DELEGATE_FIRST)
  }
}