// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.PsiLocation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testIntegration.TestFramework;

public class JUnitDetectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit; public @interface Test {}");
    myFixture.addClass("package org.junit.runners; public class Parameterized { public @interface Parameters {} public @interface Parameter {}}");
    myFixture.addClass("package org.junit.runner; public @interface RunWith {Class<?> value();}");
  }

  public void testInnerClassesForKnownRunners() {
    PsiFile file = myFixture.configureByText("A.java", "import org.junit.Test;\n" +
                                                       "import org.junit.runner.RunWith;\n" +
                                                       "import org.junit.runners.Parameterized;\n" +
                                                       "import java.util.*;\n" +
                                                       "@RunWith(Parameterized.class)\n" +
                                                       "public class A {\n" +
                                                       "  @Parameterized.Parameters\n" +
                                                       "  public static Collection<Object[]> data() {\n" +
                                                       "    return Arrays.asList(new Object[] {\"11\"}, new Object[] {\"12\"});\n" +
                                                       "  }\n" +
                                                       "  @Parameterized.Parameter\n" +
                                                       "  public String myJUnitVersion;\n" +
                                                       "  @Test\n" +
                                                       "  public void ignoredTestMethod() throws Exception {}\n" +
                                                       "  public class Foo {\n" +
                                                       "    public void testMe() {}\n " +
                                                       "  }\n" +
                                                       "}\n");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework);
    assertTrue(framework.isTestClass(aClass));
    PsiClass innerClass = aClass.getInnerClasses()[0];
    assertFalse(framework.isTestClass(innerClass));
    assertFalse(framework.isTestMethod(innerClass.getMethods()[0]));
  }

  public void testKnownClassInheritor() {
    myFixture.addClass("package p; import org.junit.*; import org.junit.runners.*; public class Theories extends Parameterized {}");
    myFixture.addClass("package p; public @interface Theory {}"); //custom annotation
    PsiFile file = myFixture.configureByText("A.java", "import org.junit.Test;\n" +
                                                       "import org.junit.runner.RunWith;\n" +
                                                       "import p.Theories;\n" +
                                                       "import java.util.*;\n" +
                                                       "@RunWith(Theories.class)\n" +
                                                       "public class A {\n" +
                                                       "  @Theory\n" +
                                                       "  public void validTestMethod(int param) {}\n" +
                                                       "  public class Foo {\n" +
                                                       "    public void testMe() {}\n " +
                                                       "  }\n" +
                                                       "}\n");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework);
    assertTrue(framework.isTestClass(aClass));
    assertTrue(framework.isTestMethod(aClass.getMethods()[0]));
    PsiClass innerClass = aClass.getInnerClasses()[0];
    assertFalse(framework.isTestClass(innerClass));
    assertFalse(framework.isTestMethod(innerClass.getMethods()[0]));
  }

  public void testCustomRunner() {
    myFixture.addClass("import org.junit.runner.Runner;" +
                       "public class CustomRunner extends Runner {}");
    PsiFile file = myFixture.configureByText("CustomRunnerTest.java",
                                             "import org.junit.rules.ExternalResource;\n" +
                                             "import org.junit.runner.RunWith;\n" +
                                             "@RunWith(CustomRunner.class)\n" +
                                             "public class CustomRunnerTest {\n" +
                                             "@org.junit.Rule\n" +
                                             "public ExternalResource noTest() {return null;}\n" +
                                             "public void foo() {}}\n");

    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework);
    assertTrue(framework.isTestClass(aClass));
    assertFalse(framework.isTestMethod(aClass.getMethods()[0]));
    assertTrue(framework.isTestMethod(aClass.getMethods()[1]));
  }

  public void testMockitoRunner() {
    myFixture.addClass("package org.mockito.junit; import org.junit.runner.Runner; import org.junit.runner.manipulation.Filterable; " +
                                            "public class MockitoJUnitRunner extends Runner implements Filterable {}");
    PsiFile file = myFixture.configureByText("MockitoRunnerTest.java",
                                                        "import org.junit.rules.ExternalResource;\n" +
                                                        "import org.mockito.junit.MockitoJUnitRunner;\n" +
                                                        "import org.junit.runner.RunWith;\n" +
                                                        "@RunWith(MockitoJUnitRunner.class)\n" +
                                                        "public class MockitoRunnerTest {\n" +
                                                        "  @org.junit.Rule\n" +
                                                        "  public ExternalResource noTest() {return null;}\n" +
                                                        "  public void foo() {}}\n");

    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework);
    assertTrue(framework.isTestClass(aClass));
    assertFalse(framework.isTestMethod(aClass.getMethods()[0]));
    assertFalse(framework.isTestMethod(aClass.getMethods()[1]));
  }

  public void testJqwikProperty() {
    myFixture.addClass("package net.jqwik.api;" +
                       "@org.junit.platform.commons.annotation.Testable public @interface Property {}");
    myFixture.addClass("package org.junit.platform.commons.annotation;" +
                       "public @interface Testable {}");
    myFixture.addClass("package net.jqwik.api; public @interface ForAll {}");
    PsiFile file = myFixture.configureByText("JqwikPropertyTest.java",
                                             "import net.jqwik.api.ForAll;\n" +
                                             "import net.jqwik.api.Property;\n" +
                                             "class JqwikPropertyTests {\n" +
                                             "\t@Property\n" +
                                             "\tvoid aProperty(@ForAll int anInt) {}\n" +
                                             "}"
    );

    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    assertTrue(JUnitUtil.isTestClass(aClass));
    PsiMethod psiMethod = aClass.getMethods()[0];
    assertTrue(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(psiMethod), false));
  }

  public void testableMethodTest() {
        myFixture.addClass("package org.junit.jupiter.api;" +
                       "@org.junit.platform.commons.annotation.Testable public @interface Test {}");
        PsiFile file = myFixture.configureByText("TestableClassTest.java",
                                             "class TestableClassTest {\n" +
                                             "\t@org.junit.jupiter.api.Test\n" +
                                             "\tvoid foo() {}\n" +
                                             "\tvoid foo1() {}\n" +
                                             "}");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    assertTrue(JUnitUtil.isTestClass(aClass));
    assertTrue(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(aClass.getMethods()[0])));
    assertFalse(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(aClass.getMethods()[1])));
  }
  
  public void testableClassTest() {
     myFixture.addClass("package org.junit.jupiter.api;" +
                       "@org.junit.platform.commons.annotation.Testable public @interface Test {}");
       
    PsiFile file = myFixture.configureByText("TestableClassTest.java",
                                             "@org.junit.platform.commons.annotation.Testable " +
                                             "class TestableClassTest {\n" +
                                             "\tvoid foo() {}\n" +
                                             "}");
    PsiClass aClass = ((PsiClassOwner)file).getClasses()[0];
    assertTrue(JUnitUtil.isTestClass(aClass));
    assertTrue(JUnitUtil.isTestMethod(PsiLocation.fromPsiElement(aClass.getMethods()[0])));
  }
}
