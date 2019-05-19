// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import org.jetbrains.plugins.groovy.util.recursionAwareLazy

open class BaseGroovyResolveResult<out T : PsiElement>(
  element: T,
  protected val place: PsiElement,
  private val resolveContext: PsiElement? = null,
  private val substitutor: PsiSubstitutor = PsiSubstitutor.EMPTY,
  private val spreadState: SpreadState? = null
) : ElementResolveResult<T>(element) {

  constructor(element: T, place: PsiElement, state: ResolveState) : this(
    element,
    place,
    resolveContext = state[ClassHint.RESOLVE_CONTEXT],
    substitutor = state[PsiSubstitutor.KEY],
    spreadState = state[SpreadState.SPREAD_STATE]
  )

  private val accessible by recursionAwareLazy {
    element !is PsiMember || PsiUtil.isAccessible(place, element)
  }

  override fun isAccessible(): Boolean = accessible

  private val staticsOk by recursionAwareLazy {
    resolveContext is GrImportStatement ||
    element !is PsiModifierListOwner ||
    GrStaticChecker.isStaticsOK(element, place, resolveContext, false)
  }

  override fun isStaticsOK(): Boolean = staticsOk

  override fun getCurrentFileResolveContext(): PsiElement? = resolveContext

  final override fun getContextSubstitutor(): PsiSubstitutor = substitutor

  override fun getSubstitutor(): PsiSubstitutor = substitutor

  override fun getSpreadState(): SpreadState? = spreadState
}
