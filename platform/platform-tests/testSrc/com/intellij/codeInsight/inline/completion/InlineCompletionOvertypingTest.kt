// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.impl.GradualMultiSuggestInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionBuilder
import com.intellij.openapi.fileTypes.PlainTextFileType
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
      "inus(p1: Int, p2: Int)",
      "ax(p1: Int, p2: Int)",
      "ain(args: Array<String>)"
    )

    typeChar('a')
    assertAllVariants("in(args: Array<String>)", "x(p1: Int, p2: Int)")
    prevVariant()
    assertInlineRender("x(p1: Int, p2: Int)")
    assertFileContent("fun ma<caret>")
    insert()
    assertInlineHidden()
    assertFileContent("fun max(p1: Int, p2: Int)<caret>")
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
    typeChars("34")
    assertAllVariants("56")

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
    assertAllVariants("4444", "4555", "4567", "4333")

    typeChar('4')
    assertAllVariants("444", "555", "567", "333")

    typeChar('5')
    assertAllVariants("55", "67")

    typeChar('6')
    assertAllVariants("7")

    insert()
    assertFileContent("1234567<caret>")
  }
}
