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

@TestApplication
public class InjectionNotApplicableTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var inspection = new InjectionNotApplicable();
    myFixture.enableInspections(inspection);
  }

  @Test
  public void testNonString() {
    highlightTest("""
                      import org.intellij.lang.annotations.Language;
                      
                      class Hello {
                          <error descr="Language injection is only applicable to strings or string templates">@Language("JAVA")</error>
                          public static native Object getSomething();
                      }""");
  }

  @Test
  public void testNonStringFix() {
    quickFixTest("""
                      import org.intellij.lang.annotations.Language;
                    
                      class Hello {
                          @<caret>Language("JAVA")
                          public static native Object getSomething();
                      }""",
                 """
                      class Hello {
                          public static native Object getSomething();
                      }""", 
                 "Remove annotation");
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