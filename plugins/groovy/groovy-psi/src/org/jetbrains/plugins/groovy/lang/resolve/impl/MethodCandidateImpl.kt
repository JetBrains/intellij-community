// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isCompileStatic
import org.jetbrains.plugins.groovy.lang.psi.util.isEffectivelyVarArgs
import org.jetbrains.plugins.groovy.lang.psi.util.isOptional
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability.applicable
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.util.recursionAwareLazy

class MethodCandidateImpl(
  override val receiver: PsiType?,
  override val method: PsiMethod,
  erasureSubstitutor: PsiSubstitutor,
  arguments: Arguments?,
  context: PsiElement
) : GroovyMethodCandidate {

  override val argumentMapping: ArgumentMapping? by recursionAwareLazy {
    when {
      arguments == null -> null
      method.isEffectivelyVarArgs -> {
        val invokedAsIs = run(fun(): Boolean {
          // call foo(X[]) as is, i.e. with argument of type X[] (or subtype)
          val parameters = method.parameterList.parameters
          if (arguments.size != parameters.size) return false
          val parameterType = parameterType(parameters.last().type, erasureSubstitutor, true)
          val lastArgApplicability = argumentApplicability(parameterType, arguments.last(), context)
          return lastArgApplicability == applicable
        })
        if (invokedAsIs) {
          PositionalArgumentMapping(method, arguments, context)
        }
        else {
          VarargArgumentMapping(method, arguments, context)
        }
      }
      arguments.isEmpty() -> {
        val parameters = method.parameterList.parameters
        val parameter = parameters.singleOrNull()
        if (parameter != null && !parameter.isOptional && parameter.type is PsiClassType && !isCompileStatic(context)) {
          NullArgumentMapping(parameter)
        }
        else {
          PositionalArgumentMapping(method, arguments, context)
        }
      }
      else -> PositionalArgumentMapping(method, arguments, context)
    }
  }
}
