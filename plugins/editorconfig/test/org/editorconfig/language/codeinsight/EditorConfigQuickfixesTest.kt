// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.editorconfig.language.codeinsight.inspections.*
import org.editorconfig.language.messages.EditorConfigBundle
import org.jetbrains.annotations.PropertyKey
import kotlin.reflect.KClass

class EditorConfigQuickfixesTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/quickfixes/"

  fun testCleanupValueList() =
    doTest(EditorConfigUnexpectedCommaInspection::class,
           "quickfix.values.list.cleanup.description")

  fun testConvertToPlainPattern() =
    doTest(EditorConfigCharClassPatternRedundancyInspection::class,
           "quickfix.charclass.convert.to.plain.pattern.description")

  fun testInsertStar() =
    doTest(EditorConfigEmptyHeaderInspection::class,
           "quickfix.header.insert.star.description")

  fun testMergeSections() =
    doTest(EditorConfigHeaderUniquenessInspection::class,
           "quickfix.section.merge-duplicate.description")

  fun testRemoveBraces() =
    doTest(EditorConfigPatternEnumerationRedundancyInspection::class,
           "quickfix.pattern-enumeration.redundant.remove-braces.description")

  fun testRemoveDeprecatedDescriptor() =
    doTest(EditorConfigDeprecatedDescriptorInspection::class,
           "quickfix.deprecated.element.remove")

  fun testRemoveHeaderElement() =
    doTest(EditorConfigPatternRedundancyInspection::class,
           "quickfix.header-element.remove.description")

  fun testRemoveListValue() =
    doTest(EditorConfigValueUniquenessInspection::class,
           "quickfix.value.remove.description")

  fun testRemoveOption() =
    doTest(EditorConfigShadowingOptionInspection::class,
           "quickfix.option.remove.description")

  fun testRemoveRootDeclaration() =
    doTest(EditorConfigRootDeclarationCorrectnessInspection::class,
           "quickfix.root-declaration.remove.description")

  fun testRemoveSection() =
    doTest(EditorConfigEmptySectionInspection::class,
           "quickfix.section.remove.description")

  fun testRemoveSpaces() =
    doTest(EditorConfigSpaceInHeaderInspection::class,
           "quickfix.header.remove.spaces.description")

  fun testRemoveUnexpectedValues() =
    doTest(EditorConfigListAcceptabilityInspection::class,
           "quickfix.value.list.remove.others")

  fun testAddRequiredDeclaration() =
    doTest(EditorConfigMissingRequiredDeclarationInspection::class,
           "quickfix.declaration.add-required.description")

  fun testReplaceWithValidRootDeclaration() =
    doTest(EditorConfigRootDeclarationCorrectnessInspection::class,
           "quickfix.root-declaration.replace-with-valid.description")

  fun testSanitizeCharClass() =
    doTest(EditorConfigCharClassLetterRedundancyInspection::class,
           "quickfix.charclass.sanitize.description")

  private fun doTest(
    inspection: KClass<out LocalInspectionTool>,
    @PropertyKey(resourceBundle = EditorConfigBundle.BUNDLE) intentionKey: String
  ) {
    myFixture.enableInspections(inspection.java)
    val testName = getTestName(true)
    myFixture.configureByFile("$testName/.editorconfig")
    val quickFix = findIntention(intentionKey)
    myFixture.launchAction(quickFix)
    myFixture.checkResultByFile("$testName/result.txt", true)
  }

  private fun findIntention(@PropertyKey(resourceBundle = EditorConfigBundle.BUNDLE) intentionKey: String): IntentionAction {
    val availableIntentions = myFixture.availableIntentions
    val intentionName = EditorConfigBundle[intentionKey]
    val result = availableIntentions.firstOrNull { it.text == intentionName }
    return result ?: throw AssertionError("Intention '$intentionName' not found among ${availableIntentions.map(IntentionAction::getText)}")
  }
}
