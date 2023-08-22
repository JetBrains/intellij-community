// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.*
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.psi.util.isEffectivelyVarArgs
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter

internal class MethodSignature(
  private val method: PsiMethod,
  private val substitutor: PsiSubstitutor,
  context: PsiElement
) : CallSignature<PsiCallParameter> {

  override val isVararg: Boolean by lazyPub {
    method.isEffectivelyVarArgs
  }

  override val parameters: List<PsiCallParameter> by lazyPub {
    method.parameterList.parameters.map { psi ->
      PsiCallParameterImpl(psi, substitutor, context)
    }
  }

  override val returnType: PsiType?
    get() {
      if (method.isConstructor) {
        val clazz: PsiClass = method.containingClass ?: return null
        return GroovyPsiElementFactory.getInstance(method.project).createType(clazz, PsiSubstitutor.EMPTY)
      }
      else {
        return substitutor.substitute(PsiUtil.getSmartReturnType(method))
      }
    }

  override fun applyTo(arguments: Arguments, context: PsiElement): ArgumentMapping<PsiCallParameter> {
    return argumentMapping(this, arguments, context)
  }

  fun originalMethod() : PsiMethod = method
}
