// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.testIntegration

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.util.GroovyMainMethodSearcher

/**
 * Used as an "extension" version of [GroovyMainMethodSearcher] where it is possible.
 */
class GroovyMainMethodProvider : JavaMainMethodProvider {
  override fun isApplicable(clazz: PsiClass): Boolean = clazz.language == GroovyLanguage

  override fun hasMainMethod(clazz: PsiClass): Boolean {
    return findMainInClass(clazz) != null
  }

  override fun findMainInClass(clazz: PsiClass): PsiMethod? {
    return GroovyMainMethodSearcher.findMainMethodInClassOrParent(clazz)
  }
}