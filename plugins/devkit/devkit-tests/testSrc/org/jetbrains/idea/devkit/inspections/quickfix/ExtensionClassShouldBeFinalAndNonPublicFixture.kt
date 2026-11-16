// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.ExtensionClassShouldBeFinalAndNonPublicInspection

/**
 * The fixture of the [ExtensionClassShouldBeFinalAndNonPublicInspection] tests. Create it with [extensionClassShouldBeFinalAndNonPublicFixture].
 *
 * The fixture enables [ExtensionClassShouldBeFinalAndNonPublicInspection]. The project has the stub of `VisibleForTesting`.
 */
class ExtensionClassShouldBeFinalAndNonPublicFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  /**
   * Adds the stubs of `Attribute` and `ServiceDescriptor`.
   */
  fun addServiceDescriptorClass() {
    fixture.addClass(
      """
      package com.intellij.util.xmlb.annotations;

      public @interface Attribute {}
      """
    )

    fixture.addClass(
      """
      package com.intellij.openapi.components;

      public final class ServiceDescriptor {

        @com.intellij.util.xmlb.annotations.Attribute
        public final String serviceImplementation;

      }
      """
    )
  }
}

/**
 * Creates a [ExtensionClassShouldBeFinalAndNonPublicFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun extensionClassShouldBeFinalAndNonPublicFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<ExtensionClassShouldBeFinalAndNonPublicFixture> = testFixture("extensionClassShouldBeFinalAndNonPublicFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(ExtensionClassShouldBeFinalAndNonPublicInspection())
  fixture.addClass(
    """
    package org.jetbrains.annotations;

    public @interface VisibleForTesting { }
  """)
  initialized(ExtensionClassShouldBeFinalAndNonPublicFixture(fixture, fileExtension)) {}
}
