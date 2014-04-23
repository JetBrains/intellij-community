/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.spellchecker.inspection;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class JavaSpellcheckerInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("java-i18n") + "/testData/inspections/spellchecker";
  }

  public void testCorrectJava() { doTest(); }
  public void testTypoInJava() { doTest(); }
  public void testVarArg() { doTest(); }
  public void testJapanese() { doTest(); }

  public void testClassName() { doTest(); }
  public void testFieldName() { doTest(); }
  public void testMethodName() { doTest(); }
  public void testLocalVariableName() { doTest(); }
  public void testDocComment() { doTest(); }
  public void testStringLiteral() { doTest(); }
  public void testStringLiteralEscaping() { doTest(); }
  public void testSuppressions() { doTest(); }

  private void doTest() {
    myFixture.enableInspections(SpellcheckerInspectionTestCase.getInspectionTools());
    myFixture.testHighlighting(false, false, true, getTestName(false) + ".java");
  }
}
