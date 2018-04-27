// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testIntegration.TestFramework;

public class JUnitDetectionTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit; public @interface Test {}");
    myFixture.addClass("package org.junit.runners; public class Parameterized { public @interface Parameters {} public @interface Parameter {}}");
    myFixture.addClass("package org.junit.runner; public @interface RunWith {Class value();}");
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
    assertTrue(framework != null);
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
}
