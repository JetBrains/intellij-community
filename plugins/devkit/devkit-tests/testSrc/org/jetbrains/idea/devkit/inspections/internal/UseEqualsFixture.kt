// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal

import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.idea.devkit.inspections.PluginModuleFixture
import org.jetbrains.idea.devkit.inspections.pluginModuleFixture
import org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture

/**
 * The fixture of the [UseEqualsInspectionBase] tests. Create it with [useEqualsFixture].
 *
 * The test enables the inspection with [UseEqualsFixture.doTest].
 */
class UseEqualsFixture(fixture: LightJavaCodeInsightTestFixture) : PluginModuleFixture(fixture, "") {
  /**
   * Enables [inspection] and checks the highlighting of all [filePaths].
   * Call it from any thread. It checks the highlighting on the EDT, because it opens an editor for each file.
   */
  fun doTest(inspection: Class<out UseEqualsInspectionBase>, @TestDataFile vararg filePaths: String) {
    fixture.enableInspections(inspection)
    fixture.allowTreeAccessForAllFiles()
    runInEdtAndWait {
      fixture.testHighlightingAllFiles(true, false, false, *filePaths)
    }
  }
}

/**
 * Creates a [UseEqualsFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun useEqualsFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<UseEqualsFixture> = testFixture("useEqualsFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath).init()
  val fixture = pluginModule.fixture

  initialized(UseEqualsFixture(fixture)) {}
}
