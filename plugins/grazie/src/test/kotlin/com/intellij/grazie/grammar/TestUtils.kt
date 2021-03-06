// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.grammar

import com.intellij.grazie.grammar.strategy.GrammarCheckingStrategy
import com.intellij.grazie.utils.withOffset
import com.intellij.psi.PsiElement
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun assertIsEmpty(collection: Collection<*>) {
  assertTrue { collection.isEmpty() }
}

fun Typo.verify(text: String? = null) {
  if (location.pointer != null) location.errorText
  if (location.pointer != null && text != null) {
    //it may work unexpectedly if there is more than one equal element, but it is ok for tests
    val indexOfElement = text.indexOf(location.pointer?.element!!.text)
    assertEquals(location.errorText ?: "", text.subSequence(location.errorRange.withOffset(indexOfElement)))
  }
}

fun Typo.assertTypoIs(range: IntRange, fixes: List<String> = emptyList(), text: String? = null) {
  assertEquals(range, location.errorRange)
  assertTrue { fixes.containsAll(fixes) }

  verify(text)
}

fun GrammarChecker.check(elements: Collection<PsiElement>, strategy: GrammarCheckingStrategy): Set<Typo> {
  return elements.map { check(listOf(it), strategy) }.flatten().toSet()
}
