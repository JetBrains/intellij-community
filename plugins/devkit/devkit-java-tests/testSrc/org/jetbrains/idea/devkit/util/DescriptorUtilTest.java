// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/util/descriptor")
public class DescriptorUtilTest extends JavaCodeInsightFixtureTestCase {
  public void testSimple() {
    myFixture.copyFileToProject("simple.xml", "META-INF/plugin.xml");
    assertSameElements(DescriptorUtil.getPluginAndOptionalDependenciesIds(myModule), "com.intellij.example");
  }

  public void testWithDependency() {
    myFixture.copyFileToProject("withDependency.xml", "META-INF/plugin.xml");
    assertSameElements(DescriptorUtil.getPluginAndOptionalDependenciesIds(myModule), "com.intellij.example");
  }

  public void testWithOptionalDependency() {
    myFixture.copyFileToProject("withOptionalDependency.xml", "META-INF/plugin.xml");
    assertSameElements(DescriptorUtil.getPluginAndOptionalDependenciesIds(myModule), "com.intellij.example", "my.dependency");
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "util/descriptor";
  }
}
