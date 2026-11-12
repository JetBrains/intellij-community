// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class Groovy25ResolveTest implements ResolveTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_2_5);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Test
  public void tap() {
    runInEdtAndWait(() -> {
      GrReferenceExpression expression = elementUnderCaret("""
        class A { def prop }
        new A().tap { <caret>prop = 1 }
        """, GrReferenceExpression.class);
      var reference = expression.getLValueReference();
      Assertions.assertNotNull(reference);
      var resolved = reference.resolve();
      assertInstanceOf(GrAccessorMethod.class, resolved);
    });
  }

  @Test
  public void recursiveType() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("A.java", """
        class A<SELF extends A<SELF>> {
           public static A<?> gen() {
               return null;
           }


           public SELF foo() { return (SELF)this;}
           public void bar(){}
        }""");
      resolveTest("A.gen().foo().<caret>bar()", PsiMethod.class);
    });
  }

  @Test
  public void wildcardType() {
    runInEdtAndWait(() -> {
      getFixture().addFileToProject("A.java", """
        class A<SELF extends Integer> {
           public static A<?> gen() {
               return null;
           }
           public SELF foo() { return null;}
        }""");
      resolveTest("A.gen().foo().<caret>byteValue()", PsiMethod.class);
    });
  }
}