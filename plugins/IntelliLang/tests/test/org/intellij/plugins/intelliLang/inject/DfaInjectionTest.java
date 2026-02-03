// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.plugins.intelliLang.Configuration;

public class DfaInjectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimple() {
    highlightTest("""
                    import org.intellij.lang.annotations.Language;

                    class X {
                      void test() {
                        String javaCode = "class X {<error descr="'}' expected">"</error>;
                        foo(javaCode);
                      }
                     \s
                      native void foo(@Language("JAVA") String str);
                    }""");
  }
  
  public void testConcatenation() {
    highlightTest("""
                    import org.intellij.lang.annotations.Language;

                    class X {
                      void test() {
                        String content = "void test() {";
                        content += "}}";
                        foo("class X {"+content+"<error descr="'class' or 'interface' expected">}</error>");
                      }

                      native void foo(@Language("JAVA") String str);
                    }""");
  }

  public void highlightTest(String text) {
    Configuration.AdvancedConfiguration configuration = Configuration.getInstance().getAdvancedConfiguration();
    Configuration.DfaOption oldOption = configuration.getDfaOption();
    try {
      configuration.setDfaOption(Configuration.DfaOption.DFA);
      myFixture.configureByText("X.java", text);
      myFixture.testHighlighting();
    }
    finally {
      configuration.setDfaOption(oldOption);
    }
  }
}
