// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import java.nio.file.Files
import java.nio.file.Path

/**
 * The fixture of a light DevKit inspection test. Create it with [lightDevKitInspectionFixture].
 *
 * The fixtures of the DevKit inspection tests extend it with their own helpers.
 */
open class LightDevKitInspectionFixture(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  /**
   * The extension of the test data files, without the dot.
   */
  val fileExtension: String,
) {
  /**
   * Checks the highlighting of the test data file `<test name>.<file extension>`.
   */
  fun doTest() {
    fixture.testHighlighting(fixture.getTestName(false) + '.' + fileExtension)
  }

  /**
   * Checks the highlighting of `<test name>.<file extension>`, applies the quick fix [fixName] and checks `<test name>_after.<file extension>`.
   * When the file `<test name>_preview.<file extension>` exists, the fixture compares the preview of the quick fix with it.
   * Call it from any thread. It applies the quick fix on the EDT.
   */
  fun doTest(fixName: String) {
    doFixTest(fixture, fixName, fileExtension, fixture.getTestName(false))
  }

  companion object {
    /**
     * Checks the highlighting of `<testName>.<fileExtension>`, applies the quick fix [fixName] and checks `<testName>_after.<fileExtension>`.
     * It is the JUnit 5 form of [DevKitInspectionFixTestBase.doTest]. Call it from any thread. It applies the quick fix on the EDT.
     */
    @JvmStatic
    fun doFixTest(fixture: JavaCodeInsightTestFixture, fixName: String, fileExtension: String, testName: String) {
      val fileNameBefore = "$testName.$fileExtension"
      val fileNameAfter = "${testName}_after.$fileExtension"
      fixture.testHighlighting(fileNameBefore)
      runInEdtAndWait {
        val intention = fixture.findSingleIntention(fixName)
        val previewPath = Path.of(fixture.testDataPath, "${testName}_preview.$fileExtension")
        if (Files.exists(previewPath)) {
          val previewText = fixture.getIntentionPreviewText(intention)
          PlatformTestUtil.assertSameLinesWithFile(previewPath.toString(), previewText!!)
          fixture.launchAction(intention)
        }
        else {
          fixture.checkPreviewAndLaunchAction(intention)
        }
        fixture.checkResultByFile(fileNameBefore, fileNameAfter, true)
      }
    }
  }
}

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun lightDevKitInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("lightDevKitInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  initialized(LightDevKitInspectionFixture(fixture, fileExtension)) {}
}
