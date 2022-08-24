package org.jetbrains.completion.full.line

import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase


class StepByStepCompletionTests : FullLineCompletionTestCase() {
  fun `test with tab`() = stepByStepByText("Python") {
    withSuggestion("def main(args=None, **kwargs):") {
      tab("def ")
      tab("main(")
      tab("args=")
      tab("None, ")
      tab("**kwargs)")
      tab(":")
    }
  }

  fun `test with enter`() = stepByStepByText("Python") {
    withSuggestion("def main(args=None, **kwargs):") {
      tab("def ")
      tab("main(")
      enter("args=None, **kwargs):")
    }
  }

  fun `test with multiple suggestions`() = stepByStepByText("Python") {
    withSuggestion("def main(args=None, **kwargs):") {
      tab("def ")
      tab("main(", nextSuggestion = "):")
      enter("):")
    }
  }

  fun `test in string`() = stepByStepByText("Python", "str = \":<caret>") {
    withSuggestion("func :`prep_param_lists` word.\"") {
      tab("func")
      tab(" :`prep_param_lists")
      tab("` word")
      enter(".\"")
    }
  }

  fun `test with tabbed string`() = stepByStepByText("Python") {
    withSuggestion("str = \"|Simple string!\" + var") {
      tab("str =")
      tab(" \"|Simple string!\" ")
      tab("+ ")
      tab("var")
    }
  }

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

  fun `test with unclosed pars`() {
    stepByStepByText("JAVA", " <caret>") {
      withSuggestion("class Test(") {
        pickingSuggestion += ")"
        tab("class ")
        tab("Test(")
      }
    }
    assertEquals(myFixture.file.text, " class Test()")
  }

  fun `test after dot`() = stepByStepByText("Python") {
    withSuggestion("app.register_blueprint") {
      tab("app.")
      tab("register_blueprint")
    }
  }

  fun `test simple words`() = stepByStepByText("Python") {
    withSuggestion("simple next word") {
      tab("simple ")
      tab("next ")
      tab("word")
    }
  }

  fun `test leading whitespace`() = stepByStepByText("Python", lookupShownAfter = true) {
    withSuggestion("text next") {
      tab("text ")
    }
  }

  fun `test leading whitespaces`() = stepByStepByText("Python", lookupShownAfter = true) {
    withSuggestion("text next") {
      tab("text ")
    }
  }

  fun `test leading symbols`() = stepByStepByText("Python", lookupShownAfter = true) {
    withSuggestion(":// text next") {
      tab(":// ")
      tab("text ")
    }
  }

  fun `test leading symbols and spaces`() = stepByStepByText("Python", lookupShownAfter = true) {
    withSuggestion("://   text next") {
      tab("://   ")
      tab("text ")
    }
  }


  fun `test leading underscore`() = stepByStepByText("Python", lookupShownAfter = true) {
    withSuggestion("_text next") {
      tab("_text ")
    }
  }

  fun `test leading dot`() = stepByStepByText("Python", lookupShownAfter = true) {
    withSuggestion(".text next") {
      tab(".text ")
    }
  }

  fun `test simple JavaScript`() = stepByStepByText("JavaScript") {
    withSuggestion("let arr = [1, 2, 3]") {
      tab("let ")
      tab("arr =")
      tab(" [1")
      tab(", 2")
      enter(", 3]")
    }
  }

  fun `test simple TypeScript`() = stepByStepByText("TypeScript") {
    withSuggestion("var str: string = \"Hello world\"") {
      tab("var ")
      tab("str: ")
      tab("string =")
      enter(" \"Hello world\"")
    }
  }
}

