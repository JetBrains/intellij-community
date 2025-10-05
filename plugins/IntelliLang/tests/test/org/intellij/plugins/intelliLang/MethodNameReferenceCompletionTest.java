// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    String input = """
      import org.intellij.lang.annotations.Language;

      class Scratch {
        @Language("jvm-method-name")
        @interface MethodName {}

        public @interface ComponentProperties {
          // ... various other attributes ...

          @MethodName
          String waitForMethod() default "";
        }

        public class MainComponent {
          @ComponentProperties(waitForMethod = "<error descr="Cannot resolve symbol 'waitForB'">waitForB<caret></error>")
          public BlueComponent blueComponent;

          private void waitForBlueComponent() {
            // ...
          }
        }
       \s
        interface BlueComponent {}
      }""";
    myFixture.configureByText("MethodName.java", input);
    myFixture.enableInspections(new InjectedReferencesInspection());
    myFixture.checkHighlighting();
    myFixture.completeBasic();
    String result = """
      import org.intellij.lang.annotations.Language;

      class Scratch {
        @Language("jvm-method-name")
        @interface MethodName {}

        public @interface ComponentProperties {
          // ... various other attributes ...

          @MethodName
          String waitForMethod() default "";
        }

        public class MainComponent {
          @ComponentProperties(waitForMethod = "waitForBlueComponent<caret>")
          public BlueComponent blueComponent;

          private void waitForBlueComponent() {
            // ...
          }
        }
       \s
        interface BlueComponent {}
      }""";
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
