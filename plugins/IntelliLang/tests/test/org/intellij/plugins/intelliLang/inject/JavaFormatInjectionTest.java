// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.InjectionTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.intelliLang.Configuration;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class JavaFormatInjectionTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testInstanceFormattedWithComputableArgument() {
    doTest("""
             import org.intellij.lang.annotations.Language;
             class X {
               static final String MID = "B";
               void test() {
                 @Language("JAVA") String q = "a(%s)c".formatted(MID);
               }
             }""",
           "a(B)c");
  }

  @Test
  public void testUserExampleWithNonComputableArgument() {
    doTest("""
             import org.intellij.lang.annotations.Language;
             class X {
               void test(String mainQuery) {
                 @Language("JAVA") String countQuery = "select count(*) from (%s) as cnt".formatted(mainQuery);
               }
             }""",
           "select count(*) from (missingValue) as cnt");
  }

  @Test
  public void testStaticStringFormat() {
    doTest("""
             import org.intellij.lang.annotations.Language;
             class X {
               static final String MID = "X";
               void test() {
                 @Language("JAVA") String q = String.format("a%sb", MID);
               }
             }""",
           "aXb");
  }

  @Test
  public void testMultipleSpecifiers() {
    doTest("""
             import org.intellij.lang.annotations.Language;
             class X {
               static final String A = "p";
               static final String B = "q";
               void test() {
                 @Language("JAVA") String q = "%s and %s".formatted(A, B);
               }
             }""",
           "p and q");
  }

  @Test
  public void testBrokenSpecifiers() {
    doTest("""
             import org.intellij.lang.annotations.Language;
             class X {
               static final String A = "p";
               static final String B = "q";
               void test() {
                  @Language("JAVA") String q = "%1$s and %aaaa$s".formatted(A, B);
               }
             }""",
           //as is, because formatted is broken
           "p and paaa$s");
  }

  @Test
  public void testErrorIsHighlightedInFormattedInjection() {
    highlightTest("""
                    import org.intellij.lang.annotations.Language;
                    class X {
                      static final String NAME = "Foo";
                      void test() {
                        @Language("JAVA") String q = "class %s {<error descr="'}' expected">"</error>.formatted(NAME);
                      }
                    }""");
  }

  @Test
  public void testMalformedPositionalSpecifierSuppressesHighlighting() {
    // %99999999999$s overflows the positional index. No highlighting
    highlightTest("""
                    import org.intellij.lang.annotations.Language;
                    class X {
                      static final String A = "p";
                      void test() {
                        @Language("JAVA") String q = "class %99999999999$s {".formatted(A);
                      }
                    }""");
  }

  @Test
  public void testEscapedPercentWithoutArguments() {
    doTest("""
             import org.intellij.lang.annotations.Language;
             class X {
               void test() {
                 @Language("JAVA") String q = "100%%done".formatted();
               }
             }""",
           "100%done");
  }

  @Test
  public void testNoSpecifierKeepsWholeString() {
    doTest("""
             import org.intellij.lang.annotations.Language;
             class X {
               void test() {
                 @Language("JAVA") String q = "plain text".formatted();
               }
             }""",
           "plain text");
  }

  private void doTest(String text, String... expectedInjectedContent) {
    Configuration.AdvancedConfiguration configuration = Configuration.getInstance().getAdvancedConfiguration();
    Configuration.DfaOption oldOption = configuration.getDfaOption();
    try {
      // OFF disables variable-following so only the format-string injection is asserted; constants are still evaluated.
      configuration.setDfaOption(Configuration.DfaOption.OFF);
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.configureByText("X.java", text);
      ReadAction.runBlocking(() -> new InjectionTestFixture(myFixture).assertInjectedContent(expectedInjectedContent));
    }
    finally {
      configuration.setDfaOption(oldOption);
    }
  }

  private void highlightTest(String text) {
    Configuration.AdvancedConfiguration configuration = Configuration.getInstance().getAdvancedConfiguration();
    Configuration.DfaOption oldOption = configuration.getDfaOption();
    try {
      configuration.setDfaOption(Configuration.DfaOption.OFF);
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.configureByText("X.java", text);
      myFixture.testHighlighting(false, false, false);
    }
    finally {
      configuration.setDfaOption(oldOption);
    }
  }
}
