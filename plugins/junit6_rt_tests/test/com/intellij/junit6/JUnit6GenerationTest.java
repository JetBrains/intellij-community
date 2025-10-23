// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit6;

import com.intellij.testIntegration.BaseGenerateTestSupportMethodAction;
import com.intellij.testIntegration.TestIntegrationUtils;
import org.junit.jupiter.api.Test;

public class JUnit6GenerationTest extends JUnit6CodeInsightTest {
  @Test
  void testMethodInTopLevelClass() {
    doTest("import org.junit.jupiter.api.Test; class MyTest {<caret> @Test void m2(){}}",
           """
             import org.junit.jupiter.api.Test; class MyTest {
                 @Test
                 void name() {
                    \s
                 }

                 @Test void m2(){}}""");
  }

  @Test
  void testMethodInNestedClass() {
    doTest("import org.junit.jupiter.api.Nested; class MyTest { @Nested class NTest { <caret>}}",
           """
             import org.junit.jupiter.api.Nested;
             import org.junit.jupiter.api.Test;

             class MyTest { @Nested class NTest {
                 @Test
                 void name() {
                    \s
                 }
             }}""");
  }

  private void doTest(String text, String expected) {
    myFixture.configureByText("MyTest.java", text);

    new BaseGenerateTestSupportMethodAction.MyHandler(TestIntegrationUtils.MethodKind.TEST)
      .invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
    myFixture.checkResult(expected);
  }
}
