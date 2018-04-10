// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil.isInheritor
import com.intellij.psi.util.PsiUtil.extractIterableTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType.createTraitType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.getItemType
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil.isTrait

class DefaultSafeCaseTypeCalculator : GrTypeCalculator<GrSafeCastExpression> {

  override fun getType(expression: GrSafeCastExpression): PsiType? {
    val typeElement = expression.castTypeElement ?: return null
    return getTypeFromRawCollectionCast(expression) ?:
           getTraitType(expression) ?:
           typeElement.type
  }

  /**
   * Given an expression `expr as List`.
   * If expr has an array type, then we want to preserve information about component type of this array.
   * For example if expr is of `String[]` type then the type of whole expression will be `List<String>`.
   */
  private fun getTypeFromRawCollectionCast(expression: GrSafeCastExpression): PsiType? {
    val castType = expression.castTypeElement?.type as? PsiClassType ?: return null
    if (!isInheritor(castType, JAVA_UTIL_COLLECTION)) return null
    if (extractIterableTypeParameter(castType, false) != null) return null

    val resolved = castType.resolve() ?: return null
    val typeParameter = resolved.typeParameters.singleOrNull() ?: return null
    val itemType = getItemType(expression.operand.type) ?: return null

    val substitutionMap = mapOf(typeParameter to itemType)
    val factory = JavaPsiFacade.getElementFactory(expression.project)
    val substitutor = factory.createSubstitutor(substitutionMap)
    return factory.createType(resolved, substitutor)
  }

  private fun getTraitType(expression: GrSafeCastExpression): PsiType? {
    val castType = expression.castTypeElement?.type as? PsiClassType ?: return null

    val exprType = expression.operand.type
    if (exprType !is PsiClassType && exprType !is GrTraitType) return null

    val resolved = castType.resolve() ?: return null
    if (!isTrait(resolved)) return null

    return createTraitType(exprType, listOf(castType))
  }
}