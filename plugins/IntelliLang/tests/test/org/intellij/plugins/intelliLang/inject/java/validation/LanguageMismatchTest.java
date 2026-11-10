// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.java.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class LanguageMismatchTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var inspection = new LanguageMismatch();
    inspection.CHECK_NON_ANNOTATED_REFERENCES = true;
    myFixture.enableInspections(inspection);
  }

  @Test
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

  @Test
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
  
  @Test
  public void testProcessorReassigned() {
    highlightTest("""
                      import org.intellij.lang.annotations.Language;
                      
                      class Hello {
                          @Language("JAVA")
                          public static final StringTemplate.Processor<String, RuntimeException> JAVA = STR;
                      }""");
  }

  @Test
  public void testProcessorFromMethod() {
    highlightTest("""
                      import org.intellij.lang.annotations.Language;
                      
                      interface MyProcessor extends StringTemplate.Processor<Object, RuntimeException> {}
                      
                      class Hello {
                          @Language("JAVA")
                          public static native MyProcessor getProcessor();
                      }""");

  }
  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("X.java", text);
    myFixture.testHighlighting();
  }

  public void quickFixTest(String before, String after, String hint) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("X.java", before);
    IntentionAction action = myFixture.findSingleIntention(hint);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(action);
      myFixture.checkResult(after);
    });
  }
}