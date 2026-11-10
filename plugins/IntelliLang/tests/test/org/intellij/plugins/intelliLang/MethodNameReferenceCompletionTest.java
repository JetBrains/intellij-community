// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.plugins.intelliLang.references.InjectedReferencesInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MethodNameReferenceCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/methodNameRef/");

  @Test
  public void testMethodName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    ReadAction.runBlocking(() -> {
      PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      assertTrue(element instanceof PsiJavaToken);
      element = element.getParent();
      assertTrue(element instanceof PsiLiteralExpression);
      PsiReference[] refs = element.getReferences();
      assertEquals(1, refs.length);
      PsiElement target = refs[0].resolve();
      assertTrue(target instanceof PsiMethod);
      assertEquals("waitForBlueComponent", ((PsiMethod)target).getName());
    });
  }
}
