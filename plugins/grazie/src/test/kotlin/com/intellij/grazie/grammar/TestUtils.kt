// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun assertIsEmpty(collection: Collection<*>) {
  assertTrue { collection.isEmpty() }
}

internal fun LanguageToolChecker.Problem.assertTypoIs(range: IntRange, fixes: List<String> = emptyList()) {
  assertEquals(range, highlightRange.startOffset until highlightRange.endOffset)
  assertTrue { corrections.containsAll(fixes) }
}

