// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.LightProjectDescriptorEqualsHashCodeInspection

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [LightProjectDescriptorEqualsHashCodeInspection]. The project has the stubs of the light project descriptors.
 */
@JvmOverloads
fun lightProjectDescriptorEqualsHashCodeFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("lightProjectDescriptorEqualsHashCodeFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(LightProjectDescriptorEqualsHashCodeInspection())
  fixture.addClass(
    """
    package com.intellij.testFramework;
    public class LightProjectDescriptor {}
    """.trimIndent()
  )
  fixture.addClass(
    """
    package com.intellij.testFramework.fixtures;
    import com.intellij.testFramework.LightProjectDescriptor;
    public class DefaultLightProjectDescriptor extends LightProjectDescriptor {}
    """.trimIndent()
  )
  initialized(devKit) {}
}
