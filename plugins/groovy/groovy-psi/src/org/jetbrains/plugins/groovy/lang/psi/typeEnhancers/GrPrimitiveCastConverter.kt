/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  override fun isApplicableTo(position: ApplicableTo): Boolean = position == ApplicableTo.EXPLICIT_CAST

  override fun isConvertibleEx(lType: PsiType, rType: PsiType, context: GroovyPsiElement, position: ApplicableTo): ConversionResult? {
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