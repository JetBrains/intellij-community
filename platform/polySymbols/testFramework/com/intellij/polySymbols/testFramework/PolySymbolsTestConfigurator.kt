package com.intellij.polySymbols.testFramework

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

fun CodeInsightTestFixture.configure(configurator: PolySymbolsTestConfigurator) {
  configurator.configure(this)
}

interface PolySymbolsTestConfigurator {
  fun configure(fixture: CodeInsightTestFixture)

  fun beforeDirectoryComparison(fixture: CodeInsightTestFixture, resultsDir: VirtualFile, goldDir: VirtualFile) {
  }
}