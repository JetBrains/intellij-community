// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ResolveIndexPropertyTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @Test
  public void test_prefer_single_parameter() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("classes.groovy", """
      class A {
        def getAt(a) {}
        def getAt(Integer a) {}
        def getAt(List a) {}
        def getAt(a, b, c) {}
      }
      """);

    doTest("a[0]", 1);
    doTest("a[[0]]", 2);
    doTest("a[0, 1, 2]", 2);
    doTest("def l = [0]; a[l]", 2);
    doTest("def l = [0, 1, 2]; a[l]", 2);
  }

  @Test
  public void test_resolve_with_unwrapped_argument_types() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("classes.groovy", """
        class A {
          def getAt(Integer a) {}
          def getAt(a, b, c) {}
        }
      """);

    doTest("a[0]", 0);
    doTest("a[[0]]", 0);
    doTest("a[0, 1, 2]", 1);
    doTest("a[[0, 1, 2]]", 1);
    doTest("def l = [0]; a[l]", 0);
    doTest("def l = [0]; a[[l]]");
    doTest("def l = [0, 1, 2]; a[l]", 1);
    doTest("def l = [0, 1, 2]; a[[l]]");
  }

  @Test
  public void test_putAt_with_three_parameters() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("classes.groovy", "class A { def putAt(a, b, c) {} }");
    doLTest("a[1, 2] = 3");
  }

  private void doTest(String text, int methodIndex) {
    doTest(text, true, methodIndex);
  }

  private void doTest(String text) {
    doTest(text, -1);
  }

  private void doLTest(String text, int methodIndex) {
    doTest(text, false, methodIndex);
  }

  private void doLTest(String text) {
    doLTest(text, -1);
  }

  private void doTest(String text, boolean rValue, int methodIndex) {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    GroovyFile file = (GroovyFile)myFixture.configureByText("_.groovy", "def a = new A()\n" + text);
    ReadAction.runBlocking(() -> {
      GrStatement expression = DefaultGroovyMethods.last(file.getStatements());
      GroovyMethodCallReference reference = rValue ?
                                            ((GrIndexProperty)expression).getRValueReference() :
                                            ((GrIndexProperty)((GrAssignmentExpression)expression).getLValue()).getLValueReference();
      List<? extends GroovyResolveResult> results = ResolveUtilKt.valid(reference.resolve(false));
      if (methodIndex < 0) {
        assertThat(results).isNullOrEmpty();
      } else {
        assertThat(results).hasSize(1);
        PsiMethod resolved = (PsiMethod)results.get(0).getElement();
        assertNotNull(resolved);
        assertEquals("A", resolved.getContainingClass().getQualifiedName());
        assertEquals(resolved, ((GrTypeDefinition)resolved.getContainingClass()).getCodeMethods()[methodIndex]);
      }
    });
  }
}
