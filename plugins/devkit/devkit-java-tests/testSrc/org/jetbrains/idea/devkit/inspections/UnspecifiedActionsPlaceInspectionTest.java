// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.UnspecifiedActionsPlaceInspectionFixtureKt.unspecifiedActionsPlaceInspectionFixture;

/**
 * @author Konstantin Bulenkov
 */
@TestDataPath("$CONTENT_ROOT/testData/inspections/unspecifiedActionsPlace")
@TestApplication
public class UnspecifiedActionsPlaceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightDevKitInspectionFixture> actionsPlace =
    unspecifiedActionsPlaceInspectionFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/unspecifiedActionsPlace");

  @Test
  public void testUnspecifiedActionPlaces() {
    actionsPlace.get().doTest();
  }
}
