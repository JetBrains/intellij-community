// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.inject;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.plugins.intelliLang.Configuration;

public class DfaInjectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimple() {
    highlightTest("import org.intellij.lang.annotations.Language;\n" +
                  "\n" +
                  "class X {\n" +
                  "  void test() {\n" +
                  "    String javaCode = \"class X {<error descr=\"'}' expected\">\"</error>;\n" +
                  "    foo(javaCode);\n" +
                  "  }\n" +
                  "  \n" +
                  "  native void foo(@Language(\"JAVA\") String str);\n" +
                  "}");
  }
  
  public void testConcatenation() {
    highlightTest("import org.intellij.lang.annotations.Language;\n" +
                  "\n" +
                  "class X {\n" +
                  "  void test() {\n" +
                  "    String content = \"void test() {\";\n" +
                  "    content += \"}}\";\n" +
                  "    foo(\"class X {\"+content+\"<error descr=\"'class' or 'interface' expected\">}</error>\");\n" +
                  "  }\n" +
                  "\n" +
                  "  native void foo(@Language(\"JAVA\") String str);\n" +
                  "}");
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
