/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject.java.validation;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * @author Bas Leijdekkers
 */
public class LanguageMismatchTest extends LightJavaCodeInsightFixtureTestCase {

  public void testParentheses() {
    highlightTest("import org.intellij.lang.annotations.Language;" +
                  "class X {" +
                  "  @Language(\"JavaScript\")" +
                  "  String JS_CODE = \"var x;\";" +
                  "  @Language(\"XPath\")" +
                  "  String XPATH_CODE = (((<warning descr=\"Language mismatch: Expected 'XPath', got 'JavaScript'\">JS_CODE</warning>)));" +
                  "}");
  }

  public void testEmptyArrayConstant() {
    highlightTest("import org.intellij.lang.annotations.Language;" +
                  "class X {" +
                  "  public static final String[] EMPTY_ARRAY = {};" +
                  "  @Language(\"HTML\")" +
                  "  String[] getCode() {" +
                  "    return EMPTY_ARRAY;" +
                  "  }" +
                  "}");
  }

  public void highlightTest(String text) {
    myFixture.enableInspections(new LanguageMismatch());
    myFixture.configureByText("X.java", text);
    myFixture.testHighlighting();
  }

}