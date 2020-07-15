// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.inspections.LeakableMapKeyInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/leakableMapKey")
public class LeakableMapKeyInspectionTest extends LeakableMapKeyInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/leakableMapKey";
  }

  @Override
  public void testHighlighting() {
    myFixture.testHighlighting("Service.kt");
  }
}
