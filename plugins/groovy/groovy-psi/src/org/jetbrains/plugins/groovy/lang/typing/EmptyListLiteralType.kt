// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil.isInheritorOrSelf
import com.intellij.util.lazyPub
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.impl.getTypeArgumentsFromResult
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.asJavaClassResult
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.getExpectedType
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.inferDerivedSubstitutor

class EmptyListLiteralType(literal: GrListOrMap) : ListLiteralType(literal) {

  private val resolveResult: GroovyResolveResult? by lazyPub(fun(): GroovyResolveResult? {
    val clazz = resolve() ?: return null
    val lType = getExpectedType(literal) as? PsiClassType
    val substitutor = if (lType == null || lType.isRaw || !isInheritorOrSelf(clazz, lType.resolve(), true)) {
      JavaPsiFacade.getInstance(literal.project).elementFactory.createRawSubstitutor(clazz)
    }
    else {
      inferDerivedSubstitutor(lType, clazz, literal)
    }
    return BaseGroovyResolveResult(clazz, literal, substitutor = substitutor)
  })

  override fun resolveGenerics(): ClassResolveResult = resolveResult.asJavaClassResult()

  override fun getParameters(): Array<out PsiType?> = resolveResult?.getTypeArgumentsFromResult() ?: PsiType.EMPTY_ARRAY

  override fun setLanguageLevel(languageLevel: LanguageLevel): PsiClassType = error("This method must not be called")

  override fun getLeastUpperBound(vararg psiTypes: PsiType?): PsiType = error("This method must not be called")
}
