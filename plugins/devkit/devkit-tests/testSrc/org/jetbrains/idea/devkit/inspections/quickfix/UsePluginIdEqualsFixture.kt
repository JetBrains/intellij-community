// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.internal.UsePluginIdEqualsInspection

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [UsePluginIdEqualsInspection]. The project has the stub of `PluginId`.
 */
@JvmOverloads
fun usePluginIdEqualsFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("usePluginIdEqualsFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.addClass("""
    package com.intellij.openapi.extensions;
    public final class PluginId {
      public static PluginId getId(String idString) {return null;}
    }
    """.trimIndent())
  fixture.enableInspections(UsePluginIdEqualsInspection())
  initialized(devKit) {}
}
