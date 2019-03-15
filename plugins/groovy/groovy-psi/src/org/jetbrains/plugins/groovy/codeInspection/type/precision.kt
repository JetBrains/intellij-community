// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypeConstants.*
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl
import java.math.BigDecimal
import java.math.BigInteger

object PrecisionUtil {
  //see org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.checkPossibleLooseOfPrecision()
  fun isPossibleLooseOfPrecision(targetType: PsiType, actualType: PsiType, expression: GrExpression): Boolean {
    val targetRank = getTypeRank(targetType)
    val actualRank = getTypeRank(actualType)
    if (targetRank == CHARACTER_RANK || actualRank == CHARACTER_RANK || targetRank == BIG_DECIMAL_RANK) return false
    if (actualRank == 0 || targetRank == 0 || actualRank <= targetRank) return false


    val value = extractLiteralValue(expression) ?: return true
    when (targetRank) {
      BYTE_RANK -> {
        val byteVal = value.toByte()
        if (value is Short) {
          return java.lang.Short.valueOf(byteVal.toShort()) != value
        }
        if (value is Int) {
          return Integer.valueOf(byteVal.toInt()) != value
        }
        if (value is Long) {
          return java.lang.Long.valueOf(byteVal.toLong()) != value
        }
        return if (value is Float) {
          java.lang.Float.valueOf(byteVal.toFloat()) != value
        }
        else java.lang.Double.valueOf(byteVal.toDouble()) != value
      }
      SHORT_RANK -> {
        val shortVal = value.toShort()
        if (value is Int) {
          return Integer.valueOf(shortVal.toInt()) != value
        }
        if (value is Long) {
          return java.lang.Long.valueOf(shortVal.toLong()) != value
        }
        return if (value is Float) {
          java.lang.Float.valueOf(shortVal.toFloat()) != value
        }
        else java.lang.Double.valueOf(shortVal.toDouble()) != value
      }
      INTEGER_RANK -> {
        val intVal = value.toInt()
        if (value is Long) {
          return java.lang.Long.valueOf(intVal.toLong()) != value
        }
        return if (value is Float) {
          java.lang.Float.valueOf(intVal.toFloat()) != value
        }
        else java.lang.Double.valueOf(intVal.toDouble()) != value
      }
      LONG_RANK -> {
        val longVal = value.toLong()
        return if (value is Float) {
          java.lang.Float.valueOf(longVal.toFloat()) != value
        }
        else java.lang.Double.valueOf(longVal.toDouble()) != value
      }
      FLOAT_RANK -> {
        val floatVal = value.toFloat()
        return java.lang.Double.valueOf(floatVal.toDouble()) != value
      }
      else -> return false
    }
  }

  private fun extractLiteralValue(expression: GrExpression?): Number? {
    var negativeNumber = false
    var valueExpression = expression
    if (expression is GrUnaryExpression) {
      val operationTokenType = expression.operationTokenType

      if (operationTokenType in listOf(GroovyTokenTypes.mPLUS, GroovyTokenTypes.mMINUS)) {
        negativeNumber = operationTokenType == GroovyTokenTypes.mMINUS
        valueExpression = expression.operand
      }
    }

    val literalValue = (valueExpression as? GrLiteralImpl)?.value ?: return null
    return (literalValue as? Number)?.let { if (negativeNumber) commonNegate(it) else it }
  }

  private fun commonNegate(num: Number): Number? {
    return when (num) {
      is Byte -> -num
      is Short -> -num
      is Int -> -num
      is Long -> -num
      is BigInteger -> -num
      is Float -> -num
      is Double -> -num
      is BigDecimal -> -num
      else -> null
    }
  }


}