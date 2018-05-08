// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.testFramework.TestDataPath;
import kotlin.KotlinVersion;
import org.jetbrains.idea.devkit.inspections.ComponentNotRegisteredInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;
import org.junit.Assume;

@TestDataPath("$CONTENT_ROOT/testData/inspections/componentNotRegistered")
public class KtComponentNotRegisteredInspectionTest extends ComponentNotRegisteredInspectionTestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Assume.assumeTrue(KotlinVersion.CURRENT.isAtLeast(1, 2, 50));
  }

  @Override
  protected String getSourceFileExtension() {
    return "kt";
  }

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/componentNotRegistered";
  }
}
