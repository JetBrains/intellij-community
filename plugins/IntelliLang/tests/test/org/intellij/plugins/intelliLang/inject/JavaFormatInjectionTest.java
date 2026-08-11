// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.testFramework.LightProjectDescriptor;
import org.intellij.plugins.intelliLang.AbstractLanguageInjectionTestCase;
import org.intellij.plugins.intelliLang.Configuration;
import org.jetbrains.annotations.NotNull;

public class JavaFormatInjectionTest extends AbstractLanguageInjectionTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

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
      myFixture.configureByText("X.java", text);
      getInjectionTestFixture().assertInjectedContent(expectedInjectedContent);
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
      myFixture.configureByText("X.java", text);
      myFixture.testHighlighting(false, false, false);
    }
    finally {
      configuration.setDfaOption(oldOption);
    }
  }
}
