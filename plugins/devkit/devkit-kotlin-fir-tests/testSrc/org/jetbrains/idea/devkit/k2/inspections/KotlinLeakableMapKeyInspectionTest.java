// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.inspections.LeakableMapKeyInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;


@TestDataPath("$CONTENT_ROOT/testData/inspections/leakableMapKey")
public class KotlinLeakableMapKeyInspectionTest extends LeakableMapKeyInspectionTestBase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/leakableMapKey";
  }

  @Override
  public void testHighlighting() {
    myFixture.testHighlighting("Service.kt");
  }
}
