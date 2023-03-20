// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GrInterfaceDefaultMethodMemberContributor : NonCodeMembersContributor() {

  override fun processDynamicElements(qualifierType: PsiType,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (!processor.shouldProcessMethods()) {
      return
    }
    val qualifier = place.asSafely<GrReferenceExpression>()?.qualifier ?: return
    if (qualifier.lastChild.elementType != GroovyTokenTypes.kSUPER) return
    val name = processor.getName(state) ?: return
    val implementedInterfaces = place.parentOfType<PsiClass>()?.implementsListTypes ?: return
    for (implementedInterface in implementedInterfaces) {
      val result = implementedInterface.resolveGenerics()
      val defaultMethods = result.element?.allMethods?.filter { !it.hasModifierProperty(PsiModifier.ABSTRACT) } ?: continue
      for (defaultMethod in defaultMethods) {
        if (defaultMethod.name == name) {
          val newState = state.put(PsiSubstitutor.KEY, state[PsiSubstitutor.KEY].putAll(result.substitutor))
          processor.execute(defaultMethod, newState)
        }
      }
    }
  }
}