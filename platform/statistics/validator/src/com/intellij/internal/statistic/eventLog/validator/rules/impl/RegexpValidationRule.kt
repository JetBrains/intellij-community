// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl

import com.intellij.internal.statistic.eventLog.StatisticsEventEscaper
import com.intellij.internal.statistic.eventLog.util.StringUtil
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.FUSRegexpAwareRule
import com.intellij.internal.statistic.eventLog.validator.rules.PerformanceCareRule
import java.util.regex.Pattern

class RegexpValidationRule(private val regexp: String?) : PerformanceCareRule(), FUSRegexpAwareRule {
  private val myPattern by lazy { computePattern(regexp) }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val pattern: Pattern = myPattern ?: return ValidationResultType.INCORRECT_RULE
    val escaped: String = StatisticsEventEscaper.escapeEventIdOrFieldValue(data)
    if (pattern.matcher(escaped).matches()) {
      return ValidationResultType.ACCEPTED
    }

    // for backward compatibility with rules created before allowed symbols were changed
    val legacyData = StatisticsEventEscaper.cleanupForLegacyRulesIfNeeded(escaped)
    return if (legacyData != null && pattern.matcher(legacyData).matches()) ValidationResultType.ACCEPTED
    else ValidationResultType.REJECTED
  }

  override fun asRegexp(): String {
    return regexp ?: "<invalid>"
  }

  override fun toString(): String {
    return "RegexpValidationRule: myRegexp=" + asRegexp()
  }

  private fun computePattern(regexp: String?): Pattern? {
    if (regexp == null) return null
    return try {
      Pattern.compile(regexp)
    }
    catch (ignored: Exception) {
      null
    }
  }

  companion object {
    private val ESCAPE_FROM = listOf("\\", "[", "]", "{", "}", "(", ")", "-", "^", "*", "+", "?", ".", "|", "$")
    private val ESCAPE_TO = ESCAPE_FROM.map { "\\" + it }

    @JvmStatic
    fun escapeText(text: String): String {
      return StringUtil.replace(text, ESCAPE_FROM, ESCAPE_TO)
    }
  }
}