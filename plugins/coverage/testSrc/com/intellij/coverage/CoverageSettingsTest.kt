// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

class CoverageSettingsTest : CoverageIntegrationBaseTest() {
  fun `test coverage settings are saved correctly`() {
    val defaultSettings = JavaCoverageOptionsProvider.State()
    assertSettings(defaultSettings)

    val settings = JavaCoverageOptionsProvider.getInstance(myProject)
    try {
      val otherSettings = JavaCoverageOptionsProvider.State().apply {
        myRunnerId = CoverageRunner.EP_NAME.extensionList.first { it.id != defaultSettings.myRunnerId }.id
        myBranchCoverage = !defaultSettings.myBranchCoverage
        myTestTracking = !defaultSettings.myTestTracking
        myTestModulesCoverage = !defaultSettings.myTestModulesCoverage
        myIgnoreImplicitConstructors = !defaultSettings.myIgnoreImplicitConstructors
        myCalculateExactHits = !defaultSettings.myCalculateExactHits
      }
      settings.loadState(otherSettings)
      assertSettings(otherSettings)
    } finally {
      // reset to default state
      settings.loadState(defaultSettings)
    }
    assertSettings(defaultSettings)
  }

  private fun assertSettings(expected: JavaCoverageOptionsProvider.State) {
    val settings = JavaCoverageOptionsProvider.getInstance(myProject)
    assertEquals(expected.myRunnerId, settings.coverageRunner?.id)
    assertEquals(expected.myBranchCoverage, settings.branchCoverage)
    assertEquals(expected.myTestTracking, settings.testTracking)
    assertEquals(expected.myTestModulesCoverage, settings.testModulesCoverage)
    assertEquals(expected.myIgnoreImplicitConstructors, settings.ignoreImplicitConstructors)
    assertEquals(expected.myCalculateExactHits, settings.calculateExactHits)
  }
}
