// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;

import java.util.List;

public class ResolveIndexPropertyTest extends GroovyResolveTestCase {
  public void test_prefer_single_parameter() {
    getFixture().addFileToProject("classes.groovy", """
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

  public void test_resolve_with_unwrapped_argument_types() {
    getFixture().addFileToProject("classes.groovy", """
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

  public void test_putAt_with_three_parameters() {
    getFixture().addFileToProject("classes.groovy", "class A { def putAt(a, b, c) {} }");
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
    GroovyFile file = (GroovyFile)getFixture().configureByText("_.groovy", "def a = new A()\n" + text);
    GrStatement expression = DefaultGroovyMethods.last(file.getStatements());
    GroovyMethodCallReference reference = rValue ?
                                          ((GrIndexProperty)expression).getRValueReference() :
                                          ((GrIndexProperty)((GrAssignmentExpression)expression).getLValue()).getLValueReference();
    List<? extends GroovyResolveResult> results = ResolveUtilKt.valid(reference.resolve(false));
    if (methodIndex < 0) {
      assert results.isEmpty();
    } else {
      assert results.size() == 1;
      PsiMethod resolved = (PsiMethod)results.get(0).getElement();
      assertNotNull(resolved);
      assertEquals("A", resolved.getContainingClass().getQualifiedName());
      assertEquals(resolved, ((GrTypeDefinition)resolved.getContainingClass()).getCodeMethods()[methodIndex]);
    }
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;
}
