// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import com.intellij.psi.CommonClassNames.JAVA_LANG_LONG
import com.intellij.psi.PsiType
import org.codehaus.groovy.syntax.Numbers
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.NUM_INT
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_MATH_BIG_INTEGER
import java.math.BigDecimal
import java.math.BigInteger

internal fun parseInteger(literalText: String): Number? {
  return try {
    Numbers.parseInteger(literalText)
  }
  catch (e: NumberFormatException) {
    null
  }
}

internal fun getLiteralType(literal: GrLiteral): PsiType? {
  val elemType = GrLiteralImpl.getLiteralType(literal)
  if (elemType === GroovyElementTypes.KW_NULL) {
    return PsiType.NULL
  }
  val integerLiteralWithoutSuffix = elemType == NUM_INT && literal.text.let { text ->
    !text.hasIntegerSuffix()
  }
  val fqn = if (integerLiteralWithoutSuffix) {
    when (literal.value) {
      is Int -> JAVA_LANG_INTEGER
      is Long -> JAVA_LANG_LONG
      is BigInteger -> JAVA_MATH_BIG_INTEGER
      else -> return null
    }
  }
  else {
    TypesUtil.getBoxedTypeName(elemType) ?: return null
  }
  return TypesUtil.createTypeByFQClassName(fqn, literal)
}

private fun String.hasIntegerSuffix(): Boolean = endsWith('i') || endsWith('I')

private val zeros = setOf(0, 0.toLong(), 0.toFloat(), 0.toDouble(), 0.toBigInteger())

fun GrExpression?.isZero(): Boolean {
  if (this !is GrLiteral) {
    return false
  }
  val value = value
  return value in zeros || value is BigDecimal && value.compareTo(BigDecimal.ZERO) == 0
}
