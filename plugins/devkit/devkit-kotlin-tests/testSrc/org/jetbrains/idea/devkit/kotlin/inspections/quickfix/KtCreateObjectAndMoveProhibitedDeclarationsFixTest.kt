// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.KtCompanionObjectInExtensionInspectionTestBase

@TestDataPath("/inspections/createObjectAndMoveProhibitedDeclarations")
open class KtCreateObjectAndMoveProhibitedDeclarationsFixTest : KtCompanionObjectInExtensionInspectionTestBase() {

  private val quickFixName = DevKitKotlinBundle.message("inspections.create.object.and.move.prohibited.declarations.fix.text")

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/createObjectAndMoveProhibitedDeclarations"

  fun testMoveProhibitedDeclarations() {
    doTestQuickFix(quickFixName)
  }

  private fun doTestQuickFix(fixName: String) {
    val (fileNameBefore, fileNameAfter) = getBeforeAfterFileNames()
    val (referencesFileNameBefore, referencesFileNameAfter) = getBeforeAfterFileNames(suffix = "references")
    myFixture.testHighlighting(fileNameBefore, referencesFileNameBefore)
    val intention: IntentionAction = myFixture.findSingleIntention(fixName)
    myFixture.launchAction(intention)
    myFixture.checkResultByFile(fileNameBefore, fileNameAfter, true)
    myFixture.checkResultByFile(referencesFileNameBefore, referencesFileNameAfter, true)
  }

  private fun getBeforeAfterFileNames(testName: String = getTestName(false), suffix: String? = null): Pair<String, String> {
    val resultName = testName + suffix?.let { "_$it" }.orEmpty()
    return "${resultName}.$fileExtension" to "${resultName}_after.$fileExtension"
  }
}
