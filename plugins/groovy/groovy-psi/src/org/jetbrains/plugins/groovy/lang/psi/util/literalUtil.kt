// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.psi.util.PsiLiteralUtil.parseDigits
import com.intellij.psi.util.PsiLiteralUtil.parseIntegerNoPrefix
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import java.math.BigDecimal

fun parseInteger(text: String): Int? {
  try {
    if (text.getOrNull(0) == '0') {
      val parsed = when (text.getOrNull(1)) {
        'x', 'X' -> parseDigits(text.substring(2), 4, 32)
        'b', 'B' -> parseDigits(text.substring(2), 1, 32)
        else -> parseDigits(text, 3, 32)
      }
      return Integer.valueOf(parsed.toInt())
    }
    else {
      return parseIntegerNoPrefix(text)
    }
  }
  catch (e: NumberFormatException) {
    return null
  }
}

private val zeros = setOf(0, 0.toLong(), 0.toFloat(), 0.toDouble(), 0.toBigInteger())

fun GrExpression?.isZero(): Boolean {
  if (this !is GrLiteral) {
    return false
  }
  val value = value
  return value in zeros || value is BigDecimal && value.compareTo(BigDecimal.ZERO) == 0
}
