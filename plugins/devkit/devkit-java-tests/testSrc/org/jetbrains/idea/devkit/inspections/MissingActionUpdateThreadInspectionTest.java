/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/missingActionUpdateThread")
public class MissingActionUpdateThreadInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/missingActionUpdateThread";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(MissingActionUpdateThread.class);
    myFixture.addClass("package com.intellij.openapi.actionSystem;" +
                       "public interface ActionUpdateThreadAware {" +
                       "  default ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }" +
                       "}");
    myFixture.addClass("package com.intellij.openapi.actionSystem;" +
                       "import com.intellij.openapi.actionSystem.ActionUpdateThread;" +
                       "public class AnAction implements ActionUpdateThreadAware {" +
                       "  public void update(AnActionEvent event);" +
                       "  @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }" +
                       "}");
    myFixture.addClass("package com.intellij.openapi.actionSystem;" +
                       "public class AnActionEvent {}");
    myFixture.addClass("package com.intellij.openapi.actionSystem;" +
                       "public enum ActionUpdateThread { EDT, BGT }");
  }

  private void doTest() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testAction() { doTest(); }
  public void testActionUpdateAware() { doTest(); }
}
