// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.CommonClassNames.JAVA_UTIL_ITERATOR
import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil.isInheritor
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createGenericType
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer.getEntryForMap
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_GROOVY_METHODS
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

class DgmIteratorCallTypeCalculator : GrCallTypeCalculator {

  companion object {
    @NlsSafe
    private const val ITERATOR = "iterator"
  }

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    if (method.name != ITERATOR || method.containingClass?.qualifiedName != DEFAULT_GROOVY_METHODS) return null

    val receiverType = arguments?.singleOrNull()?.type as? PsiClassType ?: return null
    if (!isInheritor(receiverType, JAVA_UTIL_MAP)) return null

    val entryType = getEntryForMap(receiverType, context.project, context.resolveScope)
    return createGenericType(JAVA_UTIL_ITERATOR, context, entryType)
  }
}
