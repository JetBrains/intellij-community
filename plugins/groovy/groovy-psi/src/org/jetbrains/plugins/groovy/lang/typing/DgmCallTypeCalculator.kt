// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION
import com.intellij.psi.util.InheritanceUtil.isInheritor
import com.intellij.psi.util.PsiUtil.extractIterableTypeParameter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.CollectionUtil.createSimilarCollection
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_GROOVY_METHODS
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

class DgmCallTypeCalculator : GrCallTypeCalculator {

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    if (GroovyConfigUtils.getInstance().isVersionAtLeast(context, GroovyConfigUtils.GROOVY2_4)) return null

    if (arguments == null || arguments.isEmpty()) {
      return null
    }
    if (method.containingClass?.qualifiedName != DEFAULT_GROOVY_METHODS) {
      return null
    }

    val methodName = method.name
    if (methodName == FIND) {
      return (arguments.first().type as? PsiArrayType)?.componentType
    }

    if (methodName !in interestingNames || !isSimilarCollectionReturner(method)) {
      return null
    }

    val receiverType = arguments.first().type
    var itemType = getItemType(receiverType)
    if (FLATTEN == methodName && itemType != null) {
      while (true) {
        itemType = getItemType(itemType) ?: break
      }
    }

    return createSimilarCollection(receiverType, context.project, itemType)
  }

  companion object {

    @NlsSafe
    private const val FIND = "find"
    @NlsSafe
    private const val FLATTEN = "flatten"

    @NonNls
    private val interestingNames = setOf(
      "unique",
      "findAll",
      "grep",
      "collectMany",
      "split",
      "plus",
      "intersect",
      "leftShift"
    )

    private fun isSimilarCollectionReturner(method: PsiMethod): Boolean {
      val receiverParameter = method.parameterList.parameters.firstOrNull() ?: return false
      val receiverParameterType = receiverParameter.type
      if (receiverParameterType !is PsiArrayType && !isInheritor(receiverParameterType, JAVA_UTIL_COLLECTION)) {
        return false
      }

      val returnType = method.returnType as? PsiClassType ?: return false
      return returnType.resolve()?.qualifiedName == JAVA_UTIL_COLLECTION
    }

    private fun getItemType(type: PsiType?): PsiType? {
      return (type as? PsiArrayType)?.componentType
             ?: extractIterableTypeParameter(type, true)
    }
  }
}
