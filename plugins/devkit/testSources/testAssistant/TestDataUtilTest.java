// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/testData/testDataUtil")
public class TestDataUtilTest extends TestDataPathTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/testDataUtil";
  }

  public void testDotAfter() {
    String dotAfter = myFixture.copyFileToProject("dotAfter.txt").getPath();
    String dotAfterAfter = myFixture.copyFileToProject("dotAfter.after.txt").getPath();
    String dotAfterSomething = myFixture.copyFileToProject("dotAfter.something.txt").getPath();

    assertNull(TestDataUtil.getTestDataGroup(dotAfter, dotAfterSomething));
    assertNull(TestDataUtil.getTestDataGroup(dotAfterSomething, dotAfterAfter));
    assertNotNull(TestDataUtil.getTestDataGroup(dotAfter, dotAfterAfter));
  }
}
