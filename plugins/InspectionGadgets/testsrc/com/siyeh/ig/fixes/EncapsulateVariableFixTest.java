// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.encapsulation.PublicFieldInspection;

public class EncapsulateVariableFixTest extends LightJavaCodeInsightFixtureTestCase {

  public void testIntentionPreview() {
    myFixture.enableInspections(new PublicFieldInspection());
    myFixture.configureByText("Test.java",
                              "class A {\n" +
                              "    public String name<caret>;\n" +
                              "}\n" +
                              "class B {\n" +
                              "    void foo(A a) {\n" +
                              "        System.out.println(a.name);\n" +
                              "    }\n" +
                              "}");
    IntentionAction action = myFixture.findSingleIntention("Encapsulate field 'name'");
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("class A {\n" +
                 "    private String name;\n" +
                 "\n" +
                 "    public String getName() {\n" +
                 "        return name;\n" +
                 "    }\n" +
                 "\n" +
                 "    public void setName(String name) {\n" +
                 "        this.name = name;\n" +
                 "    }\n" +
                 "}\n" +
                 "class B {\n" +
                 "    void foo(A a) {\n" +
                 "        System.out.println(a.getName());\n" +
                 "    }\n" +
                 "}", text);
  }
}
