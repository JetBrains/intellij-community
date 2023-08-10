// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.UnspecifiedActionsPlaceInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

/**
 * @author Konstantin Bulenkov
 */
@TestDataPath("/inspections/unspecifiedActionPlace")
public class KtUnspecifiedActionsPlaceInspectionTest extends UnspecifiedActionsPlaceInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/unspecifiedActionsPlace";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testUnspecifiedActionPlaces() {
    doTest();
  }
}
