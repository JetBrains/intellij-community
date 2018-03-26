// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.j2me

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.j2me.SingleCharacterStartsWithInspection

/**
 * @author Bas Leijdekkers
 */
class SingleCharacterStartsWithLengthTest : SingleCharacterStartsWithFixTestCase() {

  fun testUseLengthMethod() = quickfixTest()

  override fun getProjectDescriptor() = JAVA_1_4
}
class SingleCharacterStartsWithIsEmptyTest : SingleCharacterStartsWithFixTestCase() {

  fun testUseIsEmptyMethod() = quickfixTest();

  override fun getProjectDescriptor() = JAVA_1_6
}
open class SingleCharacterStartsWithFixTestCase : LightCodeInsightFixtureTestCase() {

  protected fun quickfixTest() {
    myFixture.enableInspections(SingleCharacterStartsWithInspection())
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.testHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention(InspectionGadgetsBundle.message("single.character.startswith.quickfix")))
    myFixture.checkResultByFile(getTestName(false) + ".after.java")
  }

  override fun getBasePath() = "/plugins/InspectionGadgets/test/com/siyeh/igfixes/j2me/single_character_starts_with"
}