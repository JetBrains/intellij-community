// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.text.TextProblem
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun assertIsEmpty(collection: Collection<*>) {
  assertTrue { collection.isEmpty() }
}

internal fun TextProblem.assertTypoIs(range: IntRange, fixes: List<String> = emptyList()) {
  assertEquals(range, highlightRanges[0].startOffset until highlightRanges.last().endOffset)
  assertTrue { corrections.containsAll(fixes) }
}

