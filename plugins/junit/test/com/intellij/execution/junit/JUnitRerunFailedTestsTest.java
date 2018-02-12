/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.junit;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

@SuppressWarnings({"JUnitTestClassNamingConvention", "JUnitTestCaseWithNoTests"})
public class JUnitRerunFailedTestsTest extends LightCodeInsightFixtureTestCase  {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package junit.framework; public class TestCase {}");
  }

  public void testIncludeMethodsToRerunFromChildClass() {
    myFixture.addClass("abstract class ATest extends junit.framework.TestCase {" +
                       "  public void testMe() {}\n" +
                       "}");
    myFixture.addClass("public class ChildTest extends ATest {}");

    final SMTestProxy testProxy = new SMTestProxy("testMe", false, "java:test://ChildTest.testMe");
    final Project project = getProject();
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    testProxy.setLocator(JavaTestLocator.INSTANCE);

    final Location location = testProxy.getLocation(project, searchScope);
    assertNotNull(location);
    assertInstanceOf(location, MethodLocation.class);

    //navigation to the method in abstract super class
    final PsiElement element = location.getPsiElement();
    assertInstanceOf(element, PsiMethod.class);
    final PsiMethod method = (PsiMethod)element;
    assertEquals("testMe", method.getName());
    final PsiClass containingClass = method.getContainingClass();
    assertNotNull(containingClass);
    assertEquals("ATest", containingClass.getQualifiedName());

    //include method "from" child class to rerun
    final String presentation = TestMethods.getTestPresentation(testProxy, project, searchScope);
    assertEquals("ChildTest,testMe", presentation);
  }

  public void testParameterizedTestNavigation() {
    myFixture.addClass("package org.junit.runner;\n" +
                       "public @interface RunWith {\n" +
                       "    Class<? extends Runner> value();\n" +
                       "}");
    myFixture.addClass("package org.junit.runners; public class Parameterized {}");

    final PsiClass testClass = myFixture.addClass("import org.junit.Test;\n" +
                                                  "import org.junit.runner.RunWith;\n" +
                                                  "import org.junit.runners.Parameterized;\n" +
                                                  "@RunWith(Parameterized.class)\n" +
                                                  "public class MyTest {\n" +
                                                  "    @Test\n" +
                                                  "    public void testName1() throws Exception {}\n" +
                                                  "}");

    final Project project = getProject();
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    final JavaTestLocator locationProvider = JavaTestLocator.INSTANCE;

    final SMTestProxy rootProxy = new SMTestProxy("MyTest", true, "java:suite://MyTest");
    rootProxy.setLocator(locationProvider);

    final SMTestProxy proxyParam = new SMTestProxy("[0.java]", true, "java:suite://MyTest.[0.java]");
    proxyParam.setLocator(locationProvider);

    final SMTestProxy parameterizedTestProxy = new SMTestProxy("testName1[0.java]", false, "java:test://MyTest.testName1[0.java]");
    parameterizedTestProxy.setLocator(locationProvider);

    final Location rootLocation = rootProxy.getLocation(project, searchScope);
    assertNotNull(rootLocation);
    assertEquals(testClass, rootLocation.getPsiElement());

    final Location proxyParamLocation = proxyParam.getLocation(project, searchScope);
    assertNotNull(proxyParamLocation);
    assertInstanceOf(proxyParamLocation, PsiMemberParameterizedLocation.class);
    assertEquals("[0.java]", ((PsiMemberParameterizedLocation)proxyParamLocation).getParamSetName());
    assertEquals(testClass, proxyParamLocation.getPsiElement());

    final Location parameterizedTestProxyLocation = parameterizedTestProxy.getLocation(project, searchScope);
    assertNotNull(parameterizedTestProxyLocation);
    assertInstanceOf(parameterizedTestProxyLocation, PsiMemberParameterizedLocation.class);
    assertEquals("[0.java]", ((PsiMemberParameterizedLocation)parameterizedTestProxyLocation).getParamSetName());
    assertEquals(testClass.getMethods()[0], parameterizedTestProxyLocation.getPsiElement());
    assertEquals(testClass, ((PsiMemberParameterizedLocation)parameterizedTestProxyLocation).getContainingClass());
    assertEquals("MyTest,testName1[0.java]", TestMethods.getTestPresentation(parameterizedTestProxy, project, searchScope));
  }

  public void testIgnoreRenamedMethodInRerunFailed() {
    final PsiClass baseClass = myFixture.addClass("abstract class ATest extends junit.framework.TestCase {" +
                                                 "  public void testMe() {}\n" +
                                                 "}");
    myFixture.addClass("public class ChildTest extends ATest {}");

    final SMTestProxy testProxy = new SMTestProxy("testMe", false, "java:test://ChildTest.testMe");
    final Project project = getProject();
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    testProxy.setLocator(JavaTestLocator.INSTANCE);
    final String presentation = TestMethods.getTestPresentation(testProxy, project, searchScope);
    assertEquals("ChildTest,testMe", presentation);
    WriteCommandAction.runWriteCommandAction(project, () -> {
      baseClass.getMethods()[0].setName("testName2");
    });
    assertNull(TestMethods.getTestPresentation(testProxy, project, searchScope));
  }

  public void testInnerClass() {
    myFixture.addClass("public class TestClass {\n" +
                       "    public static class Tests extends junit.framework.TestCase {\n" +
                       "        public void testFoo() throws Exception {}\n" +
                       "    }\n" +
                       "}");

    final SMTestProxy testProxy = new SMTestProxy("testFoo", false, "java:test://TestClass$Tests.testFoo");
    final Project project = getProject();
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    testProxy.setLocator(JavaTestLocator.INSTANCE);
    Location location = testProxy.getLocation(project, searchScope);
    assertNotNull(location);
    PsiElement element = location.getPsiElement();
    assertTrue(element instanceof PsiMethod);
    String name = ((PsiMethod)element).getName();
    assertEquals("testFoo", name);
  }

  public void testLocatorForIgnoredClass() {
    PsiClass aClass = myFixture.addClass("@org.junit.Ignore" +
                                         "public class TestClass {\n" +
                                         "    @org.junit.Test" +
                                         "    public void testFoo() throws Exception {}\n" +
                                         "}");
    final SMTestProxy testProxy = new SMTestProxy("TestClass", false, "java:test://TestClass.TestClass");
    final Project project = getProject();
    final GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
    testProxy.setLocator(JavaTestLocator.INSTANCE);
    Location location = testProxy.getLocation(project, searchScope);
    assertNotNull(location);
    PsiElement element = location.getPsiElement();
    assertEquals(aClass, element);
  }

  public void testPresentationForJunit5MethodsWithParameters() {
    myFixture.addClass("class A {}" +
                       "public class TestClass {\n" +
                       "    @org.junit.platform.commons.annotation.Testable" +
                       "    public void testFoo(A a) throws Exception {}\n" +
                       "}");
    final SMTestProxy testProxy = new SMTestProxy("testFoo", false, "java:test://TestClass.testFoo");
    testProxy.setLocator(JavaTestLocator.INSTANCE);
    final String presentation = TestMethods.getTestPresentation(testProxy, myFixture.getProject(), GlobalSearchScope.projectScope(myFixture.getProject()));
    assertEquals("TestClass,testFoo(A)", presentation);
  }

  public void testMultipleClassesInOneFile() {
    myFixture.configureByText("a.java", "public class Test1 {<caret>} public class Test2 {}");

    final IdeaTestApplication testApplication = IdeaTestApplication.getInstance();
    try {
      testApplication.setDataProvider(new TestDataProvider(myFixture.getProject()) {
        @Override
        public Object getData(@NonNls String dataId) {
          if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
            return new VirtualFile[] {myFixture.getFile().getVirtualFile()};
          }
          return super.getData(dataId);
        }
      });
      final PsiElement psiElement = myFixture.getFile().findElementAt(getEditor().getCaretModel().getOffset());
      final PatternConfigurationProducer configurationProducer = RunConfigurationProducer.getInstance(PatternConfigurationProducer.class);
      assertFalse(configurationProducer.isMultipleElementsSelected(new ConfigurationContext(psiElement)));
    }
    finally {
      testApplication.setDataProvider(null);
    }
  }
}