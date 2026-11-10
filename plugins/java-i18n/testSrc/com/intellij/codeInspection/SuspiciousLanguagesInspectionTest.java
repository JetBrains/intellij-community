/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class SuspiciousLanguagesInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/inspections/suspiciousLanguages");

  @Test
  public void testSimple1() {
    doTest("p.properties", "p_en.properties");
  }

  @Test
  public void testWithAdditionalLocales() {
    doTest("p.properties", "p_asd.properties", "asd");
  }

  private void doTest(final String file1, final String file2, final String... additionalLocales) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(true) + "/" + file1);
    myFixture.configureByFile(myFixture.getTestName(true) + "/" + file2);
    final SuspiciousLocalesLanguagesInspection inspection = new SuspiciousLocalesLanguagesInspection();
    if (additionalLocales.length != 0) {
      inspection.setAdditionalLanguages(List.of(additionalLocales));
    }
    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting();
  }
}
