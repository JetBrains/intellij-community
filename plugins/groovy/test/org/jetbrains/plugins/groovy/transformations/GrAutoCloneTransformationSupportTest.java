// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;


public class GrAutoCloneTransformationSupportTest extends LightGroovyTestCase {
  @Override
  @NotNull
  public LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
  
  public void testCloneReturnType() {
    doExpressionTypeTest("""
                           @groovy.transform.AutoClone
                           class A1 {}
                           new A1().clo<caret>ne()
                           """, "A1");
  }

  public void testCloneReturnTypeOverridden() {
    doExpressionTypeTest("""
                           @groovy.transform.AutoClone
                           class A1 {}
                           @groovy.transform.AutoClone
                           class A2 extends A1 {}

                           new A2().clo<caret>ne()
                           """, "A2");
  }

  public void testCloneUsageFromJava() {
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

  public void testCopyConstructorUsageFromJava() {
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

  public void testCloneOrCopyMembersUsageFromJava() {
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
    PsiFile file = myFixture.configureByText("_.groovy", text);
    GrReferenceExpression ref =(GrReferenceExpression) file.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    PsiType actual = ref.getType();
    LightGroovyTestCase.assertType(expectedType, actual);
  }
}
