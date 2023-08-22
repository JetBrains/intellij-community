// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.KtCompanionObjectInExtensionInspectionTestBase

@TestDataPath("/inspections/createObjectAndMoveProhibitedDeclarationsFix")
class KtCreateObjectAndMoveProhibitedDeclarationsFixTest : KtCompanionObjectInExtensionInspectionTestBase() {

  private val fixName = DevKitKotlinBundle.message("inspections.create.object.and.move.prohibited.declarations.fix.text")

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/createObjectAndMoveProhibitedDeclarationsFix"

  fun testMoveProhibitedDeclarations() {
    doTestFixWithReferences(fixName)
  }

  fun testMoveConflicts() {
    val expectedConflicts = listOf(
      "Property logger uses property toInitLogger which will be inaccessible after move",
      "Variable a uses property g which will be inaccessible after move",
      "Function foobar() uses function quux() which will be inaccessible after move",
    )
    doTestFixWithConflicts(fixName, expectedConflicts)
  }

}
