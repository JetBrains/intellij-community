package org.jetbrains.completion.full.line.kotlin.supporters.features

import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

class StepByStepCompletionTests : FullLineCompletionTestCase() {
  fun `test simple Kotlin`() = stepByStepByText("kotlin") {
    withSuggestion("val item = listOf(1, 2).let { it.first() }") {
      tab("val ")
      tab("item =")
      tab(" listOf(")
      tab("1, ")
      tab("2).")
      tab("let {")
      tab(" it.")
      enter("first() }")
    }
  }

  fun `test continues with dot`() = stepByStepByText("kotlin") {
    withSuggestion("val string = a.str.filter { it.isDigit() }") {
      tab("val ")
      tab("string =")
      tab(" a.")
      tab("str.")
      tab("filter {")
      tab(" it.")
      tab("isDigit()")
      enter(" }")
    }
  }

  fun `test space-char-space`() = stepByStepByText("kotlin") {
    withSuggestion("a. * b") {
      tab("a. ")
      tab("* b")
    }
  }

  fun `test space-char-space with prefix`() = stepByStepByText("kotlin", "a<caret>") {
    withSuggestion("a { it }") {
      tab(" { ")
      tab("it }")
    }
  }
}

