// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * The fixture of the [SerializableCtorInspection] tests. Create it with [serializableCtorInspectionFixture].
 *
 * The fixture enables [SerializableCtorInspection].
 */
class SerializableCtorInspectionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val fileExtension: String,
) {
  /**
   * Adds the stub of `PropertyMapping` and checks the highlighting of the test data file `<test name>.<file extension>`.
   */
  fun doTest() {
    doTest(false)
  }

  /**
   * Checks the highlighting of the test data file `<test name>.<file extension>`.
   * Without [disablePropertyMappingClass], it adds the stub of `PropertyMapping` before the check.
   */
  fun doTest(disablePropertyMappingClass: Boolean) {
    if (!disablePropertyMappingClass) {
      fixture.addClass("package com.intellij.serialization;\n" +
                       "public @interface PropertyMapping { String[] value(); }")
    }
    fixture.testHighlighting(fixture.getTestName(false) + '.' + fileExtension)
  }
}

/**
 * Creates a [SerializableCtorInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun serializableCtorInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<SerializableCtorInspectionFixture> = testFixture("serializableCtorInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  fixture.enableInspections(SerializableCtorInspection())
  initialized(SerializableCtorInspectionFixture(fixture, fileExtension)) {}
}
