// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.encapsulation.PublicFieldInspection;

public class EncapsulateVariableFixTest extends LightJavaCodeInsightFixtureTestCase {

  public void testIntentionPreview() {
    myFixture.enableInspections(new PublicFieldInspection());
    myFixture.configureByText("Test.java",
                              """
                                class A {
                                    public String name<caret>;
                                }
                                class B {
                                    void foo(A a) {
                                        System.out.println(a.name);
                                    }
                                }""");
    IntentionAction action = myFixture.findSingleIntention("Encapsulate field 'name'");
    String text = myFixture.getIntentionPreviewText(action);
    assertEquals("""
                   class A {
                       private String name;

                       public String getName() {
                           return name;
                       }

                       public void setName(String name) {
                           this.name = name;
                       }
                   }
                   class B {
                       void foo(A a) {
                           System.out.println(a.getName());
                       }
                   }""", text);
  }
}
