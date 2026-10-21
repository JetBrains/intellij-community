// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.editorconfig.Utils;
import org.editorconfig.language.codeinsight.inspections.EditorConfigValueCorrectnessInspection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class EditorConfigValueInspectionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private CodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    myFixture = codeInsightFixture.get();
    String testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
    myFixture.setTestDataPath(PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') +
                              "/plugins/editorconfig/testData/org/editorconfig/configmanagement/inspections/" + testName);
    Utils.setFullIntellijSettingsSupportEnabledInTest(true);
  }

  @AfterEach
  void tearDown() {
    Utils.setFullIntellijSettingsSupportEnabledInTest(false);
  }

  @Test
  public void testValues() {
    myFixture.configureByFile(".editorconfig");
    myFixture.enableInspections(EditorConfigValueCorrectnessInspection.class);
    myFixture.testHighlighting(true, false, true);
  }
}
