// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.ResolveTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.jetbrains.plugins.groovy.util.TypingTest;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class MethodReferenceTest extends LightGroovyTestCase implements TypingTest, ResolveTest, HighlightingTest {
  public void testMethodReferenceInStaticContext() {
    myFixture.addFileToProject("classes.groovy", """
      class C {\s
        C(int a) {}
        static foo() {}
      }
      """);
    Collection<? extends GroovyResolveResult> results = multiResolveByText("C::<caret>foo");
    assertEquals(1, results.size());
    assertTrue(ContainerUtil.getFirstItem(results).getElement() instanceof GrMethodImpl);
  }

  public void testMultipleConstructors() {
    Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                             class D {
                                                                               D(a) {}
                                                                               D(a, b) {}
                                                                             }
                                                                             D::<caret>new
                                                                             """);
    assertEquals(2, results.size());
    for (GroovyResolveResult result : results) {
      assertTrue(result.getElement() instanceof GrConstructorImpl);
    }
  }

  public void testConstructorInStaticContext() {
    Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                             class D { D() {} }
                                                                             D::<caret>new
                                                                             """);
    assertEquals(1, results.size());
    assertTrue(ContainerUtil.getFirstItem(results).getElement() instanceof GrConstructorImpl);
  }

  public void testStaticMethodInStaticContext() {
    Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                             class D { static 'new'() {} }
                                                                             D::<caret>new
                                                                             """);
    assertEquals(2, results.size());
    Iterator<? extends GroovyResolveResult> iterator = results.iterator();
    assertTrue(iterator.next().getElement() instanceof GrMethodImpl);
    assertTrue(iterator.next().getElement() instanceof DefaultConstructor);
  }

  public void testConstructorInInstanceContext() {
    Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                             class D { D() {} }
                                                                             def d = new D()
                                                                             d::<caret>new
                                                                             """);
    assertEmpty(results);
  }

  public void testInstanceMethodInInstanceContext() {
    Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                             class D { def 'new'() {} }
                                                                             def d = new D()
                                                                             d::<caret>new
                                                                             """);
    assertEquals(1, results.size());
    assertTrue(ContainerUtil.getFirstItem(results).getElement() instanceof GrMethodImpl);
  }

  public void testTyping() {
    myFixture.addFileToProject("classes.groovy", """
      class D {
        def prop1
        D() {}
        D(int a) {}
        static long 'new'(String b) { 42l }
      }
      """);
    expressionTypeTest("def ref = D::new; ref(1)", "D");
    expressionTypeTest("def ref = D::new; ref(\"hello\")", "long");
  }

  public void testArrayTyping() {
    expressionTypeTest("Integer[]::new(0)", "java.lang.Integer[]");
    expressionTypeTest("int[][]::new(0)", "int[][]");
    expressionTypeTest("int[][]::new(1, 2)", "int[][]");
  }

  public void testConstructorsHighlighting() { fileHighlightingTest(); }

  public void testArrayConstructorsHighlighting() { fileHighlightingTest(); }

  public void testConstructorSearchAndRename() {
    myFixture.addFileToProject("classes.groovy", "class A { A(String p){} }");
    configureByText("""
                      <caret>A::new
                      A::'new'
                      A::"new"
                      """);

    PsiClass clazz = myFixture.findClass("A");
    PsiMethod constructor = clazz.getConstructors()[0];
    assertEquals(3, MethodReferencesSearch.search(constructor).findAll().size());

    myFixture.renameElementAtCaret("B");
    myFixture.checkResult("""
                               B::new
                               B::'new'
                               B::"new"
                               """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_3_0;
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "lang/methodReferences/";
  }

  @Override
  public final @NotNull List<Class<? extends LocalInspectionTool>> getInspections() {
    return List.of(GroovyAssignabilityCheckInspection.class);
  }
}
