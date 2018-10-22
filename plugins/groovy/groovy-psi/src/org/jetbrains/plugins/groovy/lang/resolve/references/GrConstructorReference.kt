// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.util.GrInnerClassConstructorUtil
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getConstructorCandidates
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCachingReference
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.buildTopLevelArgumentTypes

class GrConstructorReference(element: GrNewExpression) : GroovyCachingReference<GrNewExpression>(element) {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val ref = element.referenceElement ?: return emptyList()
    val classCandidate = inferClassCandidate(ref) ?: return emptyList()
    assert(classCandidate.element is PsiClass)
    if (incomplete) {
      return getConstructorCandidates(ref, classCandidate, null).toList()
    }
    val argumentList = element.argumentList ?: return emptyList()

    if (argumentList.namedArguments.isNotEmpty() && argumentList.expressionArguments.isEmpty()) {
      val mapType = GrMapType.createFromNamedArgs(argumentList, element.namedArguments)
      val constructorResults = getConstructorCandidates(ref, classCandidate, arrayOf<PsiType>(mapType)) //one Map parameter, actually
      for (result in constructorResults) {
        val resolved = result.element
        if (resolved is PsiMethod) {
          val constructor = resolved as PsiMethod?
          val parameters = constructor!!.parameterList.parameters
          if (parameters.size == 1 && InheritanceUtil.isInheritor(parameters[0].type, CommonClassNames.JAVA_UTIL_MAP)) {
            return constructorResults.toList()
          }
        }
      }
      val emptyConstructors = getConstructorCandidates(ref, classCandidate, PsiType.EMPTY_ARRAY)
      if (emptyConstructors.isNotEmpty()) {
        return emptyConstructors.toList()
      }
    }

    var types = buildTopLevelArgumentTypes(ref)
    types = GrInnerClassConstructorUtil.addEnclosingArgIfNeeded(types, element, classCandidate.element as PsiClass)
    return getConstructorCandidates(ref, classCandidate, types).toList()
  }

  private fun inferClassCandidate(ref: GrCodeReferenceElement): GroovyResolveResult? {
    val classResults = ref.multiResolve(false)
    for (result in classResults) {
      if (result.element is PsiClass) {
        return result
      }
    }
    return null
  }
}
