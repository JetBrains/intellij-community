// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.lightGroovyFixture
import org.jetbrains.plugins.groovy.util.TestUtils
import java.util.function.Supplier

/**
 * The fixture of the Groovy highlighting tests. Create it with [grHighlightingFixture].
 *
 * Each member enables the inspections of the call and the custom inspections of the fixture, and checks the highlighting.
 * Call the members from any thread.
 */
@TestOnly
class GrHighlightingFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val customInspections: Supplier<out Array<out InspectionProfileEntry>>,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Returns new instances of the custom inspections of the fixture.
   */
  fun getCustomInspections(): Array<out InspectionProfileEntry> = customInspections.get()

  /**
   * Checks the highlighting of `<test name>.groovy` in the test data.
   */
  fun doTest(checkWarnings: Boolean, checkInfos: Boolean, checkWeakWarnings: Boolean, vararg tools: InspectionProfileEntry) {
    fixture.enableInspections(*tools)
    fixture.enableInspections(*getCustomInspections())
    fixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings, getTestName(false) + ".groovy")
  }

  fun doTest(checkWarnings: Boolean, checkInfos: Boolean, vararg tools: InspectionProfileEntry) {
    doTest(checkWarnings, checkInfos, true, *tools)
  }

  fun doTest(checkWarnings: Boolean, vararg tools: InspectionProfileEntry) {
    doTest(checkWarnings, false, true, *tools)
  }

  fun doTest(vararg tools: InspectionProfileEntry) {
    doTest(true, false, true, *tools)
  }

  /**
   * Checks the highlighting of `<test name>.groovy` in the test data, as [doTest] does, with [GrUnresolvedAccessInspection] enabled.
   */
  fun doRefTest(checkWarnings: Boolean, checkInfos: Boolean, checkWeakWarnings: Boolean, vararg tools: InspectionProfileEntry) {
    fixture.enableInspections(GrUnresolvedAccessInspection())
    fixture.enableInspections(*tools)
    fixture.enableInspections(*getCustomInspections())
    fixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings, getTestName(false) + ".groovy")
  }

  fun doRefTest(vararg tools: InspectionProfileEntry) {
    doRefTest(true, false, true, *tools)
  }

  /**
   * Configures `_.groovy` with [text] and checks its highlighting.
   */
  fun doTestHighlighting(
    text: String,
    checkWarnings: Boolean,
    checkInfos: Boolean,
    checkWeakWarnings: Boolean,
    vararg inspections: Class<out LocalInspectionTool>,
  ) {
    fixture.configureByText("_.groovy", text)
    fixture.enableInspections(*inspections)
    fixture.enableInspections(*getCustomInspections())
    fixture.testHighlighting(checkWarnings, checkInfos, checkWeakWarnings)
  }

  fun doTestHighlighting(text: String, checkWarnings: Boolean, checkInfos: Boolean, vararg inspections: Class<out LocalInspectionTool>) {
    doTestHighlighting(text, checkWarnings, checkInfos, true, *inspections)
  }

  fun doTestHighlighting(text: String, checkWarnings: Boolean, vararg inspections: Class<out LocalInspectionTool>) {
    doTestHighlighting(text, checkWarnings, false, true, *inspections)
  }

  fun doTestHighlighting(text: String, vararg inspections: Class<out LocalInspectionTool>) {
    doTestHighlighting(text, true, false, true, *inspections)
  }

  companion object {
    @JvmField
    val EMPTY_INSPECTION_PROFILE_ENTRY_ARRAY: Array<InspectionProfileEntry> = emptyArray()

    /**
     * The test data path of the highlighting tests.
     */
    @JvmStatic
    fun getHighlightingTestDataPath(): String = TestUtils.getAbsoluteTestDataPath() + "highlighting/"
  }
}

/**
 * Creates the [GrHighlightingFixture] of a highlighting test. Declare it as an instance field.
 *
 * [customInspections] gives the inspections that each member enables. The fixture calls it for each member call.
 * The default [testDataPath] is [GrHighlightingFixture.getHighlightingTestDataPath].
 */
@TestOnly
@JvmOverloads
fun grHighlightingFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String = GrHighlightingFixture.getHighlightingTestDataPath(),
  customInspections: Supplier<out Array<out InspectionProfileEntry>> = Supplier { GrHighlightingFixture.EMPTY_INSPECTION_PROFILE_ENTRY_ARRAY },
): TestFixture<GrHighlightingFixture> = testFixture("grHighlightingFixture") {
  val fixture = lightGroovyFixture(projectFixture, testDataPath).init()
  initialized(GrHighlightingFixture(fixture, customInspections)) {}
}
