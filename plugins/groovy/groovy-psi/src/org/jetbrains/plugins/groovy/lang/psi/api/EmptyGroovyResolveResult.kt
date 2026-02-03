// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor

object EmptyGroovyResolveResult : GroovyResolveResult {

  override fun getElement(): PsiElement? = null

  override fun isApplicable(): Boolean = false

  override fun isAccessible(): Boolean = false

  override fun getCurrentFileResolveContext(): PsiElement? = null

  override fun isStaticsOK(): Boolean = true

  override fun getContextSubstitutor(): PsiSubstitutor = PsiSubstitutor.EMPTY

  override fun getSubstitutor(): PsiSubstitutor = PsiSubstitutor.EMPTY

  override fun isValidResult(): Boolean = false

  override fun isInvokedOnProperty(): Boolean = false

  override fun getSpreadState(): SpreadState? = null
}
