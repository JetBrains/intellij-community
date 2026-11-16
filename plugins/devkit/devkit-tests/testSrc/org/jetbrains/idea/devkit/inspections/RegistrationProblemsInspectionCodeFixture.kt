// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * Creates a [PluginModuleFixture] for one test. Declare it as an instance field. Give it the project of [pluginModuleProjectFixture].
 *
 * [basePath] is the test data path under the community directory.
 * The fixture enables [RegistrationProblemsInspection]. The project has the stubs of `AnAction` and `BaseComponent`.
 */
@JvmOverloads
fun registrationProblemsInspectionCodeFixture(
  projectFixture: TestFixture<LightJavaProject>,
  basePath: String = "",
): TestFixture<PluginModuleFixture> = testFixture("registrationProblemsInspectionCodeFixture") {
  val pluginModule = pluginModuleFixture(projectFixture, basePath).init()
  val fixture = pluginModule.fixture
  fixture.enableInspections(RegistrationProblemsInspection())
  fixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction {}")
  fixture.addClass("package com.intellij.openapi.components; public interface BaseComponent {}")
  initialized(pluginModule) {}
}
