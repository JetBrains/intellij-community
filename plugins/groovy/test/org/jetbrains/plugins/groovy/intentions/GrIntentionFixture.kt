// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail

/**
 * The fixture of the Groovy intention tests. Create it with [grIntentionFixture].
 *
 * The members configure a Groovy file, run the intention [hint] and check the result.
 * A member that runs an intention runs the whole sequence on the EDT.
 */
@TestOnly
class GrIntentionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  /**
   * The text of the intention that the members run when the call gives no hint.
   */
  val hint: String?,
  private val inspections: Array<out Class<out LocalInspectionTool>>,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Configures `<test name>.groovy` of the test data and looks for the intention [hint].
   * When [intentionShouldBeAvailable] is true, it runs the intention and compares the file with `<test name>_after.groovy`.
   */
  fun doTest(hint: String, intentionShouldBeAvailable: Boolean) {
    val testName = getTestName(false)
    runInEdtAndWait {
      fixture.configureByFile("$testName.groovy")
      val list = fixture.filterAvailableIntentions(hint)
      if (intentionShouldBeAvailable) {
        if (list.size != 1) {
          fail<Unit>("Intention not found among " + fixture.availableIntentions.joinToString(", ") { it.text })
        }
        launch(list.single())
        fixture.checkResultByFile("${testName}_after.groovy")
      }
      else if (list.isNotEmpty()) {
        fail<Unit>(list.joinToString(",") { it.familyName })
      }
    }
  }

  fun doTest(intentionShouldBeAvailable: Boolean) {
    doTest(requireHint(), intentionShouldBeAvailable)
  }

  /**
   * Configures `a.groovy` with [before], enables [inspections] and the inspections of the fixture, runs the intention [hint],
   * and compares the file with [after].
   */
  fun doTextTest(before: String, hint: String, after: String, vararg inspections: Class<out LocalInspectionTool>) {
    runInEdtAndWait {
      configure(before, inspections)
      val list = fixture.filterAvailableIntentions(hint)
      if (list.size != 1) {
        fail<Unit>("Intention not found among " + fixture.availableIntentions.joinToString(", ") { it.text })
      }
      launch(list.single())
      fixture.checkResult(after)
    }
  }

  fun doTextTest(before: String, after: String, vararg inspections: Class<out LocalInspectionTool>) {
    doTextTest(before, requireHint(), after, *inspections)
  }

  /**
   * Configures `a.groovy` with [before] and checks that the intention [hint] is not available.
   */
  fun doAntiTest(before: String, hint: String, vararg inspections: Class<out LocalInspectionTool>) {
    configure(before, inspections)
    val list = fixture.filterAvailableIntentions(hint)
    assertTrue(list.isEmpty()) { "Expected no intentions, got: $list" }
  }

  fun doAntiTest(before: String, vararg inspections: Class<out LocalInspectionTool>) {
    doAntiTest(before, requireHint(), *inspections)
  }

  private fun configure(before: String, inspections: Array<out Class<out LocalInspectionTool>>) {
    fixture.configureByText("a.groovy", before)
    fixture.enableInspections(*inspections)
    fixture.enableInspections(*this.inspections)
  }

  private fun launch(action: IntentionAction) {
    fixture.launchAction(action)
    PostprocessReformattingAspect.getInstance(fixture.project).doPostponedFormatting()
  }

  private fun requireHint(): String = checkNotNull(hint) { "Give the hint to grIntentionFixture" }
}

/**
 * Creates a [GrIntentionFixture] for one test. Declare it as an instance field.
 *
 * [hint] is the intention of the members without a hint. The members that configure a text enable [inspections].
 * [testDataPath] is the absolute test data path. By default, it is the community root.
 * The function has no Java overloads, because a `null` hint and a vararg make a Java call ambiguous. Java gives all arguments.
 */
@TestOnly
fun grIntentionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  hint: String? = null,
  testDataPath: String? = null,
  vararg inspections: Class<out LocalInspectionTool>,
): TestFixture<GrIntentionFixture> = testFixture("grIntentionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  if (testDataPath != null) {
    fixture.testDataPath = testDataPath
  }
  initialized(GrIntentionFixture(fixture, hint, inspections)) {}
}
