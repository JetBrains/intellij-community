// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.intelliLang;

import com.intellij.JavaTestUtil;
import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.plugins.intelliLang.references.InjectedReferencesInspection;

public class MethodNameReferenceCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/methodNameRef/";
  }

  public void testMethodName() {
    String input = "import org.intellij.lang.annotations.Language;\n" +
                  "\n" +
                  "class Scratch {\n" +
                  "  @Language(\"jvm-method-name\")\n" +
                  "  @interface MethodName {}\n" +
                  "\n" +
                  "  public @interface ComponentProperties {\n" +
                  "    // ... various other attributes ...\n" +
                  "\n" +
                  "    @MethodName\n" +
                  "    String waitForMethod() default \"\";\n" +
                  "  }\n" +
                  "\n" +
                  "  public class MainComponent {\n" +
                  "    @ComponentProperties(waitForMethod = \"<error descr=\"Cannot resolve symbol 'waitForB'\">waitForB<caret></error>\")\n" +
                  "    public BlueComponent blueComponent;\n" +
                  "\n" +
                  "    private void waitForBlueComponent() {\n" +
                  "      // ...\n" +
                  "    }\n" +
                  "  }\n" +
                  "  \n" +
                  "  interface BlueComponent {}\n" +
                  "}";
    myFixture.configureByText("MethodName.java", input);
    myFixture.enableInspections(new InjectedReferencesInspection());
    myFixture.checkHighlighting();
    myFixture.completeBasic();
    String result = "import org.intellij.lang.annotations.Language;\n" +
                  "\n" +
                  "class Scratch {\n" +
                  "  @Language(\"jvm-method-name\")\n" +
                  "  @interface MethodName {}\n" +
                  "\n" +
                  "  public @interface ComponentProperties {\n" +
                  "    // ... various other attributes ...\n" +
                  "\n" +
                  "    @MethodName\n" +
                  "    String waitForMethod() default \"\";\n" +
                  "  }\n" +
                  "\n" +
                  "  public class MainComponent {\n" +
                  "    @ComponentProperties(waitForMethod = \"waitForBlueComponent<caret>\")\n" +
                  "    public BlueComponent blueComponent;\n" +
                  "\n" +
                  "    private void waitForBlueComponent() {\n" +
                  "      // ...\n" +
                  "    }\n" +
                  "  }\n" +
                  "  \n" +
                  "  interface BlueComponent {}\n" +
                  "}";
    myFixture.checkResult(result);
    PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    assertTrue(element instanceof PsiJavaToken);
    element = element.getParent();
    assertTrue(element instanceof PsiLiteralExpression);
    PsiReference[] refs = element.getReferences();
    assertEquals(1, refs.length);
    PsiElement target = refs[0].resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals("waitForBlueComponent", ((PsiMethod)target).getName());
  }
}
