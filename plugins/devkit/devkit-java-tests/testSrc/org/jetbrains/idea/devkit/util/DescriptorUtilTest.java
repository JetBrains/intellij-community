// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestDataPath("$CONTENT_ROOT/testData/util/descriptor")
@TestApplication
public class DescriptorUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH + "util/descriptor");

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("simple.xml", "META-INF/plugin.xml");
    ReadAction.runBlocking(() -> {
      assertThat(DescriptorUtil.getPluginAndOptionalDependenciesIds(myFixture.getModule())).containsOnly("com.intellij.example");
    });
  }

  @Test
  public void testWithDependency() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("withDependency.xml", "META-INF/plugin.xml");
    ReadAction.runBlocking(() -> {
      assertThat(DescriptorUtil.getPluginAndOptionalDependenciesIds(myFixture.getModule())).containsOnly("com.intellij.example");
    });
  }

  @Test
  public void testWithOptionalDependency() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyFileToProject("withOptionalDependency.xml", "META-INF/plugin.xml");
    ReadAction.runBlocking(() -> {
      assertThat(DescriptorUtil.getPluginAndOptionalDependenciesIds(myFixture.getModule())).containsOnly("com.intellij.example", "my.dependency");
    });
  }
}
