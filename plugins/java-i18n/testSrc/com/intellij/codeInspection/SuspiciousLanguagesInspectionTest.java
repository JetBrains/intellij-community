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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;

/**
 * @author Dmitry Batkovich
 */
public class SuspiciousLanguagesInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/inspections/suspiciousLanguages";
  }

  public void testSimple1() {
    doTest();
  }

  public void testSimple2() {
    doTest();
  }

  public void testWithAdditionalLocales() {
    doTest("asd");
  }

  private void doTest(final String... additionalLocales) {
    final File tesDataDir = new File(getHomePath() + File.separator + getBasePath() + File.separator + getTestName(true));
    final File[] files = tesDataDir.listFiles();
    assertNotNull(files);
    for (File file : files) {
      final String path = file.getAbsolutePath();
      myFixture.configureByFile(path);
    }
    final SuspiciousLocalesLanguagesInspection inspection = new SuspiciousLocalesLanguagesInspection();
    if (additionalLocales.length != 0) {
      inspection.setAdditionalLanguages(ContainerUtil.newArrayList(additionalLocales));
    }
    myFixture.enableInspections(inspection);
    myFixture.checkHighlighting();
  }
}
