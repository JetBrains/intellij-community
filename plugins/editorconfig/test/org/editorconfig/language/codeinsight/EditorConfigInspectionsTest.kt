// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.editorconfig.language.codeinsight.inspections.*
import kotlin.reflect.KClass

class EditorConfigInspectionsTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/inspections"

  // Inspections

  fun testCharClassLetterRedundancy() = doTest(EditorConfigCharClassLetterRedundancyInspection::class)
  fun testCharClassRedundancy() = doTest(EditorConfigCharClassRedundancyInspection::class)
  fun testDeprecatedDescriptor() = doTest(EditorConfigDeprecatedDescriptorInspection::class)
  fun testEmptyHeader() = doTest(EditorConfigEmptyHeaderInspection::class)
  fun testEmptySection() = doTest(EditorConfigEmptySectionInspection::class)
  fun testPatternEnumerationRedundancy() = doTest(EditorConfigPatternEnumerationRedundancyInspection::class)
  fun testHeaderUniqueness() = doTest(EditorConfigHeaderUniquenessInspection::class)
  fun testKeyCorrectness() = doTest(EditorConfigKeyCorrectnessInspection::class)
  fun testListAcceptability() = doTest(EditorConfigListAcceptabilityInspection::class)
  fun testMissingRequiredDeclaration() = doTest(EditorConfigMissingRequiredDeclarationInspection::class)
  fun testNoMatchingFiles_negative() {
    myFixture.configureByFiles(
      "${getTestName(true)}/this/subpackage/is/deep/abcd.cs",
      "${getTestName(true)}/this/subpackage/is/deep/enough/to/make/anyone/tired/of/opening/it/Main.java")
    doTest(EditorConfigNoMatchingFilesInspection::class)
  }

  fun testNoMatchingFiles_positive() = doTest(EditorConfigNoMatchingFilesInspection::class)
  fun testNumerousWildcards() = doTest(EditorConfigNumerousWildcardsInspection::class, checkWeakWarnings = true)
  fun testOptionRedundancy() = doTest(EditorConfigOptionRedundancyInspection::class)
  fun testPairAcceptability() = doTest(EditorConfigPairAcceptabilityInspection::class)
  fun testPartialOverride() = doTest(EditorConfigPartialOverrideInspection::class, checkWarnings = true)
  fun testPatternRedundancy_complex() = doTest(EditorConfigPatternRedundancyInspection::class)
  fun testPatternRedundancy_simple() = doTest(EditorConfigPatternRedundancyInspection::class)
  fun testReferenceCorrectness_complex() = doTest(EditorConfigReferenceCorrectnessInspection::class)
  fun testReferenceCorrectness_simple() = doTest(EditorConfigReferenceCorrectnessInspection::class)
  fun testRootDeclarationCorrectness() = doTest(EditorConfigRootDeclarationCorrectnessInspection::class)
  fun testRootDeclarationUniqueness() = doTest(EditorConfigRootDeclarationUniquenessInspection::class)
  fun testShadowedOption() = doTest(EditorConfigShadowedOptionInspection::class)
  fun testShadowingOption() = doTest(EditorConfigShadowingOptionInspection::class)
  fun testSpaceInHeader() = doTest(EditorConfigSpaceInHeaderInspection::class, checkWeakWarnings = true)
  fun testUnexpectedComma() = doTest(EditorConfigUnexpectedCommaInspection::class)
  fun testUnusedDeclaration() = doTest(EditorConfigUnusedDeclarationInspection::class)
  fun testValueCorrectness() = doTest(EditorConfigValueCorrectnessInspection::class)
  fun testValueUniqueness() = doTest(EditorConfigValueUniquenessInspection::class)
  fun testWildcardRedundancy() = doTest(EditorConfigWildcardRedundancyInspection::class)

  // Features

  fun testUnset() = doTest(EditorConfigValueCorrectnessInspection::class)
  fun testMaxLineLength() = doTest(EditorConfigKeyCorrectnessInspection::class, EditorConfigValueCorrectnessInspection::class)
  fun testIgnoreCaseKey() = doTest(EditorConfigKeyCorrectnessInspection::class)
  fun testIgnoreCaseValue() = doTest(EditorConfigValueCorrectnessInspection::class)
  fun testIgnoreCaseReference() = doTest(EditorConfigReferenceCorrectnessInspection::class)

  // Bugfixes

  fun testIncompleteHeader() = doTest(EditorConfigNoMatchingFilesInspection::class)

  // Utils

  private fun doTest(
    vararg inspections: KClass<out LocalInspectionTool>,
    checkWarnings: Boolean = true,
    checkWeakWarnings: Boolean = false,
    checkInfos: Boolean = false
  ) {
    myFixture.enableInspections(inspections.map(KClass<out LocalInspectionTool>::java))
    myFixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings, "${getTestName(true)}/.editorconfig")
  }
}
