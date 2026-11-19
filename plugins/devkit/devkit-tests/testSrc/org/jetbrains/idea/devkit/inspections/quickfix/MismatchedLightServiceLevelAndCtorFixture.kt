// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.MismatchedLightServiceLevelAndCtorInspection

/**
 * The fixture of the [MismatchedLightServiceLevelAndCtorInspection] tests. Create it with [mismatchedLightServiceLevelAndCtorFixture].
 *
 * The fixture enables [MismatchedLightServiceLevelAndCtorInspection]. The project has the stubs of `Service`, `Project` and `CoroutineScope`.
 */
class MismatchedLightServiceLevelAndCtorFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  /**
   * The name of the quick fix that specifies the project level of the service.
   */
  val annotateAsServiceFixName: String =
    DevKitBundle.message("inspection.mismatched.light.service.level.and.ctor.specify.project.level.fix")
}

/**
 * Creates a [MismatchedLightServiceLevelAndCtorFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun mismatchedLightServiceLevelAndCtorFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<MismatchedLightServiceLevelAndCtorFixture> = testFixture("mismatchedLightServiceLevelAndCtorFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(MismatchedLightServiceLevelAndCtorInspection())
  fixture.addClass(
    """
    package com.intellij.openapi.components;

    public @interface Service {
      Level[] value() default Level.APP;

      enum Level { APP, PROJECT }
    }
    """)
  fixture.addClass(
    """
    package com.intellij.openapi.project;

    import com.intellij.openapi.components.ComponentManager;

    public interface Project extends ComponentManager {}
    """)
  fixture.addClass(
    """
    package kotlinx.coroutines;

    public interface CoroutineScope {}
    """)
  initialized(MismatchedLightServiceLevelAndCtorFixture(fixture, fileExtension)) {}
}
