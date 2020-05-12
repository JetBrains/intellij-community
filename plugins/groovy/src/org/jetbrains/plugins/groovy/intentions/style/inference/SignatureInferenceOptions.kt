// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference

import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

data class SignatureInferenceOptions(val searchScope: SearchScope,
                                     val restrictScopeToLocal: Boolean,
                                     val signatureInferenceContext: SignatureInferenceContext,
                                     val calls: Lazy<List<PsiReference>>)

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

  open fun ignoreMethod(method: GrMethod): SignatureInferenceContext {
    return SignatureInferenceContext(listOf(method, *ignored.toTypedArray()))
  }

  open val allowedToProcessReturnType : Boolean = true

  open val allowedToResolveOperators : Boolean = true
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