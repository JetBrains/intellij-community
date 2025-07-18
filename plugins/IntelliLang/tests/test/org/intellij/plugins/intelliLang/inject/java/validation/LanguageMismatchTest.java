// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.java.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

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

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
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
  
  public void testProcessorReassigned() {
    highlightTest("""
                      import org.intellij.lang.annotations.Language;
                      
                      class Hello {
                          @Language("JAVA")
                          public static final StringTemplate.Processor<String, RuntimeException> JAVA = STR;
                      }""");
  }

  public void testProcessorFromMethod() {
    highlightTest("""
                      import org.intellij.lang.annotations.Language;
                      
                      interface MyProcessor extends StringTemplate.Processor<Object, RuntimeException> {}
                      
                      class Hello {
                          @Language("JAVA")
                          public static native MyProcessor getProcessor();
                      }""");

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