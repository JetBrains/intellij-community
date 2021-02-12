// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang.pattern;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class PatternValidatorTest extends LightJavaCodeInsightFixtureTestCase {
  public void testPatternValidator() {
    myFixture.enableInspections(new PatternValidator());
    myFixture.configureByText("Test.java", "import org.intellij.lang.annotations.Pattern;\n" +
                                           "\n" +
                                           "class X {\n" +
                                           "  @Pattern(\"[0-9]+\") String str = <warning descr=\"Expression '123a' doesn't match pattern: [0-9]+\">\"123a\"</warning>;\n" +
                                           "  \n" +
                                           "  @Anno(foo = <warning descr=\"Expression '123b' doesn't match pattern: [0-9]+\">\"123b\"</warning>)\n" +
                                           "  int x;\n" +
                                           "  \n" +
                                           "  @interface Anno {\n" +
                                           "    @Pattern(\"[0-9]+\") String foo();\n" +
                                           "  }\n" +
                                           "}");
    myFixture.checkHighlighting();
  }
}
