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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * @author Bas Leijdekkers
 */
public class LanguageMismatchTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    var inspection = new LanguageMismatch();
    inspection.CHECK_NON_ANNOTATED_REFERENCES = true;
    myFixture.enableInspections(inspection);
  }

  public void testParenthesesHighlighting() {
    highlightTest("""
      import org.intellij.lang.annotations.Language;
                    
      class X {
        @Language("JavaScript")
        String JS_CODE = "var x;";
                        
        @Language("XPath")
        String XPATH_CODE = (((<warning descr="Language mismatch: Expected 'XPath', got 'JavaScript'">JS_CODE</warning>)));
      }
    """);
  }

  public void testAnnotateFix() {
    quickFixTest("""
    import org.intellij.lang.annotations.Language;
                      
    class X {
      String JS_CODE = "var y;";

      @Language("JavaScript")
      String OTHER_JS_CODE = JS_<caret>CODE;
    }
    """, """
    import org.intellij.lang.annotations.Language;
                      
    class X {
      @Language("JavaScript")
      String JS_CODE = "var y;";

      @Language("JavaScript")
      String OTHER_JS_CODE = JS_<caret>CODE;
    }
    """, "Annotate field 'JS_CODE' as '@Language'");
  }

  public void testEmptyArrayConstant() {
    highlightTest("""
      import org.intellij.lang.annotations.Language;
      
      class X {
        public static final String[] EMPTY_ARRAY = {};
          
        @Language("HTML")
        String[] getCode() {
          return EMPTY_ARRAY;
        }
      }
    """);
  }

  public void highlightTest(String text) {
    myFixture.configureByText("X.java", text);
    myFixture.testHighlighting();
  }

  public void quickFixTest(String before, String after, String hint) {
    myFixture.configureByText("X.java", before);
    IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.checkPreviewAndLaunchAction(action);
    myFixture.checkResult(after);
  }
}