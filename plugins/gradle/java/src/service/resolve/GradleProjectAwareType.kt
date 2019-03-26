// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

// TODO track exact project id to obtain project extension data
class GradleProjectAwareType(
  private val fqn: String,
  private val context: PsiElement
) : PsiClassType(LanguageLevel.HIGHEST) {

  override fun isValid(): Boolean = context.isValid
  override fun getResolveScope(): GlobalSearchScope = context.resolveScope
  override fun resolve(): PsiClass? = JavaPsiFacade.getInstance(context.project).findClass(canonicalText, resolveScope)

  override fun resolveGenerics(): ClassResolveResult {
    val resolved = resolve() ?: return ClassResolveResult.EMPTY
    return object : ClassResolveResult {
      override fun getElement(): PsiClass = resolved
      override fun getSubstitutor(): PsiSubstitutor = PsiSubstitutor.EMPTY
      override fun isValidResult(): Boolean = true
      override fun isAccessible(): Boolean = true
      override fun isStaticsScopeCorrect(): Boolean = true
      override fun getCurrentFileResolveScope(): PsiElement? = null
      override fun isPackagePrefixPackageReference(): Boolean = false
    }
  }

  override fun getParameters(): Array<PsiType> = PsiType.EMPTY_ARRAY
  override fun rawType(): PsiClassType = this

  override fun getCanonicalText(): String = fqn
  override fun getClassName(): String = StringUtil.getShortName(fqn)
  override fun getPresentableText(): String = className
  override fun equalsToText(text: String): Boolean = text == fqn

  override fun getLanguageLevel(): LanguageLevel = myLanguageLevel
  override fun setLanguageLevel(languageLevel: LanguageLevel): PsiClassType = error("must not be called")
}
