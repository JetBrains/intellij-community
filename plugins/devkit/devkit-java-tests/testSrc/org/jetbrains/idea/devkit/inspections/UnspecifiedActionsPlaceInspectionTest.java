// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

/**
 * @author Konstantin Bulenkov
 */
@TestDataPath("/inspections/unspecifiedActionPlace")
public class UnspecifiedActionsPlaceInspectionTest extends UnspecifiedActionsPlaceInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/unspecifiedActionsPlace";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "java";
  }

  public void testUnspecifiedActionPlaces() {
    doTest();
  }
}
