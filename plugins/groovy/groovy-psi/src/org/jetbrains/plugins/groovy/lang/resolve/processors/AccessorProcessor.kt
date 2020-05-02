// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.ElementClassHint.DeclarationKind
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.util.checkKind
import org.jetbrains.plugins.groovy.lang.psi.util.getAccessorName
import org.jetbrains.plugins.groovy.lang.resolve.AccessorResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GenericAccessorResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

class AccessorProcessor(
  propertyName: String,
  private val propertyKind: PropertyKind,
  private val arguments: Arguments?,
  private val place: PsiElement
) : BaseMethodProcessor(propertyKind.getAccessorName(propertyName)),
    GrResolverProcessor<GroovyResolveResult> {

  init {
    hint(ElementClassHint.KEY, ElementClassHint {
      it == DeclarationKind.METHOD && acceptMore
    })
    hint(GroovyResolveKind.HINT_KEY, GroovyResolveKind.Hint {
      it == GroovyResolveKind.METHOD && acceptMore
    })
  }

  override val results: List<GroovyResolveResult> get() = myCandidates

  override fun candidate(element: PsiMethod, state: ResolveState): GroovyMethodResult? {
    if (!element.checkKind(propertyKind)) return null
    return if (element.hasTypeParameters()) {
      GenericAccessorResolveResult(element, place, state, arguments)
    }
    else {
      AccessorResolveResult(element, place, state, arguments)
    }
  }
}
