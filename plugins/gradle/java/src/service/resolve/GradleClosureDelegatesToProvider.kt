// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import com.intellij.util.asSafely
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.transformation.GRADLE_GENERATED_CLOSURE_OVERLOAD_DELEGATE_KEY
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToProvider
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getContainingCall

class GradleClosureDelegatesToProvider : GrDelegatesToProvider {
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
    if (clazz.qualifiedName != GroovyCommonClassNames.GROOVY_LANG_CLOSURE) {
      return null
    }

    val receiverClass = result.candidate?.receiverType.resolve() ?: return null
    val methodName = result.candidate?.method?.name ?: return null
    val methodOverloads = receiverClass.findMethodsAndTheirSubstitutorsByName(methodName, true)
    for (method in methodOverloads) {
      val lastParam = method.first.parameters.lastOrNull() ?: continue
      val paramType = lastParam.type.asSafely<PsiClassType>() ?: continue
      val resolveResult = paramType.resolveGenerics()
      val resolvedClass = resolveResult.element ?: continue
      if (resolvedClass.qualifiedName == GradleCommonClassNames.GRADLE_API_ACTION){
        val delegateType = paramType.parameters.singleOrNull()?.unwrapWildcard()
        return DelegatesToInfo(method.second.substitute(delegateType), Closure.DELEGATE_FIRST)
      }
    }
    return null
  }

  private fun PsiType.unwrapWildcard() : PsiType = if (this is PsiWildcardType) {
    this.bound ?: PsiType.getTypeByName(CommonClassNames.JAVA_LANG_OBJECT, this.manager.project, this.resolveScope)
  } else {
    this
  }
}