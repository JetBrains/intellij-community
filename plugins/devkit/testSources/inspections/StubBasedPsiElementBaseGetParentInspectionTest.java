/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;

import java.util.List;

public class StubBasedPsiElementBaseGetParentInspectionTest extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package com.intellij.psi; public class PsiElement {}");
    myFixture.addClass("package com.intellij.extapi.psi; " +
                       "public class StubBasedPsiElementBase {" +
                       "  public com.intellij.psi.PsiElement getParent() { return null; }" +
                       "}");

    myFixture.enableInspections(new StubBasedPsiElementBaseGetParentInspection());
  }

  public void testOverridesGetParent() {
    myFixture.configureByText("MyStub.java",
                              "public class MyStub extends com.intellij.extapi.psi.StubBasedPsiElementBase {" +
                              "  public com.intellij.psi.PsiElement getParent() { return null; }" +
                              "}");
    myFixture.testHighlighting();
  }

  public void testOverridesGetParentInHierarchy() {
    myFixture.configureByText("MyStubBase.java",
                              "public class MyStubBase extends com.intellij.extapi.psi.StubBasedPsiElementBase {" +
                              "  public com.intellij.psi.PsiElement getParent() { return null; }" +
                              "}");
    myFixture.configureByText("MyStub.java",
                              "public class MyStub extends MyStubBase {" +
                              "}");
    myFixture.testHighlighting();
  }

  public void testDoesNotOverrideGetParent() {
    myFixture.configureByText("MyStub.java",
                              "public class <warning descr=\"Default getParent() implementation is slow\">MyStub</warning>" +
                              " extends com.intellij.extapi.psi.StubBasedPsiElementBase {" +
                              "}");
    myFixture.testHighlighting();
    final List<IntentionAction> fixes = myFixture.getAllQuickFixes();
    final IntentionAction quickFix = assertOneElement(fixes);
    myFixture.launchAction(quickFix);
    myFixture.checkResult("import com.intellij.psi.PsiElement;\n" +
                          "\n" +
                          "public class MyStub extends com.intellij.extapi.psi.StubBasedPsiElementBase {\n" +
                          "    @Override\n" +
                          "    public PsiElement getParent() {\n" +
                          "        return getParentByStub();\n" +
                          "    }\n" +
                          "}");
  }

  public void testDoesNotOverrideGetParentNoHighlightInAbstractClass() {
    myFixture.configureByText("MyStub.java",
                              "public abstract class MyStub extends com.intellij.extapi.psi.StubBasedPsiElementBase {" +
                              "}");
    myFixture.testHighlighting();
  }
}
