// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState

open class ElementResolveResult<out T : PsiElement>(private val element: T) : GroovyResolveResult {

  final override fun getElement(): T = element

  override fun isValidResult(): Boolean = element.isValid && isStaticsOK && isAccessible && isApplicable

  override fun isStaticsOK(): Boolean = true

  override fun isAccessible(): Boolean = true

  override fun getCurrentFileResolveContext(): PsiElement? = null

  override fun getSubstitutor(): PsiSubstitutor = PsiSubstitutor.EMPTY

  override fun isApplicable(): Boolean = true

  override fun isInvokedOnProperty(): Boolean = false

  override fun getSpreadState(): SpreadState? = null

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as ElementResolveResult<*>
    return element == other.element
  }

  override fun hashCode(): Int = element.hashCode()
}
