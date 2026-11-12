// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.groovy.testFramework.GroovyAssertions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GrAutoCloneTransformationSupportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testCloneReturnType() {
    doExpressionTypeTest("""
                           @groovy.transform.AutoClone
                           class A1 {}
                           new A1().clo<caret>ne()
                           """, "A1");
  }

  @Test
  public void testCloneReturnTypeOverridden() {
    doExpressionTypeTest("""
                           @groovy.transform.AutoClone
                           class A1 {}
                           @groovy.transform.AutoClone
                           class A2 extends A1 {}

                           new A2().clo<caret>ne()
                           """, "A2");
  }

  @Test
  public void testCloneUsageFromJava() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Pogo.groovy", """
          @groovy.transform.AutoClone
          class Pogo {}\s
          """);
    myFixture.configureByText("Main.java", """
          class Main {
            void foo() {
              new Pogo().<error descr="Unhandled exception: java.lang.CloneNotSupportedException">clone</error>();
              try {
                 Pogo pogo = new Pogo().clone();
              } catch (java.lang.CloneNotSupportedException e) {}
            }
          }
          """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testCopyConstructorUsageFromJava() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Pogo.groovy", """
          @groovy.transform.AutoClone(style=groovy.transform.AutoCloneStyle.COPY_CONSTRUCTOR)
          class Pogo {}\s
          """);
    myFixture.configureByText("Main.java", """
          class Main {
            void foo() {
              Pogo a = new Pogo();
              Pogo b = new Pogo(a);
            }
          }
          """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testCloneOrCopyMembersUsageFromJava() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Pogo.groovy", """
          @groovy.transform.AutoClone(style=groovy.transform.AutoCloneStyle.SIMPLE)
          class Pogo {}\s
          """);
    myFixture.configureByText("Pojo.java", """
          class Pojo extends Pogo {
            void foo() {
              Pogo pogo = new Pogo();\s
              <error descr="Unhandled exception: java.lang.CloneNotSupportedException">cloneOrCopyMembers</error>(pogo);
              cloneOrCopyMembers<error descr="Expected 1 argument but found 0">()</error>;
            }
          }
          """);
    myFixture.checkHighlighting();
  }

  private void doExpressionTypeTest(String text, String expectedType) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("_.groovy", text);
    ReadAction.runBlocking(() -> {
      GrReferenceExpression ref =(GrReferenceExpression) file.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      PsiType actual = ref.getType();
      GroovyAssertions.assertType(expectedType, actual);
    });
  }
}
