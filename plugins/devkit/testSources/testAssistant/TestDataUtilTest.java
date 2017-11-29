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

  public void testGetTestDataGroup() {
    String something = myFixture.copyFileToProject("something.txt").getPath();
    String somethingAfter = myFixture.copyFileToProject("something_after.txt").getPath();
    String somethingBefore = myFixture.copyFileToProject("something_before.txt").getPath();
    String afterSomething = myFixture.copyFileToProject("afterSomething.txt").getPath();
    String beforeSomething = myFixture.copyFileToProject("beforeSomething.txt").getPath();

    assertNotNull(TestDataUtil.getTestDataGroup(something, somethingAfter));
    assertNotNull(TestDataUtil.getTestDataGroup(somethingAfter, something));

    assertNotNull(TestDataUtil.getTestDataGroup(somethingBefore, somethingAfter));
    assertNotNull(TestDataUtil.getTestDataGroup(somethingAfter, somethingBefore));

    assertNotNull(TestDataUtil.getTestDataGroup(beforeSomething, afterSomething));
    assertNotNull(TestDataUtil.getTestDataGroup(afterSomething, beforeSomething));
  }

  // https://youtrack.jetbrains.com/issue/IDEA-179740
  public void testGetTestDataGroupDotAfter() {
    String dotAfter = myFixture.copyFileToProject("dotAfter.txt").getPath();
    String dotAfterAfter = myFixture.copyFileToProject("dotAfter.after.txt").getPath();
    String dotAfterSomething = myFixture.copyFileToProject("dotAfter.something.txt").getPath();

    assertNull(TestDataUtil.getTestDataGroup(dotAfter, dotAfterSomething));
    assertNull(TestDataUtil.getTestDataGroup(dotAfterSomething, dotAfter));

    assertNull(TestDataUtil.getTestDataGroup(dotAfterSomething, dotAfterAfter));
    assertNull(TestDataUtil.getTestDataGroup(dotAfterAfter, dotAfterSomething));

    assertNotNull(TestDataUtil.getTestDataGroup(dotAfter, dotAfterAfter));
    assertNotNull(TestDataUtil.getTestDataGroup(dotAfterAfter, dotAfter));
  }

  public void testGetGroupDisplayName() {
    assertEquals(TestDataUtil.BEFORE_AFTER_DISPLAY_NAME_PART + "Something",
                 TestDataUtil.getGroupDisplayName("beforeSomething", "afterSomething"));
    assertEquals(TestDataUtil.BEFORE_AFTER_DISPLAY_NAME_PART + "Something.ext",
                 TestDataUtil.getGroupDisplayName("beforeSomething.ext", "afterSomething.ext"));
    assertEquals(TestDataUtil.BEFORE_AFTER_DISPLAY_NAME_PART + "_Something.ext",
                 TestDataUtil.getGroupDisplayName("before_Something.ext", "after_Something.ext"));

    assertEquals("something_" + TestDataUtil.BEFORE_AFTER_DISPLAY_NAME_PART + ".ext",
                 TestDataUtil.getGroupDisplayName("something_before.ext", "something_after.ext"));
    assertEquals("something_" + TestDataUtil.BEFORE_AFTER_DISPLAY_NAME_PART + ".ext",
                 TestDataUtil.getGroupDisplayName("something.ext", "something_after.ext"));
    assertEquals("something." + TestDataUtil.BEFORE_AFTER_DISPLAY_NAME_PART + ".ext",
                 TestDataUtil.getGroupDisplayName("something.ext", "something.after.ext"));
  }
}
