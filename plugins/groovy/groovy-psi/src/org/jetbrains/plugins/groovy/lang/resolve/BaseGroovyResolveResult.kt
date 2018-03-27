// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  protected val myPlace: PsiElement?,
  private val myResolveContext: PsiElement? = null,
  protected val mySubstitutor: PsiSubstitutor = PsiSubstitutor.EMPTY
) : ElementGroovyResult<T>(element) {

  private val accessible by lazy(LazyThreadSafetyMode.PUBLICATION) {
    myElement !is PsiMember || myPlace == null || PsiUtil.isAccessible(myPlace, myElement)
  }

  override fun isAccessible(): Boolean = accessible

  private val staticsOk by lazy(LazyThreadSafetyMode.PUBLICATION) {
    myResolveContext is GrImportStatement ||
    myElement !is PsiModifierListOwner ||
    myPlace == null ||
    GrStaticChecker.isStaticsOK(myElement, myPlace, myResolveContext, false)
  }

  override fun isStaticsOK(): Boolean = staticsOk

  override fun getCurrentFileResolveContext(): PsiElement? = myResolveContext

  override fun getSubstitutor(): PsiSubstitutor = mySubstitutor
}
