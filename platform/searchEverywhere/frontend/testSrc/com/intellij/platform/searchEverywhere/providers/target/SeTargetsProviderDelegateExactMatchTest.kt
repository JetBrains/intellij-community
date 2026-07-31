// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SeTargetsProviderDelegate.isExactMatch] — the rule that marks a target as an exact match of what the
 * user typed, so that the result list can keep it above partial/fuzzy siblings (IJPL-248758).
 */
class SeTargetsProviderDelegateExactMatchTest {

  @Test
  fun contributorVerdictIsAlwaysHonored() {
    // IJPL-133399, IJPL-251596: once the contributor reported an exact match, nothing here may revoke it.
    assertTrue(isExactMatch(presentableText = "Unrelated.kt", query = "Foo", fromItem = true))
    assertTrue(isExactMatch(presentableText = "", query = "Foo", fromItem = true))
  }

  @Test
  fun presentableTextEqualToQueryIsExact() {
    // IJPL-55665: typing the whole presentable name marks the target exact, for any provider.
    assertTrue(isExactMatch(presentableText = "MyClass", query = "MyClass"))
    assertTrue(isExactMatch(presentableText = "index.html", query = "index.html", isFile = true))
  }

  @Test
  fun equalityIsCaseSensitive() {
    // Pins current behavior: the matchers that produced the candidates ignore case, this check does not.
    assertFalse(isExactMatch(presentableText = "MyClass", query = "myclass"))
  }

  @Test
  fun fileNameWithoutExtensionIsExactForFileProvider() {
    // IJPL-55732, IJPL-156298: users rarely type the extension, so "Foo" must mark "Foo.kt" exact.
    assertTrue(isExactMatch(presentableText = "Foo.kt", query = "Foo", isFile = true))
  }

  @Test
  fun extensionShortcutAppliesOnlyToTheFileProvider() {
    // A class or symbol whose name happens to start with "Foo." is not an exact match of "Foo".
    assertFalse(isExactMatch(presentableText = "Foo.kt", query = "Foo", isFile = false))
  }

  @Test
  fun extensionShortcutIsOffWhenTheQueryAlreadyContainsADot() {
    // "Foo.k" is a partial extension, not an exact match of "Foo.kt".
    assertFalse(isExactMatch(presentableText = "Foo.kt", query = "Foo.k", isFile = true))
  }

  @Test
  fun extensionShortcutRequiresTheWholeNameBeforeTheDot() {
    // Only a "Foo." prefix counts — a longer name that merely starts with "Foo" must not be marked exact.
    assertFalse(isExactMatch(presentableText = "FooBar.kt", query = "Foo", isFile = true))
    assertFalse(isExactMatch(presentableText = "Foobar", query = "Foo", isFile = true))
  }

  @Test
  fun unrelatedOrBlankQueriesAreNotExact() {
    assertFalse(isExactMatch(presentableText = "Bar.kt", query = "Foo", isFile = true))
    // A blank query must not mark every file as an exact match.
    assertFalse(isExactMatch(presentableText = "Foo.kt", query = "", isFile = true))
    assertFalse(isExactMatch(presentableText = "", query = "Foo", isFile = true))
  }

  /**
   * [queryHasNoExtension] defaults to the same expression the production caller uses, so that the cases above exercise
   * realistic argument combinations unless they override it on purpose.
   */
  private fun isExactMatch(
    presentableText: String,
    query: String,
    fromItem: Boolean = false,
    isFile: Boolean = false,
    queryHasNoExtension: Boolean = !query.contains('.'),
  ): Boolean = SeTargetsProviderDelegate.isExactMatch(
    isExactMatchFromItem = fromItem,
    presentableText = presentableText,
    inputQuery = query,
    isFile = isFile,
    inputQueryHasNoExtension = queryHasNoExtension,
  )
}
