// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.groovy.testFramework.TypingTest;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MethodReferenceTest implements TypingTest, ResolveTest, HighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "lang/methodReferences/");

  @Override
  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Override
  public String getTestName() {
    return LightGroovyFixtures.groovyTestName(codeInsightFixture.get());
  }


  @Test
  public void testMethodReferenceInStaticContext() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("classes.groovy", """
      class C {\s
        C(int a) {}
        static foo() {}
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      Collection<? extends GroovyResolveResult> results = multiResolveByText("C::<caret>foo");
      assertEquals(1, results.size());
      assertTrue(ContainerUtil.getFirstItem(results).getElement() instanceof GrMethodImpl);
    });
  }

  @Test
  public void testMultipleConstructors() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testConstructorInStaticContext() {
    EdtTestUtil.runInEdtAndWait(() -> {
      Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                               class D { D() {} }
                                                                               D::<caret>new
                                                                               """);
      assertEquals(1, results.size());
      assertTrue(ContainerUtil.getFirstItem(results).getElement() instanceof GrConstructorImpl);
    });
  }

  @Test
  public void testStaticMethodInStaticContext() {
    EdtTestUtil.runInEdtAndWait(() -> {
      Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                               class D { static 'new'() {} }
                                                                               D::<caret>new
                                                                               """);
      assertEquals(2, results.size());
      Iterator<? extends GroovyResolveResult> iterator = results.iterator();
      assertTrue(iterator.next().getElement() instanceof GrMethodImpl);
      assertTrue(iterator.next().getElement() instanceof DefaultConstructor);
    });
  }

  @Test
  public void testConstructorInInstanceContext() {
    EdtTestUtil.runInEdtAndWait(() -> {
      Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                               class D { D() {} }
                                                                               def d = new D()
                                                                               d::<caret>new
                                                                               """);
      assertThat(results).isNullOrEmpty();
    });
  }

  @Test
  public void testInstanceMethodInInstanceContext() {
    EdtTestUtil.runInEdtAndWait(() -> {
      Collection<? extends GroovyResolveResult> results = multiResolveByText("""
                                                                               class D { def 'new'() {} }
                                                                               def d = new D()
                                                                               d::<caret>new
                                                                               """);
      assertEquals(1, results.size());
      assertTrue(ContainerUtil.getFirstItem(results).getElement() instanceof GrMethodImpl);
    });
  }

  @Test
  public void testTyping() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("classes.groovy", """
      class D {
        def prop1
        D() {}
        D(int a) {}
        static long 'new'(String b) { 42l }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      expressionTypeTest("def ref = D::new; ref(1)", "D");
      expressionTypeTest("def ref = D::new; ref(\"hello\")", "long");
    });
  }

  @Test
  public void testArrayTyping() {
    EdtTestUtil.runInEdtAndWait(() -> {
      expressionTypeTest("Integer[]::new(0)", "java.lang.Integer[]");
      expressionTypeTest("int[][]::new(0)", "int[][]");
      expressionTypeTest("int[][]::new(1, 2)", "int[][]");
    });
  }

  @Test
  public void testConstructorsHighlighting() { fileHighlightingTest(); }

  @Test
  public void testArrayConstructorsHighlighting() { fileHighlightingTest(); }

  @Test
  public void testConstructorSearchAndRename() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("classes.groovy", "class A { A(String p){} }");
    configureByText("""
                      <caret>A::new
                      A::'new'
                      A::"new"
                      """);

    PsiMethod constructor = ReadAction.computeBlocking(() -> myFixture.findClass("A").getConstructors()[0]);
    assertEquals(3, ReadAction.computeBlocking(()->MethodReferencesSearch.search(constructor).findAll()).size());

    EdtTestUtil.runInEdtAndWait(() -> myFixture.renameElementAtCaret("B"));
    myFixture.checkResult("""
                               B::new
                               B::'new'
                               B::"new"
                               """);
  }

  @Override
  public final @NotNull List<Class<? extends LocalInspectionTool>> getInspections() {
    return List.of(GroovyAssignabilityCheckInspection.class);
  }
}
