// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.completion.ml.tracker

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.assertj.core.api.Assertions


class PrefixChangeListenerTest: LightFixtureCompletionTestCase() {

  private val beforeChange = mutableListOf<LookupElement>()
  private val afterChange = mutableListOf<LookupElement>()
  private val lastLookupState = mutableListOf<LookupElement>()

  override fun setUp() {
    super.setUp()
    setupCompletionContext(myFixture)
  }

  fun `test prefix change listener`() {
    myFixture.completeBasic()

    lookup.addPrefixChangeListener(object : PrefixChangeListener {
      override fun afterAppend(c: Char) {
        afterChange.clear()
        afterChange.addAll(lookup.items)
      }

      override fun afterTruncate() {
        afterChange.clear()
        afterChange.addAll(lookup.items)
      }

      override fun beforeTruncate() {
        beforeChange.clear()
        beforeChange.addAll(lookup.items)
      }

      override fun beforeAppend(c: Char) {
        beforeChange.clear()
        beforeChange.addAll(lookup.items)
      }
    }, testRootDisposable)

    lastLookupState.clear()
    lastLookupState.addAll(lookup.items)
    afterChange.clear()
    afterChange.addAll(lastLookupState)

    check { myFixture.type('r') }
    Assertions.assertThat(afterChange.size).isLessThan(beforeChange.size)

    check { myFixture.type('u') }
    Assertions.assertThat(afterChange.size).isLessThanOrEqualTo(beforeChange.size)

    check { myFixture.type('\b') }
    Assertions.assertThat(afterChange.size).isGreaterThanOrEqualTo(beforeChange.size)

    check { myFixture.type('\b') }
    Assertions.assertThat(afterChange.size).isGreaterThan(beforeChange.size)
  }

  private fun check(action: () -> Unit) {
    lastLookupState.clear()
    lastLookupState.addAll(lookup.items)

    Assertions.assertThat(afterChange).isEqualTo(lastLookupState)
    action()
    Assertions.assertThat(beforeChange).isEqualTo(lastLookupState)
  }

}

internal fun setupCompletionContext(fixture: JavaCodeInsightTestFixture) {
  fixture.addClass("""
interface XRunnable {
  void man();
  void run();
  void cat();
  void runnable();
  void rus();
}
""")

  fixture.configureByText(JavaFileType.INSTANCE, "class T { void r() { XRunnable x; x.<caret> } }")
}
