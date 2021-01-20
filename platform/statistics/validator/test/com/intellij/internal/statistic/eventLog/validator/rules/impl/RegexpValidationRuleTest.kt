// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules.impl

import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

class RegexpValidationRuleTest {
  @Test
  fun test_regex_escapes() {
    val foo = "[aa] \\ \\p{Lower} (a|b|c) [a-zA-Z_0-9] X?+ X*+ X?? [\\p{L}&&[^\\p{Lu}]] "
    val pattern = Pattern.compile(RegexpValidationRule.escapeText(foo))
    assertTrue(pattern.matcher(foo).matches())
  }
}