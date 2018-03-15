/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataPath;

@TestDataPath("$CONTENT_ROOT/testData/completionTestDataPath")
public class TestDataPathCompletionTest extends TestDataPathTestCase {
  public void testProjectRoot() {
    doTest();
  }

  public void testReferencesAfterProjectRoot() {
    doTest();
  }

  public void testContentRoot() {
    doTest();
  }

  public void testReferencesAfterContentRoot() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/completeTestDataPath";
  }
}
