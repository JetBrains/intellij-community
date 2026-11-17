// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * Creates the [LightDevKitInspectionFixture] of the [UnspecifiedActionsPlaceInspection] tests for one test. Declare it as an instance field.
 *
 * The fixture enables [UnspecifiedActionsPlaceInspection].
 * The project has the stubs of `ActionPopupMenu`, `ActionToolbar`, `ActionGroup`, `ActionPlaces` and `ActionManager`.
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun unspecifiedActionsPlaceInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("unspecifiedActionsPlaceInspectionFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(UnspecifiedActionsPlaceInspection::class.java)
  fixture.addClass("package com.intellij.openapi.actionSystem; public class ActionPopupMenu {}")
  fixture.addClass("package com.intellij.openapi.actionSystem; public class ActionToolbar {}")
  fixture.addClass("package com.intellij.openapi.actionSystem; public class ActionGroup {}")
  fixture.addClass("package com.intellij.openapi.actionSystem; public class ActionPlaces {public static final String UNKNOWN = \"unknown\";}")
  fixture.addClass("package com.intellij.openapi.actionSystem; " +
                   "public class ActionManager {" +
                   "public static ActionManager getInstance() {return new ActionManager();}" +
                   "public ActionPopupMenu createActionPopupMenu(@NonNls String place, @NotNull ActionGroup group) {return new ActionPopupMenu();}" +
                   "public ActionToolbar createActionToolbar(@NonNls String place, @NotNull ActionGroup group, boolean horizontal) {return new ActionToolbar();}" +
                   "}")
  initialized(devKit) {}
}
