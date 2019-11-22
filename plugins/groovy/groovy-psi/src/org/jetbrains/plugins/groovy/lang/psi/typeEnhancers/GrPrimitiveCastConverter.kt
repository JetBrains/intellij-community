// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiType.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.ERROR
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult.OK
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypeConstants.*
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrCharConverter.checkSingleSymbolLiteral

class GrPrimitiveCastConverter : GrTypeConverter() {

  private companion object {
    fun PsiType.unbox() = PsiPrimitiveType.getUnboxedType(this)
    val numericRanks = arrayOf(
      BYTE_RANK, SHORT_RANK, INTEGER_RANK, LONG_RANK, BIG_INTEGER_RANK, BIG_DECIMAL_RANK, FLOAT_RANK, DOUBLE_RANK
    )
  }

  override fun isApplicableTo(position: Position): Boolean = position == Position.EXPLICIT_CAST

  override fun isConvertible(lType: PsiType, rType: PsiType, position: Position, context: GroovyPsiElement): ConversionResult? {
    if (lType.unbox() == rType) return OK // boxing
    if (rType.unbox() == lType) return OK // unboxing

    if (rType == VOID) return ERROR
    if (lType.equalsToText(JAVA_LANG_OBJECT) || rType.equalsToText(JAVA_LANG_OBJECT)) return OK
    if (lType == VOID) return ERROR
    if (rType == NULL) return if (lType == BOOLEAN || lType !is PsiPrimitiveType) return OK else ERROR
    if (lType.unbox() == VOID || rType.unbox() == VOID) return ERROR
    if (lType == BOOLEAN || lType.unbox() == BOOLEAN || rType == BOOLEAN || rType.unbox() == BOOLEAN) return ERROR
    if (lType.unbox() == CHAR || rType.unbox() == CHAR) return ERROR

    val lRank = getTypeRank(lType)
    if (rType == CHAR) return if (lRank in numericRanks) OK else ERROR
    val rRank = getTypeRank(rType)
    if (lType == CHAR) return if (checkSingleSymbolLiteral(context) || rType is PsiPrimitiveType && rRank in numericRanks) OK else ERROR

    return if (lRank in numericRanks && rRank in numericRanks) OK else null
  }
}