// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.completion.ml.tracker

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


@TestApplication
class PrefixChangeListenerTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture
  private val lookup get() = completion.getLookup()!!

  private val beforeChange = mutableListOf<LookupElement>()
  private val afterChange = mutableListOf<LookupElement>()
  private val lastLookupState = mutableListOf<LookupElement>()

  @BeforeEach
  fun setUp() {
    setupCompletionContext(myFixture)
  }

  @Test
  fun `test prefix change listener`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
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
    }, myFixture.testRootDisposable)

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
