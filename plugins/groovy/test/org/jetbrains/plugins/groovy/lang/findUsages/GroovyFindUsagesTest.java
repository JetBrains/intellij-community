// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.findUsages;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Query;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyFindUsagesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "findUsages/" + codeInsightFixture.get().getTestName(true) + "/");
  }

  private void doConstructorTest(String filePath, int expectedCount) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(filePath);
    ReadAction.runBlocking(() -> {
      final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      final PsiMethod method = PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
      assertNotNull(method);
      assertTrue(method.isConstructor());
      final Query<PsiReference> query = ReferencesSearch.search(method);

      assertEquals(expectedCount, ReadAction.computeBlocking(()->query.findAll()).size());
    });
  }

  @Test
  public void testDerivedClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("p/B.java", "A.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement elementAt = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      final PsiClass clazz = PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
      assertNotNull(clazz);

      final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
      final Query<PsiClass> query = DirectClassInheritorsSearch.search(clazz, projectScope);

      assertEquals(1, ReadAction.computeBlocking(()->query.findAll()).size());
    });
  }

  @Test
  public void testConstructor1() {
    doConstructorTest("A.groovy", 2);
  }

  @Test
  public void testConstructorUsageInNewExpression() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("ConstructorUsageInNewExpression.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved =
        TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getReferenceSearchFlags());
      assertNotNull(resolved, "Could not resolve reference");
      final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
      assertEquals(2, ReadAction.computeBlocking(()->MethodReferencesSearch.search((PsiMethod)resolved, projectScope, true).findAll()).size());
      assertEquals(4, ReadAction.computeBlocking(()->MethodReferencesSearch.search((PsiMethod)resolved, projectScope, false).findAll()).size());
    });
  }

  @Test
  public void testGotoConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("GotoConstructor.groovy");
    ReadAction.runBlocking(() -> {
      final PsiElement target =
        TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getReferenceSearchFlags());
      assertNotNull(target);
      assertInstanceOf(PsiMethod.class, target);
      assertTrue(((PsiMethod)target).isConstructor());
      assertEquals(0, ((PsiMethod)target).getParameterList().getParametersCount());
    });
  }

  @Test
  public void testSetter1() {
    doTestImpl("A.groovy", 2);
  }

  @Test
  public void testGetter1() {
    doTestImpl("A.groovy", 1);
  }

  @Test
  public void testProperty1() {
    doTestImpl("A.groovy", 2);
  }

  @Test
  public void testProperty2() {
    doTestImpl("A.groovy", 1);
  }

  @Test
  public void testEscapedReference() {
    doTestImpl("A.groovy", 1);
  }

  @Test
  public void testKeywordPropertyName() {
    doTestImpl("A.groovy", 1);
  }

  @Test
  public void testTypeAlias() {
    doTestImpl("A.groovy", 2);
  }

  @Test
  public void testMethodAlias() {
    doTestImpl("A.groovy", 2);
  }

  @Test
  public void testAliasImportedProperty() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Abc.groovy", "class Abc {static def foo}");
    doTestImpl("A.groovy", 5);
  }

  @Test
  public void testGetterWhenAliasedImportedProperty() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Abc.groovy", "class Abc {static def foo}");
    doTestImpl("A.groovy", 5);
  }

  @Test
  public void testForInParameter() {
    doTestImpl("A.groovy", 1);
  }

  @Test
  public void testSyntheticParameter() {
    doTestImpl("A.groovy", 1);
  }

  @Test
  public void testOverridingMethodUsage() {
    doTestImpl("OverridingMethodUsage.groovy", 2);
  }

  @Test
  public void testDynamicUsages() {
    doTestImpl("DynamicUsages.groovy", 2);
  }

  @Test
  public void testDynamicCallExpressionUsages() {
    doTestImpl("DynamicCallExpressionUsages.groovy", 2);
  }

  @Test
  public void testAnnotatedMemberSearch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();

    final PsiReference ref = myFixture.getReferenceAtCaretPosition("A.groovy");
    assertNotNull(ref, "Did not find reference");
    ReadAction.runBlocking(() -> {
      final PsiElement resolved = ref.resolve();
      assertNotNull(resolved, "Could not resolve reference");

      final Query<PsiReference> query;
      final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myFixture.getProject());
      if (resolved instanceof PsiMethod) {
        query = MethodReferencesSearch.search((PsiMethod)resolved, projectScope, true);
      }
      else {
        query = ReferencesSearch.search(resolved, projectScope);
      }

      assertEquals(1, ReadAction.computeBlocking(()->query.findAll()).size());
    });
  }

  private void doTestImpl(String filePath, int expectedUsagesCount) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(filePath);
    ReadAction.runBlocking(() -> {
      assertUsageCount(expectedUsagesCount);
    });
  }

  private void assertUsageCount(int expectedUsagesCount) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiElement resolved =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getReferenceSearchFlags());
    assertNotNull(resolved, "Could not resolve reference");
    doFind(expectedUsagesCount, resolved);
  }

  private void doFind(int expectedUsagesCount, PsiElement resolved) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(myFixture.getProject())).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(resolved, false);
    assertNotNull(handler);
    final FindUsagesOptions options = handler.getFindUsagesOptions();
    final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<>();
    for (PsiElement element : handler.getPrimaryElements()) {
      handler.processElementUsages(element, processor, options);
    }

    for (PsiElement element : handler.getSecondaryElements()) {
      handler.processElementUsages(element, processor, options);
    }

    assertEquals(expectedUsagesCount, processor.getResults().size());
  }

  @Test
  public void testGdkMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", "[''].ea<caret>ch {}");
    ReadAction.runBlocking(() -> {
      assertUsageCount(1);
    });
  }

  @Test
  public void testGDKSuperMethodSearch() {
    doSuperMethodTest("T");
  }

  @Test
  public void testGDKSuperMethodForMapSearch() {
    doSuperMethodTest("Map");
  }

  @Test
  public void testLabels() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    final GroovyFile file = (GroovyFile)myFixture.getFile();
    assertEquals(2, ReadAction.computeBlocking(()->ReferencesSearch.search(file.getTopStatements()[0]).findAll()).size());
  }

  @Test
  public void testConstructorUsageInAnonymousClass() {
    doTestImpl("A.groovy", 1);
  }

  @Test
  public void testCapitalizedProperty1() {
    doTest(1, """
      class A {
        def Pro<caret>p
      }
      
      new A().Prop""");
  }

  @Test
  public void testCapitalizedProperty2() {
    doTest(1, """
      class A {
        def Pro<caret>p
      }
      
      new A().prop""");
  }

  @Test
  public void testCapitalizedProperty3() {
    doTest(0, """
      class A {
        def pro<caret>p
      }
      
      new A().Prop""");
  }

  @Test
  public void testCapitalizedProperty4() {
    doTest(1, """
      class A {
        def p<caret>rop
      }
      
      new A().prop""");
  }

  @Test
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

  @Test
  public void test_do_not_report_dynamic_usages_when_argument_count_differs() {
    doTest(0, """
      class A {
          static void start<caret>sWith(String s, String s1, boolean b) {}
          def c = { it.startsWith("aaa") }
      }
      """);
  }

  @Test
  public void test_literal_names() {
    doTest(1, """
      def 'f<caret>oo'() {}
      
      'foo'()
      """);
  }

  @Test
  public void test_literal_name_with_escaping() {
    doTest(1, """
      def 'f<caret>oo \\''() {}
      
      'foo \\''()
      """);
  }

  @Test
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

  @Test
  public void testResolveBinding1() {
    doTest(2, """
      abc = 4
      
      print ab<caret>c
      """);
  }

  @Test
  public void testResolveBinding3() {
    doTest(2, """
      a<caret>bc = 4
      
      print abc
      """);
  }

  @Test
  public void testResolveBinding4() {
    doTest(1, """
      print abc
      
      a<caret>bc = 4
      """);
  }

  @Test
  public void testBinding9() {
    doTest(4, """
      a<caret>a = 5
      print aa
      aa = 6
      print aa
      """);
  }

  @Test
  public void testBinding10() {
    doTest(4, """
      aa = 5
      print a<caret>a
      aa = 6
      print aa
      """);
  }

  @Test
  public void testBinding11() {
    doTest(4, """
      aa = 5
      print aa
      a<caret>a = 6
      print aa
      """);
  }

  @Test
  public void testBinding12() {
    doTest(4, """
      aa = 5
      print aa
      aa = 6
      print a<caret>a
      """);
  }

  @Test
  public void testBinding13() {
    doTest(2, """
      aaa = 1
      
      def foo() {
        aa<caret>a
      }
      """);
  }

  @Test
  public void testBinding14() {
    doTest(2, """
      aa<caret>a = 1
      
      def foo() {
        aaa
      }
      """);
  }

  @Test
  public void testBinding15() {
    doTest(1, """
      def foo() {
        aaa
      }
      
      aa<caret>a = 1
      """);
  }

  @Test
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

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    ReadAction.runBlocking(() -> {
      final GroovyFile file = (GroovyFile)myFixture.getFile();
      final GrTypeDefinition psiClass = (GrTypeDefinition)file.getClasses()[0];
      final GrMethod method = (GrMethod)psiClass.getMethods()[0];
      final Collection<MethodSignatureBackedByPsiMethod> superMethods =
        ReadAction.computeBlocking(() -> SuperMethodsSearch.search(method, null, true, true).findAll());
      assertEquals(firstParameterTypes.length, superMethods.size());

      final Iterator<MethodSignatureBackedByPsiMethod> iterator = superMethods.iterator();
      for (String firstParameterType : firstParameterTypes) {
        final MethodSignatureBackedByPsiMethod methodSignature = iterator.next();
        final PsiMethod superMethod = methodSignature.getMethod();
        final String className = superMethod.getContainingClass().getName();
        assertEquals("DefaultGroovyMethods", className);
        final String actualParameterType = ((PsiClassType)methodSignature.getParameterTypes()[0]).resolve().getName();
        assertEquals(firstParameterType, actualParameterType);
      }
    });
  }

  private void doTest(int usageCount, String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", text);
    ReadAction.runBlocking(() -> {
      assertUsageCount(usageCount);
    });
  }

  @Test
  public void testWholeWordsIndexIsBuiltForLiterals() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", "11");
    ReadAction.runBlocking(() -> {
      PsiFile[] words = PsiSearchHelper.getInstance(myFixture.getProject()).findFilesWithPlainTextWords("11");
      assertEquals(1, words.length);
    });
  }

  @Test
  public void test_find_usages_for_path_of_a_package() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("aaa/bbb/Foo.groovy", "package aaa.bbb \n class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "import aaa.bbb.Foo");
    ReadAction.runBlocking(() -> {
      PsiElement packageStatement = file.getChildren()[0].getChildren()[1].getReference().resolve();// package 'aaa.bbb'
      assertEquals(2, ReadAction.computeBlocking(()->ReferencesSearch.search(packageStatement).findAll()).size());
    });
  }
}
