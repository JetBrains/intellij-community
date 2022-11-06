package org.jetbrains.completion.full.line.java.features

import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

class StepByStepCompletionTests : FullLineCompletionTestCase() {
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
}
