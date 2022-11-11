// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.style.ChainedMethodCallInspection;

public class IntroduceVariableFixTest extends LightJavaCodeInsightFixtureTestCase {

  public void testIntentionPreview() {
    myFixture.enableInspections(new ChainedMethodCallInspection());
    myFixture.configureByText("Test.java",
                              "import java.io.File;\n" +
                              "\n" +
                              "class X {\n" +
                              "    int foo(File f) {\n" +
                              "      return f.getName().lengt<caret>h();\n" +
                              "    }\n" +
                              "}");
    IntentionAction action = myFixture.findSingleIntention("Introduce variable");
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("import java.io.File;\n" +
                 "\n" +
                 "class X {\n" +
                 "    int foo(File f) {\n" +
                 "        String name = f.getName();\n" +
                 "        return name.length();\n" +
                 "    }\n" +
                 "}", text);
  }
}
