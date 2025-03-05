// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.overriding;

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
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
public class FindOverridingMethodsAndClassesTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "overriding/findOverridingMethodsAndClasses";
  }

  public void testSimpleCase() {
    doTest(1, 1);
  }

  public void testAnonymousClass() {
    doTest(2, 2);
  }

  public void testTraitImplementsInterface() {
    doTest(4, 4);
  }

  public void testClassExtendsTrait() {
    doTest(2, 2);
  }

  public void testTraitExtendsTrait() { doTest(2, 2); }

  public void testTraitImplementsTrait() { doTest(2, 2); }

  private void doTest(int methodCount, int classCount) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final GroovyFile groovyFile = (GroovyFile)myFixture.getFile();
    final PsiClass psiClass = groovyFile.getClasses()[0];
    final PsiMethod method = psiClass.getMethods()[0];

    final Collection<PsiMethod> methods = OverridingMethodsSearch.search(method, psiClass.getResolveScope(), true).findAll();
    TestCase.assertEquals("Method count is wrong", methodCount, methods.size());

    final Collection<PsiClass> classes = ClassInheritorsSearch.search(psiClass).findAll();
    TestCase.assertEquals("Class count is wrong", classCount, classes.size());
  }

  public void test_find_java_functional_expression_passed_into_a_groovy_method() {
    myFixture.addFileToProject("a.groovy", "interface I { void foo(); }; class C { static void bar(I i) {}}");
    myFixture.addFileToProject("a.java", "class D {{  C.bar(() -> {}); }");
    UsefulTestCase.assertSize(1, FunctionalExpressionSearch.search(myFixture.findClass("I")).findAll());
  }

  public void test_find_sub_classes_works_even_in_local_scope() {
    PsiFile t = myFixture.addFileToProject("x/T.groovy", "package x; trait T {}");
    PsiFile t2 = myFixture.addFileToProject("x/T2.groovy", "package x; class T2 implements T {}");

    PsiClass classT = ((GroovyFile)t).getClasses()[0];
    PsiClass classT2 = ((GroovyFile)t2).getClasses()[0];
    TestCase.assertTrue(Arrays.asList(classT2.getSupers()).contains(classT));

    PsiElement[] files = new PsiElement[]{t, t2};
    SearchScope scope = new LocalSearchScope(files);
    Collection<PsiClass> subClasses = DirectClassInheritorsSearch.search(classT, scope).findAll();
    PsiClass subClass = UsefulTestCase.assertOneElement(subClasses);
    TestCase.assertEquals("T2", subClass.getName());
  }

  public void test_find_groovy_inheritor_of_java_class_in_local_scope() {
    PsiClass superClass = myFixture.addClass("class Super {}");
    PsiFile file = myFixture.addFileToProject("a.groovy", "class Foo extends Super {}");

    assertEquals(1, ClassInheritorsSearch.search(superClass).findAll().size());
    assertEquals(1, ClassInheritorsSearch.search(superClass, new LocalSearchScope(file), true).findAll().size());
  }
}
