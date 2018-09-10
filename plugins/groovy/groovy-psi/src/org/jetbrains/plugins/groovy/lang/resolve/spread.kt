// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState.SPREAD_STATE
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState.create
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer

fun PsiType?.processSpread(processor: PsiScopeProcessor, state: ResolveState, place: PsiElement, deep: Boolean = false): Boolean {
  if (this == null) return true

  val componentType = ClosureParameterEnhancer.findTypeForIteration(this, place)
  if (componentType == null || componentType == this) return true

  val componentState = create(componentType, state[SPREAD_STATE])
  val resolveState = state.put(SPREAD_STATE, componentState)
  if (!componentType.processReceiverType(processor, resolveState, place)) return false
  if (!deep) return true
  return componentType.processSpread(processor, resolveState, place, true)
}
