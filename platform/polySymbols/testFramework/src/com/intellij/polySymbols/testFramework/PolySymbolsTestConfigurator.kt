package com.intellij.polySymbols.testFramework

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

fun CodeInsightTestFixture.configure(configurator: PolySymbolsTestConfigurator) {
  configurator.configure(this)
}

interface PolySymbolsTestConfigurator {
  fun configure(fixture: CodeInsightTestFixture)

  /**
   * Names the files in the project root that the directory comparison of a test with `checkResult` skips.
   * A configurator names a file that [configure] created when [goldDir] has no file with that name.
   * The comparison skips the file. The configurator does not delete it from the project.
   */
  fun rootFilesToSkipInDirectoryComparison(fixture: CodeInsightTestFixture, goldDir: VirtualFile): Collection<String> =
    emptyList()
}