// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder

class CollectingGroovyInferenceSessionBuilder(val context: PsiElement,
                                              val candidate: GroovyMethodCandidate,
                                              val targetMethod: GrMethod,
                                              private val contextSubstitutor: PsiSubstitutor)
  : GroovyInferenceSessionBuilder(context, candidate, contextSubstitutor) {

  private var proxyMethod: PsiMethod? = null

  fun addProxyMethod(method: PsiMethod?): CollectingGroovyInferenceSessionBuilder {
    proxyMethod = method
    return this
  }

  override fun build(): GroovyInferenceSession {
    collectExpressionFilters()
    val mapping = getProxyMethodMapping()
    val session =
      CollectingGroovyInferenceSession(targetMethod.typeParameters, context, contextSubstitutor, mapping, emptySet(), skipClosureBlock,
                                       expressionFilters)
    return doBuild(session)
  }

  private fun getProxyMethodMapping(): Map<String, GrParameter> {
    return proxyMethod?.parameters?.map { it.name!! }?.zip(targetMethod.parameters)?.toMap() ?: emptyMap()
  }
}