// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope

class GradleExtensionType(
  private val delegate: PsiClassType
) : PsiClassType(delegate.languageLevel) {

  override fun isValid(): Boolean = delegate.isValid
  override fun getResolveScope(): GlobalSearchScope = delegate.resolveScope
  override fun resolve(): PsiClass? = delegate.resolve()
  override fun resolveGenerics(): ClassResolveResult = delegate.resolveGenerics()
  override fun getParameters(): Array<PsiType> = delegate.parameters
  override fun rawType(): PsiClassType = GradleExtensionType(delegate.rawType())

  override fun getClassName(): String = delegate.className
  override fun getCanonicalText(): String = delegate.canonicalText
  override fun getPresentableText(): String = delegate.presentableText
  override fun equalsToText(text: String): Boolean = delegate.equalsToText(text)

  override fun getLanguageLevel(): LanguageLevel = delegate.languageLevel
  override fun setLanguageLevel(languageLevel: LanguageLevel): PsiClassType = error("must not be called")
}
