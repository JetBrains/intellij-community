// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import org.jetbrains.plugins.groovy.intentions.style.inference.search.searchForInnerReferences
import org.jetbrains.plugins.groovy.intentions.style.inference.search.searchForOuterReferences
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

data class SignatureInferenceOptions(val shouldUseReducedScope : Boolean,
                                     val signatureInferenceContext: SignatureInferenceContext)

class SignatureInferenceEnvironment(originalMethod: GrMethod,
                                    searchScope: SearchScope,
                                    val signatureInferenceContext: SignatureInferenceContext,
                                    private val isEmpty: Boolean = false) {
  private val outerCalls: Lazy<List<PsiReference>> = lazy(LazyThreadSafetyMode.NONE) {
    searchForOuterReferences(originalMethod, searchScope).sortedBy { it.element.textOffset }
  }

  fun getAllCallsToMethod(virtualMethod: GrMethod): List<PsiReference> =
    if (isEmpty) emptyList() else outerCalls.value + searchForInnerReferences(virtualMethod)
}

open class SignatureInferenceContext(val ignored: List<GrMethod>) {

  open fun PsiClassType.getTypeArguments(): Array<PsiType?> = parameters

  open fun filterType(type: PsiType, context: PsiElement): PsiType {
    return if (type is PsiPrimitiveType) {
      type.getBoxedType(context) ?: type
    }
    else {
      type
    }
  }

  /**
   * Computes type of expression that is written in code. Avoids invocation of complex inference
   */
  fun GrExpression.staticType(): PsiType? {
    return when (this) {
      is GrMethodCall -> {
        val resolveResults = multiResolve(false)
        val types = resolveResults.mapNotNull { getStaticReturnType(it as? GroovyMethodResult, this) }
        if (types.isEmpty()) {
          return type
        }
        TypesUtil.getLeastUpperBoundNullable(types, this.manager)
      }
      else -> type
    }
  }

  open fun ignoreMethod(method: GrMethod): SignatureInferenceContext {
    return SignatureInferenceContext(listOf(method, *ignored.toTypedArray()))
  }

  open val allowedToProcessReturnType: Boolean = true

  open val allowedToResolveOperators: Boolean = true
}

object DefaultInferenceContext : SignatureInferenceContext(emptyList())

class ClosureIgnoringInferenceContext(private val manager: PsiManager, ignored: List<GrMethod> = emptyList()) : SignatureInferenceContext(
  ignored) {
  override fun PsiClassType.getTypeArguments(): Array<PsiType?> {
    return if (this.equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      arrayOf(PsiWildcardType.createUnbounded(manager))
    }
    else {
      this.parameters
    }
  }

  override fun filterType(type: PsiType, context: PsiElement): PsiType {
    if (type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      return GroovyPsiElementFactory.getInstance(context.project).createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)
    }
    return super.filterType(type, context)
  }

  override fun ignoreMethod(method: GrMethod): SignatureInferenceContext {
    return ClosureIgnoringInferenceContext(manager, listOf(method, *ignored.toTypedArray()))
  }

  override val allowedToProcessReturnType: Boolean = false

  override val allowedToResolveOperators: Boolean = false
}

private fun getStaticReturnType(result: GroovyMethodResult?, context: GrExpression): PsiType? {
  result ?: return null
  val unprocessedReturnType = result.candidate?.method?.returnType
  val substitutor = result.substitutor
  return TypesUtil.substituteAndNormalizeType(unprocessedReturnType, substitutor, result.spreadState, context)
}