// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ngram

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.completion.ml.util.CompletionUtil
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class NgramExtractionTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture

  @BeforeEach
  fun setUp() {
    myFixture.addClass("""
interface XRunnable {
  void man();
  void run();
  void cat();
}
""")
  }

  @Test
  fun `test ngram extraction`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    invokeCompletionWithText("class T { void r() { XRunnable x; x.<caret> }; boolean q() { return true; }}")
    myFixture.completeBasic()
    checkExpectedNGram(".")
    checkExpectedNGram("x", ".")
    checkExpectedNGram("(", ")", "{", "XRunnable", "x", ";", "x", ".")
    checkExpectedReversedNGram("}")
    checkExpectedReversedNGram(";", "}")
    checkExpectedReversedNGram("return", "{", ")", "(", "q", "boolean", ";", "}")
  }

  @Test
  fun `test ngram extraction in file beginning`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    invokeCompletionWithText("<caret>class T { void r() { XRunnable x; } }")
    checkExpectedNGram()
  }

  @Test
  fun `test reversed ngram extraction in file end`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    invokeCompletionWithText("class T { void r() { XRunnable x; } }<caret>")
    checkExpectedReversedNGram()
  }

  @Test
  fun `test ngram extraction in empty file`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    invokeCompletionWithText("<caret>")
    checkExpectedNGram()
    checkExpectedReversedNGram()
  }

  private fun invokeCompletionWithText(javaFileText: String) {
    myFixture.configureByText(JavaFileType.INSTANCE, javaFileText)
    myFixture.completeBasic()
  }

  private fun checkExpectedNGram(vararg ngram: String) {
    val parameters = CompletionUtil.getCurrentCompletionParameters()
                     ?: return fail("Completion parameters not found. Session should be started")
    assertThat(NGram.getNGramPrefix(parameters, ngram.size + 1)).isEqualTo(ngram)
  }

  private fun checkExpectedReversedNGram(vararg ngram: String) {
    val parameters = CompletionUtil.getCurrentCompletionParameters()
                     ?: return fail("Completion parameters not found. Session should be started")
    assertThat(NGram.getNGramReversedPostfix(parameters, ngram.size + 1)).isEqualTo(ngram)
  }
}
