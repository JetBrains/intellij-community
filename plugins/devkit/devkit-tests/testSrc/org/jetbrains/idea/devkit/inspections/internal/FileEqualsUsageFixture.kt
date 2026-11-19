// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal

import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.PluginModuleFixture
import org.jetbrains.idea.devkit.inspections.pluginModuleFixture
import org.jetbrains.idea.devkit.inspections.pluginModuleProjectFixture

/**
 * The fixture of the [FileEqualsUsageInspection] tests. Create it with [fileEqualsUsageFixture].
 *
 * The fixture enables [FileEqualsUsageInspection]. The project has the stub of `FileUtil`.
 */
class FileEqualsUsageFixture(fixture: LightJavaCodeInsightTestFixture) : PluginModuleFixture(fixture, "") {
  /**
   * Returns [methodExpressionText]. With [highlightError], the method name has the warning markup of the inspection.
   */
  fun getMethodExpressionText(methodExpressionText: String, highlightError: Boolean): String {
    return if (highlightError) {
      val methodName = StringUtil.substringBefore(methodExpressionText, "(")
      val methodParams = StringUtil.substringAfter(methodExpressionText, methodName!!)
      """<warning descr="${errorMessage()}">$methodName</warning>$methodParams"""
    }
    else {
      methodExpressionText
    }
  }

  /**
   * Returns [operatorText]. With [highlightError], it has the warning markup of the inspection.
   */
  fun getOperatorText(operatorText: String, highlightError: Boolean): String {
    return if (highlightError) {
      """<warning descr="${errorMessage()}">$operatorText</warning>"""
    }
    else {
      operatorText
    }
  }

  private fun errorMessage(): @Nls String? =
    DevKitBundle.message("inspections.file.equals.method")
}

/**
 * Creates a [FileEqualsUsageFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun fileEqualsUsageFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<FileEqualsUsageFixture> = testFixture("fileEqualsUsageFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath).init()
  val fixture = pluginModule.fixture
  fixture.enableInspections(FileEqualsUsageInspection())
  fixture.addClass("package com.intellij.openapi.util.io; public class FileUtil {}")
  initialized(FileEqualsUsageFixture(fixture)) {}
}
