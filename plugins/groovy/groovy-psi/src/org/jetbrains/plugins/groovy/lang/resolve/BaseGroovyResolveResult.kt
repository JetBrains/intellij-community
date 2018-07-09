// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiSubstitutor
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil

open class BaseGroovyResolveResult<out T : PsiElement>(
  element: T,
  private val place: PsiElement?,
  private val resolveContext: PsiElement? = null,
  private val substitutor: PsiSubstitutor = PsiSubstitutor.EMPTY
) : ElementResolveResult<T>(element) {

  private val accessible by lazy(LazyThreadSafetyMode.PUBLICATION) {
    element !is PsiMember || place == null || PsiUtil.isAccessible(place, element)
  }

  override fun isAccessible(): Boolean = accessible

  private val staticsOk by lazy(LazyThreadSafetyMode.PUBLICATION) {
    resolveContext is GrImportStatement ||
    element !is PsiModifierListOwner ||
    place == null ||
    GrStaticChecker.isStaticsOK(element, place, resolveContext, false)
  }

  override fun isStaticsOK(): Boolean = staticsOk

  override fun getCurrentFileResolveContext(): PsiElement? = resolveContext

  override fun getSubstitutor(): PsiSubstitutor = substitutor
}
