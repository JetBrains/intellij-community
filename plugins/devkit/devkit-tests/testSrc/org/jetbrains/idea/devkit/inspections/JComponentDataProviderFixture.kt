// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [JComponentDataProviderInspection]. The project has the stubs of the data provider interfaces.
 */
@JvmOverloads
fun jComponentDataProviderFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("jComponentDataProviderFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(JComponentDataProviderInspection())
  fixture.addClass("""
    package com.intellij.openapi.actionSystem;
    public interface DataProvider {}
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.actionSystem;
    public interface UiCompatibleDataProvider {}
  """.trimIndent())
  fixture.addClass("""
    package com.intellij.openapi.actionSystem;
    public interface UiDataProvider {}
  """.trimIndent())
  initialized(devKit) {}
}
