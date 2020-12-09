// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PrecisionUtil")
@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.jetbrains.plugins.groovy.codeInspection.type

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_MINUS
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_PLUS
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypeConstants.*
import java.math.BigDecimal
import java.math.BigInteger
import java.lang.Double as JDouble
import java.lang.Float as JFloat
import java.lang.Integer as JInteger
import java.lang.Long as JLong
import java.lang.Short as JShort

//see org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.checkPossibleLooseOfPrecision()
fun isPossibleLooseOfPrecision(targetType: PsiType, actualType: PsiType, expression: GrExpression): Boolean {
  val targetRank = getTypeRank(targetType)
  val actualRank = getTypeRank(actualType)
  if (targetRank == CHARACTER_RANK || actualRank == CHARACTER_RANK || targetRank == BIG_DECIMAL_RANK) return false
  if (actualRank == 0 || targetRank == 0 || actualRank <= targetRank) return false

  val value = extractLiteralValue(expression) ?: return true
  return when (targetRank) {
    BYTE_RANK -> {
      val byteVal = value.toByte()
      when (value) {
        is Short -> JShort.valueOf(byteVal.toShort()) != value
        is Int -> JInteger.valueOf(byteVal.toInt()) != value
        is Long -> JLong.valueOf(byteVal.toLong()) != value
        is Float -> !JFloat.valueOf(byteVal.toFloat()).equals(value) //https://kotlinlang.org/docs/reference/equality.html#floating-point-numbers-equality
        else -> JDouble.valueOf(byteVal.toDouble()) != value
      }
    }
    SHORT_RANK -> {
      val shortVal = value.toShort()
      when (value) {
        is Int -> JInteger.valueOf(shortVal.toInt()) != value
        is Long -> JLong.valueOf(shortVal.toLong()) != value
        is Float -> !JFloat.valueOf(shortVal.toFloat()).equals(value)
        else -> JDouble.valueOf(shortVal.toDouble()) != value
      }
    }
    INTEGER_RANK -> {
      val intVal = value.toInt()
      when (value) {
        is Long -> JLong.valueOf(intVal.toLong()) != value
        is Float -> !JFloat.valueOf(intVal.toFloat()).equals(value)
        else -> JDouble.valueOf(intVal.toDouble()) != value
      }
    }
    LONG_RANK -> {
      val longVal = value.toLong()
      when (value) {
        is Float -> !JFloat.valueOf(longVal.toFloat()).equals(value)
        else -> JDouble.valueOf(longVal.toDouble()) != value
      }
    }
    FLOAT_RANK -> {
      val floatVal = value.toFloat()
      JDouble.valueOf(floatVal.toDouble()) != value
    }
    else -> false
  }
}

private fun extractLiteralValue(expression: GrExpression?): Number? {
  var negativeNumber = false
  var valueExpression = expression
  if (expression is GrUnaryExpression) {
    val operationTokenType = expression.operationTokenType
    if (operationTokenType in arrayOf(T_PLUS, T_MINUS)) {
      negativeNumber = operationTokenType == T_MINUS
      valueExpression = expression.operand
    }
  }

  val value = (valueExpression as? GrLiteral)?.value as? Number ?: return null
  return if (negativeNumber) commonNegate(value) else value
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
