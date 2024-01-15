// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionBuilder
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Key
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(JUnit4::class)
internal class InlineCompletionOvertypingTest : InlineCompletionTestCase() {

  private lateinit var provider: GradualMultiSuggestInlineCompletionProvider

  private fun registerSuggestion(suggestionBuilder: suspend InlineCompletionSuggestionBuilder.() -> Unit) {
    provider = GradualMultiSuggestInlineCompletionProvider(suggestionBuilder)
    InlineCompletionHandler.registerTestHandler(provider, testRootDisposable)
  }

  @Test
  fun `test over typing of single suggestion`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("first "))
        emit(InlineCompletionGrayTextElement("second "))
        emit(InlineCompletionGrayTextElement(" third"))
      }
    }
    callInlineCompletion()
    provider.computeNextElements(3)
    delay()
    assertInlineElements { gray("first "); gray("second "); gray(" third") }

    for (c in "firs") {
      typeChar(c)
    }
    assertInlineElements { gray("t "); gray("second "); gray(" third") }

    for (c in "t sec") {
      typeChar(c)
    }
    assertInlineElements { gray("ond "); gray(" third") }
    assertFileContent("first sec<caret>")

    assertSessionSnapshot(
      nonEmptyVariants = 1..1,
      activeIndex = 0,
      ExpectedVariant.computed("ond ", " third")
    )

    for (c in "ond  ") {
      typeChar(c)
    }
    assertInlineElements { gray("third") }
    assertFileContent("first second  <caret>")
    insert()
    assertInlineHidden()
    assertFileContent("first second  third<caret>")
  }

  @Test
  fun `test type single suggestion completely`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("one"))
        emit(InlineCompletionGrayTextElement("-two"))
        emit(InlineCompletionGrayTextElement("-three"))
      }
    }
    callInlineCompletion()
    provider.computeNextElements(3)
    delay()
    InlineCompletionHandler.unRegisterTestHandler()

    assertInlineElements { gray("one"); gray("-two") ; gray("-three") }

    for (c in "one-two-thre") {
      typeChar(c)
    }
    assertInlineElements { gray("e") }
    assertFileContent("one-two-thre<caret>")
    typeChar('e')
    assertInlineHidden()
    assertFileContent("one-two-three<caret>")
  }

  @Test
  fun `test over typing of all variants`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("fun main("))
        emit(InlineCompletionGrayTextElement("args: Array<String>)"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("fun minus("))
        emit(InlineCompletionGrayTextElement("p1: Int, p2: Int)"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("fun plus(p1: Int, p2: Int)"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("fun prod("))
        emit(InlineCompletionGrayTextElement("p1: Int, p2: Int"))
        emit(InlineCompletionGrayTextElement(")"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("fun max(p1: Int, p2: Int)"))
      }
    }
    callInlineCompletion()
    provider.computeNextElements(9)
    delay()

    nextVariant()
    nextVariant()
    assertInlineRender("fun plus(p1: Int, p2: Int)")
    typeChars("fun ")
    assertAllVariants(
      "plus(p1: Int, p2: Int)",
      "prod(p1: Int, p2: Int)",
      "max(p1: Int, p2: Int)",
      "main(args: Array<String>)",
      "minus(p1: Int, p2: Int)",
    )
    assertInlineRender("plus(p1: Int, p2: Int)")

    typeChar('m')
    assertAllVariants(
      "ax(p1: Int, p2: Int)",
      "ain(args: Array<String>)",
      "inus(p1: Int, p2: Int)",
    )

    assertSessionSnapshot(
      nonEmptyVariants = 3..3,
      activeIndex = 4,
      ExpectedVariant.computed("ain(", "args: Array<String>)"),
      ExpectedVariant.computed("inus(", "p1: Int, p2: Int)"),
      ExpectedVariant.invalidated(),
      ExpectedVariant.invalidated(),
      ExpectedVariant.computed("ax(p1: Int, p2: Int)")
    )

    nextVariant()
    nextVariant()
    assertInlineRender("inus(p1: Int, p2: Int)")

    typeChar('a')
    assertAllVariants("x(p1: Int, p2: Int)", "in(args: Array<String>)")
    typeChar('i')
    assertAllVariants("n(args: Array<String>)")
    assertFileContent("fun mai<caret>")
    insert()
    assertInlineHidden()
    assertFileContent("fun main(args: Array<String>)<caret>")
  }

  @Test
  fun `test complete typing of one suggestion clears session`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant { emit(InlineCompletionGrayTextElement("abcde")) }
      variant { emit(InlineCompletionGrayTextElement("abc")) }
      variant { emit(InlineCompletionGrayTextElement("abcd")) }
    }
    callInlineCompletion()
    provider.computeNextElements(3)
    delay()
    InlineCompletionHandler.unRegisterTestHandler()

    prevVariant()
    prevVariant()
    assertAllVariants("abc", "abcd", "abcde")
    typeChars("ab")
    assertAllVariants("c", "cd", "cde")

    typeChar('c')
    assertInlineHidden()
    assertFileContent("abc<caret>")
  }

  @Test
  fun `test over type skip element`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE, "<caret>(")
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("before"))
        emit(InlineCompletionSkipTextElement("("))
        emit(InlineCompletionGrayTextElement("after"))
      }
    }
    callInlineCompletion()
    provider.computeNextElements(3)
    delay()
    InlineCompletionHandler.unRegisterTestHandler()

    typeChars("before")
    assertInlineElements { skip("("); gray("after") }

    assertSessionSnapshot(1..1, 0, ExpectedVariant.computed("(", "after"))

    typeChar('(')
    assertInlineHidden()
    assertFileContent("before(<caret>(")
  }

  @Test
  fun `test over typing while elements are computed`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("12"))
        emit(InlineCompletionGrayTextElement("34"))
        emit(InlineCompletionGrayTextElement("56"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("12"))
        emit(InlineCompletionGrayTextElement("35"))
        emit(InlineCompletionGrayTextElement("12"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("12"))
        emit(InlineCompletionGrayTextElement("56"))
        emit(InlineCompletionGrayTextElement("34"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("11"))
        emit(InlineCompletionGrayTextElement("34"))
      }
    }
    callInlineCompletion()
    provider.computeNextElements(5)
    typeChars("12")
    assertAllVariants("3456", "35")

    assertSessionSnapshot(
      nonEmptyVariants = 2..2,
      activeIndex = 0,
      ExpectedVariant.computed("34", "56"),
      ExpectedVariant.inProgress("35"),
      ExpectedVariant.invalidated(),
      ExpectedVariant.invalidated()
    )

    typeChars("34")
    assertAllVariants("56")

    assertSessionSnapshot(
      nonEmptyVariants = 1..1,
      activeIndex = 0,
      ExpectedVariant.computed("56"),
      *Array(3) { ExpectedVariant.invalidated() }
    )

    provider.computeNextElements(6, await = false)
    delay()
    assertAllVariants("56")
    insert()
    assertInlineHidden()
    assertFileContent("123456<caret>")
  }

  @Test
  fun `test computation of element is canceled after its invalidation`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("123"))
        emit(InlineCompletionGrayTextElement("456"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("124"))
        emit(InlineCompletionGrayTextElement("456"))
        throw IllegalStateException("not expected")
      }
    }
    callInlineCompletion()
    provider.computeNextElements(3)
    nextVariant()
    assertInlineRender("124")
    typeChars("12")
    assertAllVariants("4", "3456")

    typeChar('3')
    provider.computeNextElements(1, await = false)
    delay()

    assertAllVariants("456")
    insert()
    assertInlineHidden()
    assertFileContent("123456<caret>")
  }

  @Test
  fun `test new element appears while over typing`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant {
        emit(InlineCompletionGrayTextElement("1234"))
        emit(InlineCompletionGrayTextElement("5678"))
      }
      variant {
        emit(InlineCompletionGrayTextElement("1234"))
      }
    }
    callInlineCompletion()
    provider.computeNextElements(1)
    typeChars("123")
    assertAllVariants("4")
    provider.computeNextElement()
    delay()
    assertAllVariants("45678")

    assertSessionSnapshot(
      nonEmptyVariants = 1..1,
      activeIndex = 0,
      ExpectedVariant.computed("4", "5678"),
      ExpectedVariant.invalidated()
    )

    insert()
    assertInlineHidden()
    assertFileContent("12345678<caret>")
  }

  @Test
  fun `test completely typed another variant disappears`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant { emit(InlineCompletionGrayTextElement("1234")) }
      variant { emit(InlineCompletionGrayTextElement("12345678")) }
    }
    callInlineCompletion()
    provider.computeNextElements(2)
    delay()

    prevVariant()
    assertInlineRender("12345678")
    typeChars("123")
    assertAllVariants("45678", "4")

    typeChar('4')
    assertAllVariants("5678")

    assertSessionSnapshot(
      nonEmptyVariants = 1..1,
      activeIndex = 1,
      ExpectedVariant.invalidated(),
      ExpectedVariant.computed("5678")
    )
  }

  @Test
  fun `test incorrect over typing clears session`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant { emit(InlineCompletionGrayTextElement("1234")) }
      variant { emit(InlineCompletionGrayTextElement("1324")) }
    }
    callInlineCompletion()
    provider.computeNextElements(2)
    delay()
    InlineCompletionHandler.unRegisterTestHandler()

    typeChar('1')
    assertAllVariants("234", "324")
    typeChar('2')
    assertAllVariants("34")
    typeChar('4')
    assertInlineHidden()
  }

  @Test
  fun `test typing and lookup when variants are not ready`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    val isReady = AtomicBoolean(false)
    val reachedVariants = AtomicBoolean(false)
    registerSuggestion {
      reachedVariants.set(true)
      while (!isReady.get()) {
        yield()
      }
      variant { emit(InlineCompletionGrayTextElement("123")) }
    }
    callInlineCompletion()
    while (!reachedVariants.get()) {
      yield()
    }
    InlineCompletionHandler.unRegisterTestHandler()

    assertSessionIsNotActive()

    // Lookup doesn't change anything
    fillLookup("1", "2", "3")
    createLookup()
    assertNotNull(fixture.lookup)
    pickLookupElement("1")
    assertInlineRender("")

    // It must clear session
    typeChar('1')
    assertInlineHidden()
    isReady.set(true)
    provider.computeNextElements(1, await = false)
    delay()
    assertInlineHidden()
  }
  
  @Test
  fun `test order of variants choices after invalidation`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    registerSuggestion {
      variant { emit(InlineCompletionGrayTextElement("1234333")) }
      variant { emit(InlineCompletionGrayTextElement("1234444")) }
      variant { }
      variant { emit(InlineCompletionGrayTextElement("1244444")) }
      variant { }
      variant { emit(InlineCompletionGrayTextElement("1234555")) }
      variant { emit(InlineCompletionGrayTextElement("1234567")) }
    }
    callInlineCompletion()
    provider.computeNextElements(5)
    delay()
    nextVariant()
    nextVariant()
    assertAllVariants("1244444", "1234555", "1234567", "1234333", "1234444")
    typeChars("12")
    assertAllVariants("44444", "34555", "34567", "34333", "34444")

    typeChar('3')
    assertAllVariants("4555", "4567", "4333", "4444")

    typeChar('4')
    assertAllVariants("555", "567", "333", "444")

    assertSessionSnapshot(
      nonEmptyVariants = 4..4,
      activeIndex = 5,
      ExpectedVariant.computed("333"),
      ExpectedVariant.computed("444"),
      ExpectedVariant.invalidated(),
      ExpectedVariant.invalidated(),
      ExpectedVariant.invalidated(),
      ExpectedVariant.computed("555"),
      ExpectedVariant.computed("567")
    )

    nextVariant()
    assertAllVariants("567", "333", "444", "555")

    typeChar('5')
    assertAllVariants("67", "55")

    typeChar('6')
    assertAllVariants("7")

    insert()
    assertFileContent("1234567<caret>")
  }

  @Test
  fun `test data consistent when over typing`() = myFixture.testInlineCompletion {
    init(PlainTextFileType.INSTANCE)
    val key = Key.create<Int>("inline.completion.test.key")
    registerSuggestion {
      variant { data ->
        data.putUserData(key, 42)
        emit(InlineCompletionGrayTextElement("1234"))
      }
      variant { data ->
        data.putUserData(key, 10)
        emit(InlineCompletionGrayTextElement("123"))
      }
    }
    callInlineCompletion()
    provider.computeNextElements(2)
    delay()

    suspend fun assertData(variantIndex: Int, value: Int?) {
      assertEquals(value, getVariant(variantIndex).data.getUserData(key))
      assertEquals(value, assertContextExists().getUserData(key))
    }

    assertData(0, 42)

    typeChar('1')
    assertData(0, 42)
    nextVariant()
    assertData(1, 10)
    nextVariant()
    prevVariant()
    assertData(1, 10)
    nextVariant()
    typeChars("23")
    assertData(0, 42)
    assertInlineRender("4")
  }
}
