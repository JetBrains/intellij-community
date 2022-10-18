package org.jetbrains.completion.full.line.features

import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

abstract class AutoImportTest : FullLineCompletionTestCase() {
  override fun getBasePath() = "testData/completion/features/auto-import"

  /**
   * Testing auto-import feature
   * @param filename file name, to be evaluated
   * @param variant will be shown in popup as full line suggestion
   * @param expectedLine expected import line, which must be added only after auto-import
   */
  protected fun doTest(filename: String, variant: String, expectedLine: String) {
    myFixture.copyDirectoryToProject(getTestName(false), "")
    myFixture.configureByFile(filename)

    // Check that import is missing
    assertFalse("Import is already in file", myFixture.file.text.contains(expectedLine))

    myFixture.completeFullLine(variant)
    myFixture.lookup.currentItem = myFixture.lookupElements?.filterIsInstance<FullLineLookupElement>()?.first()
    myFixture.finishLookup('\n')

    // Check that import was added
    assertTrue("Import was not added", myFixture.file.text.contains(expectedLine))
  }
}
