// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.util.SmartList
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.CallParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature
import org.jetbrains.plugins.groovy.lang.resolve.impl.MethodSignature
import org.jetbrains.plugins.groovy.lang.resolve.impl.argumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.type
import org.jetbrains.plugins.groovy.transformations.impl.synch.isStatic

internal class GroovyMethodReferenceType(
  private val myMethodReference: GrReferenceExpression
) : GroovyClosureType(myMethodReference) {

  override val signatures: List<CallSignature<*>> by lazyPub {
    val methodSignatures = myMethodReference.resolve(false).mapNotNullTo(SmartList()) { result ->
      (result.element as? PsiMethod)?.let {
        MethodSignature(it, result.substitutor, myMethodReference)
      }
    }
    tryAddInstanceParameter(methodSignatures) ?: methodSignatures
  }

  private fun tryAddInstanceParameter(methodSignatures: List<MethodSignature>): List<CallSignature<*>>? {
    val resolvedQualifier = (myMethodReference.qualifierExpression as? GroovyReference)
                              ?.resolve(false)
                              ?.singleOrNull()
                              ?.element as? PsiClass ?: return null
    val instanceParameter = object : CallParameter {
      override val type: PsiType = resolvedQualifier.type()
      override val parameterName: String = "_instance"
      override val isOptional: Boolean = false
    }

    return methodSignatures.map { methodSignature ->
      val originalMethod = methodSignature.originalMethod()
      if (originalMethod.isStatic() || originalMethod.isConstructor) {
        return@map methodSignature
      }
      object : CallSignature<CallParameter> by methodSignature {

        override val parameterCount: Int
          get() = 1 + methodSignature.parameterCount

        override val parameters: List<CallParameter>
          get() = listOf(instanceParameter, *methodSignature.parameters.toTypedArray())

        override fun applyTo(arguments: Arguments, context: PsiElement): ArgumentMapping<CallParameter> =
          argumentMapping(this, arguments, context)
      }
    }
  }

}
