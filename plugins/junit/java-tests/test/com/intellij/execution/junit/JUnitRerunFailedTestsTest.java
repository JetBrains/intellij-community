// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.TestListenerProtocol;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("JUnitTestClassNamingConvention")
@TestApplication
public class JUnitRerunFailedTestsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().addClass("package junit.framework; public class TestCase {}");
  }

  @Test
  public void testIncludeMethodsToRerunFromChildClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("abstract class ATest extends junit.framework.TestCase {" +
                       "  public void testMe() {}\n" +
                       "}");
    myFixture.addClass("public class ChildTest extends ATest {}");

    ReadAction.runBlocking(() -> {
      final SMTestProxy testProxy = new SMTestProxy("testMe", false, "java:test://ChildTest/testMe");
      final Project project = myFixture.getProject();
      final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
      testProxy.setLocator(JavaTestLocator.INSTANCE);

      final Location location = testProxy.getLocation(project, searchScope);
      assertNotNull(location);
      assertInstanceOf(MethodLocation.class, location);

      //navigation to the method in abstract super class
      final PsiElement element = location.getPsiElement();
      assertInstanceOf(PsiMethod.class, element);
      final PsiMethod method = (PsiMethod)element;
      assertEquals("testMe", method.getName());
      final PsiClass containingClass = method.getContainingClass();
      assertNotNull(containingClass);
      assertEquals("ATest", containingClass.getQualifiedName());

      //include method "from" child class to rerun
      final String presentation = TestMethods.getTestPresentation(testProxy, project, searchScope);
      assertEquals("ChildTest,testMe", presentation);
    });
  }

  @Test
  public void testParameterizedTestNavigation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package org.junit.runner;
                         public @interface RunWith {
                             Class<? extends Runner> value();
                         }""");
    myFixture.addClass("package org.junit.runners; public class Parameterized {}");

    final PsiClass testClass = myFixture.addClass("""
                                                    import org.junit.Test;
                                                    import org.junit.runner.RunWith;
                                                    import org.junit.runners.Parameterized;
                                                    @RunWith(Parameterized.class)
                                                    public class MyTest {
                                                        @Test
                                                        public void testName1() {}
                                                    }""");

    ReadAction.runBlocking(() -> {
      final Project project = myFixture.getProject();
      final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
      final JavaTestLocator locationProvider = JavaTestLocator.INSTANCE;

      final SMTestProxy rootProxy = new SMTestProxy("MyTest", true, "java:suite://MyTest");
      rootProxy.setLocator(locationProvider);

      final SMTestProxy proxyParam = new SMTestProxy("[0.java]", true, "java:suite://MyTest.[0.java]");
      proxyParam.setLocator(locationProvider);

      final SMTestProxy parameterizedTestProxy = new SMTestProxy("testName1[0.java]", false, "java:test://MyTest/testName1[0.java]");
      parameterizedTestProxy.setLocator(locationProvider);

      final Location rootLocation = rootProxy.getLocation(project, searchScope);
      assertNotNull(rootLocation);
      assertEquals(testClass, rootLocation.getPsiElement());

      final Location proxyParamLocation = proxyParam.getLocation(project, searchScope);
      assertNotNull(proxyParamLocation);
      assertInstanceOf(PsiMemberParameterizedLocation.class, proxyParamLocation);
      assertEquals("[0.java]", ((PsiMemberParameterizedLocation)proxyParamLocation).getParamSetName());
      assertEquals(testClass, proxyParamLocation.getPsiElement());

      final Location parameterizedTestProxyLocation = parameterizedTestProxy.getLocation(project, searchScope);
      assertNotNull(parameterizedTestProxyLocation);
      assertInstanceOf(PsiMemberParameterizedLocation.class, parameterizedTestProxyLocation);
      assertEquals("[0.java]", ((PsiMemberParameterizedLocation)parameterizedTestProxyLocation).getParamSetName());
      assertEquals(testClass.getMethods()[0], parameterizedTestProxyLocation.getPsiElement());
      assertEquals(testClass, ((PsiMemberParameterizedLocation)parameterizedTestProxyLocation).getContainingClass());
      assertEquals("MyTest,testName1[0.java]", TestMethods.getTestPresentation(parameterizedTestProxy, project, searchScope));
    });
  }

  @Test
  public void testIgnoreRenamedMethodInRerunFailed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass baseClass = myFixture.addClass("abstract class ATest extends junit.framework.TestCase {" +
                                                 "  public void testMe() {}\n" +
                                                 "}");
    myFixture.addClass("public class ChildTest extends ATest {}");

    final SMTestProxy testProxy = new SMTestProxy("testMe", false, "java:test://ChildTest/testMe");
    final Project project = myFixture.getProject();
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    testProxy.setLocator(JavaTestLocator.INSTANCE);
    ReadAction.runBlocking(() -> {
      final String presentation = TestMethods.getTestPresentation(testProxy, project, searchScope);
      assertEquals("ChildTest,testMe", presentation);
    });
    WriteCommandAction.runWriteCommandAction(project, () -> {
      baseClass.getMethods()[0].setName("testName2");
    });
    ReadAction.runBlocking(() -> {
      assertNull(TestMethods.getTestPresentation(testProxy, project, searchScope));
    });
  }

  @Test
  public void testInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         public class TestClass {
                             public static class Tests extends junit.framework.TestCase {
                                 public void testFoo() throws Exception {}
                             }
                         }""");

    ReadAction.runBlocking(() -> {
      final SMTestProxy testProxy = new SMTestProxy("testFoo", false, "java:test://TestClass$Tests/testFoo");
      final Project project = myFixture.getProject();
      final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
      testProxy.setLocator(JavaTestLocator.INSTANCE);
      Location location = testProxy.getLocation(project, searchScope);
      assertNotNull(location);
      PsiElement element = location.getPsiElement();
      assertTrue(element instanceof PsiMethod);
      String name = ((PsiMethod)element).getName();
      assertEquals("testFoo", name);
    });
  }

  @Test
  public void testLocatorForIgnoredClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("""
                                           @org.junit.Ignorepublic class TestClass {
                                               @org.junit.Test    public void testFoo() throws Exception {}
                                           }""");
    ReadAction.runBlocking(() -> {
      final SMTestProxy testProxy = new SMTestProxy("TestClass", false, "java:test://TestClass/TestClass");
      final Project project = myFixture.getProject();
      final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
      testProxy.setLocator(JavaTestLocator.INSTANCE);
      Location location = testProxy.getLocation(project, searchScope);
      assertNotNull(location);
      PsiElement element = location.getPsiElement();
      assertEquals(aClass, element);
    });
  }

  @Test
  public void testRerunWholeClassOnClassConfigurationFailure() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         public class MyTest extends junit.framework.TestCase {
                             public void testFoo() {}
                         }""");
    ReadAction.runBlocking(() -> {
      final SMTestProxy testProxy = new SMTestProxy(TestListenerProtocol.CLASS_CONFIGURATION, false, "java:suite://MyTest");
      final Project project = myFixture.getProject();
      final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
      testProxy.setLocator(JavaTestLocator.INSTANCE);

      final Location<?> location = testProxy.getLocation(project, searchScope);
      assertNotNull(location);
      assertInstanceOf(PsiClass.class, location.getPsiElement());

      //a class-level configuration failure reruns the whole class: entry has no comma
      assertEquals("MyTest", TestMethods.getTestPresentation(testProxy, project, searchScope));
    });
  }

  @Test
  public void testRerunWholeNestedClassOnClassConfigurationFailure() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         public class Outer {
                             public static class Nested extends junit.framework.TestCase {
                                 public void testFoo() {}
                             }
                         }""");
    ReadAction.runBlocking(() -> {
      final SMTestProxy testProxy = new SMTestProxy(TestListenerProtocol.CLASS_CONFIGURATION, false, "java:suite://Outer$Nested");
      final Project project = myFixture.getProject();
      final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
      testProxy.setLocator(JavaTestLocator.INSTANCE);

      assertEquals("Outer$Nested", TestMethods.getTestPresentation(testProxy, project, searchScope));
    });
  }

  @Test
  public void testClassConfigurationNameWithMethodLocationStillRerunsMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         public class MyTest extends junit.framework.TestCase {
                             public void testFoo() {}
                         }""");
    ReadAction.runBlocking(() -> {
      //a "Class Configuration" node whose location is a method (e.g. a @TestFactory container) must keep the method mapping
      final SMTestProxy testProxy = new SMTestProxy(TestListenerProtocol.CLASS_CONFIGURATION, false, "java:test://MyTest/testFoo");
      final Project project = myFixture.getProject();
      final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
      testProxy.setLocator(JavaTestLocator.INSTANCE);

      assertEquals("MyTest,testFoo", TestMethods.getTestPresentation(testProxy, project, searchScope));
    });
  }

  @Test
  public void testPresentationForJunit5MethodsWithParameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         class A {}public class TestClass {
                             @org.junit.platform.commons.annotation.Testable    public void testFoo(A a) throws Exception {}
                         }""");
    ReadAction.runBlocking(() -> {
      final SMTestProxy testProxy = new SMTestProxy("testFoo", false, "java:test://TestClass/testFoo");
      testProxy.setLocator(JavaTestLocator.INSTANCE);
      final String presentation = TestMethods.getTestPresentation(testProxy, myFixture.getProject(), GlobalSearchScope.projectScope(myFixture.getProject()));
      assertEquals("TestClass,testFoo(A)", presentation);
    });
  }

  @Test
  public void testMultipleClassesInOneFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", "public class Test1 {<caret>} public class Test2 {}");

    TestApplicationManager testApplication = TestApplicationManager.getInstance();
    try {
      testApplication.setDataProvider(new TestDataProvider(myFixture.getProject()) {
        @Override
        public Object getData(@NotNull @NonNls String dataId) {
          if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
            return new VirtualFile[] {myFixture.getFile().getVirtualFile()};
          }
          return super.getData(dataId);
        }
      });
      ReadAction.runBlocking(() -> {
        final PsiElement psiElement = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
        final PatternConfigurationProducer configurationProducer = RunConfigurationProducer.getInstance(PatternConfigurationProducer.class);
        assertFalse(configurationProducer.isMultipleElementsSelected(new ConfigurationContext(psiElement)));
      });
    }
    finally {
      testApplication.setDataProvider(null);
    }
  }
}