// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.overriding;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class FindOverridingMethodsAndClassesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "overriding/findOverridingMethodsAndClasses");
  }

  @Test
  public void testSimpleCase() {
    doTest(1, 1);
  }

  @Test
  public void testAnonymousClass() {
    doTest(2, 2);
  }

  @Test
  public void testTraitImplementsInterface() {
    doTest(4, 4);
  }

  @Test
  public void testClassExtendsTrait() {
    doTest(2, 2);
  }

  @Test
  public void testTraitExtendsTrait() {
    doTest(2, 2);
  }

  @Test
  public void testTraitImplementsTrait() {
    doTest(2, 2);
  }

  private void doTest(int methodCount, int classCount) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    ReadAction.runBlocking(() -> {
      final GroovyFile groovyFile = (GroovyFile)myFixture.getFile();
      final PsiClass psiClass = groovyFile.getClasses()[0];
      final PsiMethod method = psiClass.getMethods()[0];

      final Collection<PsiMethod> methods = OverridingMethodsSearch.search(method, psiClass.getResolveScope(), true).findAll();
      assertEquals(methodCount, methods.size(), "Method count is wrong");

      final Collection<PsiClass> classes = ClassInheritorsSearch.search(psiClass).findAll();
      assertEquals(classCount, classes.size(), "Class count is wrong");
    });
  }

  @Test
  public void test_find_java_functional_expression_passed_into_a_groovy_method() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("a.groovy", "interface I { void foo(); }; class C { static void bar(I i) {}}");
    myFixture.addFileToProject("a.java", "class D {{  C.bar(() -> {}); }");
    assertThat(ReadAction.computeBlocking(()->FunctionalExpressionSearch.search(myFixture.findClass("I")).findAll())).hasSize(1);
  }

  @Test
  public void test_find_sub_classes_works_even_in_local_scope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile t = myFixture.addFileToProject("x/T.groovy", "package x; trait T {}");
    PsiFile t2 = myFixture.addFileToProject("x/T2.groovy", "package x; class T2 implements T {}");

    ReadAction.runBlocking(() -> {
      PsiClass classT = ((GroovyFile)t).getClasses()[0];
      PsiClass classT2 = ((GroovyFile)t2).getClasses()[0];
      assertTrue(Arrays.asList(classT2.getSupers()).contains(classT));

      PsiElement[] files = new PsiElement[]{t, t2};
      SearchScope scope = new LocalSearchScope(files);
      Collection<PsiClass> subClasses = DirectClassInheritorsSearch.search(classT, scope).findAll();
      PsiClass subClass = assertThat(subClasses).singleElement().actual();
      assertEquals("T2", subClass.getName());
    });
  }

  @Test
  public void test_find_groovy_inheritor_of_java_class_in_local_scope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass superClass = myFixture.addClass("class Super {}");
    PsiFile file = myFixture.addFileToProject("a.groovy", "class Foo extends Super {}");

    assertEquals(1, ReadAction.computeBlocking(()->ClassInheritorsSearch.search(superClass).findAll()).size());
    assertEquals(1, ReadAction.computeBlocking(()->ClassInheritorsSearch.search(superClass, new LocalSearchScope(file), true).findAll()).size());
  }
}
