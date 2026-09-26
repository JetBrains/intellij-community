// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.StatisticsCollectorNotRegisteredInspectionTestBase;

@TestDataPath("$CONTENT_ROOT/testData/inspections/statisticsCollectorNotRegistered")
public class StatisticsCollectorNotRegisteredInspectionTest extends StatisticsCollectorNotRegisteredInspectionTestBase {
  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/statisticsCollectorNotRegistered";
  }

  @Override
  protected String getSourceFileExtension() {
    return "java";
  }
}
