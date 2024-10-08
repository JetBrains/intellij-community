// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.findUsages;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Query;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Collection;
import java.util.Iterator;

public class FindUsagesTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "findUsages/" + getTestName(true) + "/";
  }

  private void doConstructorTest(String filePath, int expectedCount) {
    myFixture.configureByFile(filePath);
    final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiMethod method = PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
    TestCase.assertNotNull(method);
    TestCase.assertTrue(method.isConstructor());
    final Query<PsiReference> query = ReferencesSearch.search(method);

    TestCase.assertEquals(expectedCount, query.findAll().size());
  }

  public void testDerivedClass() {
    myFixture.configureByFiles("p/B.java", "A.groovy");
    final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final PsiClass clazz = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
    TestCase.assertNotNull(clazz);

    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    final Query<PsiClass> query = DirectClassInheritorsSearch.search(clazz, projectScope);

    TestCase.assertEquals(1, query.findAll().size());
  }

  public void testConstructor1() {
    doConstructorTest("A.groovy", 2);
  }

  public void testConstructorUsageInNewExpression() {
    myFixture.configureByFile("ConstructorUsageInNewExpression.groovy");
    final PsiElement resolved =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getReferenceSearchFlags());
    TestCase.assertNotNull("Could not resolve reference", resolved);
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    TestCase.assertEquals(2, MethodReferencesSearch.search((PsiMethod)resolved, projectScope, true).findAll().size());
    TestCase.assertEquals(4, MethodReferencesSearch.search((PsiMethod)resolved, projectScope, false).findAll().size());
  }

  public void testGotoConstructor() {
    myFixture.configureByFile("GotoConstructor.groovy");
    final PsiElement target =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getReferenceSearchFlags());
    TestCase.assertNotNull(target);
    UsefulTestCase.assertInstanceOf(target, PsiMethod.class);
    TestCase.assertTrue(((PsiMethod)target).isConstructor());
    TestCase.assertEquals(0, ((PsiMethod)target).getParameterList().getParametersCount());
  }

  public void testSetter1() {
    doTestImpl("A.groovy", 2);
  }

  public void testGetter1() {
    doTestImpl("A.groovy", 1);
  }

  public void testProperty1() {
    doTestImpl("A.groovy", 2);
  }

  public void testProperty2() {
    doTestImpl("A.groovy", 1);
  }

  public void testEscapedReference() {
    doTestImpl("A.groovy", 1);
  }

  public void testKeywordPropertyName() {
    doTestImpl("A.groovy", 1);
  }

  public void testTypeAlias() {
    doTestImpl("A.groovy", 2);
  }

  public void testMethodAlias() {
    doTestImpl("A.groovy", 2);
  }

  public void testAliasImportedProperty() {
    myFixture.addFileToProject("Abc.groovy", "class Abc {static def foo}");
    doTestImpl("A.groovy", 5);
  }

  public void testGetterWhenAliasedImportedProperty() {
    myFixture.addFileToProject("Abc.groovy", "class Abc {static def foo}");
    doTestImpl("A.groovy", 5);
  }

  public void testForInParameter() {
    doTestImpl("A.groovy", 1);
  }

  public void testSyntheticParameter() {
    doTestImpl("A.groovy", 1);
  }

  public void testOverridingMethodUsage() {
    doTestImpl("OverridingMethodUsage.groovy", 2);
  }

  public void testDynamicUsages() {
    doTestImpl("DynamicUsages.groovy", 2);
  }

  public void testDynamicCallExpressionUsages() {
    doTestImpl("DynamicCallExpressionUsages.groovy", 2);
  }

  public void testAnnotatedMemberSearch() {

    final PsiReference ref = myFixture.getReferenceAtCaretPosition("A.groovy");
    TestCase.assertNotNull("Did not find reference", ref);
    final PsiElement resolved = ref.resolve();
    TestCase.assertNotNull("Could not resolve reference", resolved);

    final Query<PsiReference> query;
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
    if (resolved instanceof PsiMethod) {
      query = MethodReferencesSearch.search((PsiMethod)resolved, projectScope, true);
    }
    else {
      query = ReferencesSearch.search(resolved, projectScope);
    }


    TestCase.assertEquals(1, query.findAll().size());
  }

  private void doTestImpl(String filePath, int expectedUsagesCount) {
    myFixture.configureByFile(filePath);
    assertUsageCount(expectedUsagesCount);
  }

  private void assertUsageCount(int expectedUsagesCount) {
    final PsiElement resolved =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getReferenceSearchFlags());
    TestCase.assertNotNull("Could not resolve reference", resolved);
    doFind(expectedUsagesCount, resolved);
  }

  private void doFind(int expectedUsagesCount, PsiElement resolved) {
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(getProject())).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(resolved, false);
    TestCase.assertNotNull(handler);
    final FindUsagesOptions options = handler.getFindUsagesOptions();
    final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<>();
    for (PsiElement element : handler.getPrimaryElements()) {
      handler.processElementUsages(element, processor, options);
    }

    for (PsiElement element : handler.getSecondaryElements()) {
      handler.processElementUsages(element, processor, options);
    }

    TestCase.assertEquals(expectedUsagesCount, processor.getResults().size());
  }

  public void testGdkMethod() {
    myFixture.configureByText("a.groovy", "[''].ea<caret>ch {}");
    assertUsageCount(1);
  }

  public void testGDKSuperMethodSearch() {
    doSuperMethodTest("T");
  }

  public void testGDKSuperMethodForMapSearch() {
    doSuperMethodTest("Map");
  }

  public void testLabels() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final GroovyFile file = (GroovyFile)myFixture.getFile();
    TestCase.assertEquals(2, ReferencesSearch.search(file.getTopStatements()[0]).findAll().size());
  }

  public void testConstructorUsageInAnonymousClass() {
    doTestImpl("A.groovy", 1);
  }

  public void testCapitalizedProperty1() {
    doTest(1, """
      class A {
        def Pro<caret>p
      }
      
      new A().Prop""");
  }

  public void testCapitalizedProperty2() {
    doTest(1, """
      class A {
        def Pro<caret>p
      }
      
      new A().prop""");
  }

  public void testCapitalizedProperty3() {
    doTest(0, """
      class A {
        def pro<caret>p
      }
      
      new A().Prop""");
  }

  public void testCapitalizedProperty4() {
    doTest(1, """
      class A {
        def p<caret>rop
      }
      
      new A().prop""");
  }

  public void testCategoryProperty() {
    doTest(1, """
      class Cat {
        static def ge<caret>tFoo(Number s) {'num'}
      }
      
      use(Cat) {
        2.foo
      }
      """);
  }

  public void test_do_not_report_dynamic_usages_when_argument_count_differs() {
    doTest(0, """
      class A {
          static void start<caret>sWith(String s, String s1, boolean b) {}
          def c = { it.startsWith("aaa") }
      }
      """);
  }

  public void test_literal_names() {
    doTest(1, """
      def 'f<caret>oo'() {}
      
      'foo'()
      """);
  }

  public void test_literal_name_with_escaping() {
    doTest(1, """
      def 'f<caret>oo \\''() {}
      
      'foo \\''()
      """);
  }

  public void test_literal_name_with_escaping_and_other_quotes() {
    doTest(1, """
      def 'f<caret>oo \\''() {}
      
      "foo '"()
      """);
  }

  public void _test_literal_name_with_escaping_and_other_quotes_2() {
    doTest(1, """
      def '<caret>\\''() {}
      
      "'"()
      """);
  }

  public void testResolveBinding1() {
    doTest(2, """
      abc = 4
      
      print ab<caret>c
      """);
  }

  public void testResolveBinding3() {
    doTest(2, """
      a<caret>bc = 4
      
      print abc
      """);
  }

  public void testResolveBinding4() {
    doTest(1, """
      print abc
      
      a<caret>bc = 4
      """);
  }

  public void testBinding9() {
    doTest(4, """
      a<caret>a = 5
      print aa
      aa = 6
      print aa
      """);
  }

  public void testBinding10() {
    doTest(4, """
      aa = 5
      print a<caret>a
      aa = 6
      print aa
      """);
  }

  public void testBinding11() {
    doTest(4, """
      aa = 5
      print aa
      a<caret>a = 6
      print aa
      """);
  }

  public void testBinding12() {
    doTest(4, """
      aa = 5
      print aa
      aa = 6
      print a<caret>a
      """);
  }

  public void testBinding13() {
    doTest(2, """
      aaa = 1
      
      def foo() {
        aa<caret>a
      }
      """);
  }

  public void testBinding14() {
    doTest(2, """
      aa<caret>a = 1
      
      def foo() {
        aaa
      }
      """);
  }

  public void testBinding15() {
    doTest(1, """
      def foo() {
        aaa
      }
      
      aa<caret>a = 1
      """);
  }

  public void testTraitField() {
    doTest(4, """
      
      trait T {
        public int fi<caret>eld = 4
      
        def bar() {
          print field
          print T__field
        }
      }
      
      class C implements T {
        def abc() {
          print field         //unresolved
          print T__field
        }
      }
      
      
      new C().T__field
      new C().field             //unresolved
      
      """);
  }

  public void testImports() {
    doTest(2, """
      package com.foo
      import static com.foo.Bar.foo
      import static com.foo.Bar.getFoo
      import static com.foo.Bar.isFoo // is not proper ref\s
      class Bar { static def <caret>getFoo() {} }
      """);
  }

  private void doSuperMethodTest(String... firstParameterTypes) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    final GroovyFile file = (GroovyFile)myFixture.getFile();
    final GrTypeDefinition psiClass = (GrTypeDefinition)file.getClasses()[0];
    final GrMethod method = (GrMethod)psiClass.getMethods()[0];
    final Collection<MethodSignatureBackedByPsiMethod> superMethods = SuperMethodsSearch.search(method, null, true, true).findAll();
    TestCase.assertEquals(firstParameterTypes.length, superMethods.size());

    final Iterator<MethodSignatureBackedByPsiMethod> iterator = superMethods.iterator();
    for (String firstParameterType : firstParameterTypes) {
      final MethodSignatureBackedByPsiMethod methodSignature = iterator.next();
      final PsiMethod superMethod = methodSignature.getMethod();
      final String className = superMethod.getContainingClass().getName();
      TestCase.assertEquals("DefaultGroovyMethods", className);
      final String actualParameterType = ((PsiClassType)methodSignature.getParameterTypes()[0]).resolve().getName();
      TestCase.assertEquals(firstParameterType, actualParameterType);
    }
  }

  private void doTest(int usageCount, String text) {
    myFixture.configureByText("_.groovy", text);
    assertUsageCount(usageCount);
  }

  public void testWholeWordsIndexIsBuiltForLiterals() {
    myFixture.configureByText("_.groovy", "11");
    PsiFile[] words = PsiSearchHelper.getInstance(getProject()).findFilesWithPlainTextWords("11");
    TestCase.assertEquals(1, words.length);
  }

  public void test_find_usages_for_path_of_a_package() {
    PsiFile file = myFixture.addFileToProject("aaa/bbb/Foo.groovy", "package aaa.bbb \n class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "import aaa.bbb.Foo");
    PsiElement packageStatement = file.getChildren()[0].getChildren()[1].getReference().resolve();// package 'aaa.bbb'
    TestCase.assertEquals(2, ReferencesSearch.search(packageStatement).findAll().size());
  }
}
