// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.impl.getTypeArgumentsFromResult
import org.jetbrains.plugins.groovy.lang.resolve.DiamondResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.asJavaClassResult

class EmptyListLiteralType(literal: GrListOrMap) : ListLiteralType(literal) {

  val resolveResult: DiamondResolveResult? by lazyPub {
    resolve()?.let {
      DiamondResolveResult(it, literal, ResolveState.initial())
    }
  }

  override fun resolveGenerics(): ClassResolveResult = resolveResult.asJavaClassResult()

  override fun getParameters(): Array<out PsiType?> = resolveResult?.getTypeArgumentsFromResult() ?: PsiType.EMPTY_ARRAY

  override fun setLanguageLevel(languageLevel: LanguageLevel): PsiClassType = error("This method must not be called")

  override fun getLeastUpperBound(vararg psiTypes: PsiType?): PsiType = error("This method must not be called")
}
