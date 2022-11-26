package org.jetbrains.completion.full.line.python.features

import org.jetbrains.completion.full.line.python.tests.FullLinePythonCompletionTestCase

class StepByStepCompletionTests : FullLinePythonCompletionTestCase() {
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
}
