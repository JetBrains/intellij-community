// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.junit.JUnit4Framework;
import com.intellij.execution.junit.JUnit5Framework;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.testIntegration.TestFramework;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class JUnit5AcceptanceTest extends JUnit5CodeInsightTest {

  @Test
  void testFactoryMethods() {
    PsiClass aClass = myFixture.addClass(
      "/** @noinspection ALL*/ class MyTest {@org.junit.jupiter.api.TestFactory java.util.List<org.junit.jupiter.api.DynamicTest> tests() {return null;}}");
    PsiMethod factoryMethod = aClass.getMethods()[0];
    assertNotNull(factoryMethod);
    assertTrue(JUnitUtil.isTestAnnotated(factoryMethod));
  }

  @Test
  void testDefaultMethodInInterface() {
    PsiClass aClass = myFixture.addClass("interface MyTest {@org.junit.jupiter.api.Test default void method() {}}");
    assertTrue(JUnitUtil.isTestClass(aClass, false, false));
  }

  @Test
  void recognizedInnerClassesWithTestMethods() {
    PsiClass aClass = myFixture
      .addClass("import org.junit.jupiter.api.*; /** @noinspection ALL*/ class MyTest {@Nested class NTest { @Test void method() {}}}");
    assertTrue(JUnitUtil.isTestClass(aClass, false, false));
    PsiClass innerClass = aClass.getInnerClasses()[0];
    assertTrue(JUnitUtil.isTestClass(innerClass));
    assertTrue(JUnitUtil.isTestMethod(MethodLocation.elementInClass(innerClass.getMethods()[0], innerClass)));
  }

  @Test
  void rejectStaticMethods() {
    PsiClass aClass =
      myFixture.addClass("import org.junit.jupiter.api.*; /** @noinspection ALL*/ class MyTest { @Test static void method() {}}");
    assertFalse(JUnitUtil.isTestClass(aClass, false, false));
    assertFalse(JUnitUtil.isTestMethod(MethodLocation.elementInClass(aClass.getMethods()[0], aClass)));
  }

  @Test
  void rejectPrivateMethods() {
    PsiClass aClass =
      myFixture.addClass("import org.junit.jupiter.api.*; /** @noinspection ALL*/ class MyTest { @Test private void method() {}}");
    assertFalse(JUnitUtil.isTestClass(aClass, false, false));
    assertFalse(JUnitUtil.isTestMethod(MethodLocation.elementInClass(aClass.getMethods()[0], aClass)));
  }

  @Test
  void testFrameworkDetection() {
    PsiClass aClass = myFixture.addClass("/** @noinspection ALL*/ class MyTest {@org.junit.jupiter.api.Test void method() {}}");
    assertNotNull(aClass);
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertNotNull(framework, "No test framework detected");
    assertInstanceOf(JUnit5Framework.class, framework, framework.getName());
  }

  @Test
  void testFrameworkDetectionWithMixedJunit4Junit5() throws ExecutionException {
    myFixture.addClass("package org.junit; public @interface Test {}");
    PsiClass aClass = myFixture.addClass("/** @noinspection ALL*/ public class MyTest {@org.junit.jupiter.api.Test void method() {} @org.junit.Test void method2(){} }");
    assertNotNull(aClass);
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertInstanceOf(JUnit4Framework.class, framework, framework.getName());
    JUnitConfiguration configuration = new JUnitConfiguration("", myFixture.getProject());
    configuration.beClassConfiguration(aClass);
    JavaParameters parameters = configuration.getTestObject().createJavaParameters4Tests();
    assertTrue(parameters.getProgramParametersList().hasParameter("-junit5"));
  }

  @Test
  void testClassWithDisabledCondition() throws ExecutionException {
    myFixture.addClass("package org.junit.jupiter.api; public @interface Disabled {}");
    PsiClass aClass = myFixture.addClass(
      """
        /** @noinspection ALL*/
        @org.junit.jupiter.api.Disabled
        public class MyTest {@org.junit.jupiter.api.Test void method() {}}""");
    assertNotNull(aClass);
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertInstanceOf(JUnit5Framework.class, framework, framework.getName());
    JUnitConfiguration configuration = new JUnitConfiguration("", myFixture.getProject());
    configuration.beClassConfiguration(aClass);
    JavaParameters parameters = configuration.getTestObject().createJavaParameters4Tests();
    assertTrue(parameters.getVMParametersList().hasParameter("-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition"));
  }

  @Test
  void testMetaDisabledClass() throws ExecutionException {
    myFixture.addClass("""
                         package org.test.sample;
                         @org.junit.jupiter.api.Disabled
                         public @interface MetaDisabled {}""");
    PsiClass aClass = myFixture.addClass(
      """
        /** @noinspection ALL*/
        @org.test.sample.MetaDisabled
        public class MyTest {@org.junit.jupiter.api.Test void method() {}}""");
    assertNotNull(aClass);
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertInstanceOf(JUnit5Framework.class, framework, framework.getName());
    JUnitConfiguration configuration = new JUnitConfiguration("", myFixture.getProject());
    configuration.beClassConfiguration(aClass);
    JavaParameters parameters = configuration.getTestObject().createJavaParameters4Tests();
    assertTrue(parameters.getVMParametersList().hasParameter("-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition"));
  }

  @Test
  void testWithDisabledCondition() throws ExecutionException {
    PsiClass aClass = myFixture.addClass(
      """
        /** @noinspection ALL*/
        public class MyTest {
          @org.junit.jupiter.api.Disabled
          @org.junit.jupiter.api.Test void method() {}}""");
    assertNotNull(aClass);
    TestFramework framework = TestFrameworks.detectFramework(aClass);
    assertInstanceOf(JUnit5Framework.class, framework, framework.getName());
    JUnitConfiguration configuration = new JUnitConfiguration("", myFixture.getProject());
    @SuppressWarnings("OptionalGetWithoutIsPresent") PsiMethod method = Arrays.stream(aClass.getMethods()).findFirst().get();
    configuration.beMethodConfiguration(MethodLocation.elementInClass(method, aClass));
    JavaParameters parameters = configuration.getTestObject().createJavaParameters4Tests();
    assertTrue(parameters.getVMParametersList().hasParameter("-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition"));
  }

  @Test
  void methodPresentations() {
    myFixture.addClass("package a; public class TestInfo {}");
    PsiClass aClass = myFixture.addClass("/** @noinspection ALL*/ class MyTest<T extends a.TestInfo> {" +
                                         "  @org.junit.jupiter.api.Test void method() {}" +
                                         "  @org.junit.jupiter.api.Test void method(a.TestInfo info) {}" +
                                         "  @org.junit.jupiter.api.Test void method(T info) {}" +
                                         "  @org.junit.Test void method1() {}" +
                                         "  @org.junit.Test void method1(a.TestInfo info) {}" +
                                         "}");
    assertNotNull(aClass);

    Stream<String> expectedData = Arrays.stream(new String[]{"method", "method(a.TestInfo)", "method(a.TestInfo)", "method1", "method1"});
    StreamEx.of(aClass.getMethods())
      .zipWith(expectedData)
      .forEach(e -> assertEquals(e.getValue(), JUnitConfiguration.Data.getMethodPresentation(e.getKey())));
  }

  @Test
  void junit5LibraryAdjustments() {
    myFixture.configureByText("MyTest.java",
                              "class MyTest {@org.junit.jupiter.api.<error descr=\"Cannot resolve symbol 'BeforeEach'\">Before<caret>Each</error> void method() {}}");
    myFixture.testHighlighting(false, false, false);
    final Set<String> frameworks = myFixture.getAllQuickFixes().stream()
      .map(action -> action.getText())
      .filter(name -> name.startsWith("Add")).collect(Collectors.toSet());
    assertAll("Detected frameworks: " + frameworks,
              () -> assertTrue(frameworks.contains("Add 'JUnit5.8.1' to classpath")));

    myFixture.configureByText("MyTest.java",
                              "class MyTest {@<error descr=\"Cannot resolve symbol 'DisplayName'\">DisplayName</error> void method() {}}");
    myFixture.testHighlighting(false, false, false);

    Set<String> displayNameFrameworks = myFixture.getAllQuickFixes().stream()
      .map(action -> action.getText())
      .filter(name -> name.startsWith("Add")).collect(Collectors.toSet());
    assertAll("Detected frameworks: " + displayNameFrameworks,
              () -> assertTrue(displayNameFrameworks.contains("Add 'JUnit5.8.1' to classpath")));
  }

  @Test
  void metaAnnotations() {
    myFixture.addClass("""
                         package a;
                         import java.lang.annotation.Retention;
                         import java.lang.annotation.RetentionPolicy;
                         @Retention(RetentionPolicy.RUNTIME)
                         @org.junit.jupiter.api.Test
                         @interface MyTest {}""");
    PsiClass aClass = myFixture.addClass("""
                                           class ATest {
                                               @a.MyTest
                                               void foo() {}
                                           }
                                           """);
    assertTrue(JUnitUtil.isTestClass(aClass, false, false));
    assertTrue(JUnitUtil.isTestMethod(MethodLocation.elementInClass(aClass.getMethods()[0], aClass)));
  }

  @Test
  void customEngineOnly() {
    PsiClass customEngineTest = myFixture.addClass("import org.junit.platform.commons.annotation.Testable;" +
                                                   " /** @noinspection ALL*/ " +
                                                   "@Testable\n" +
                                                   "class MyTests{}");
    assertTrue(JUnitUtil.isTestClass(customEngineTest));

    PsiClass customEngineAnnotationOnSuper = myFixture.addClass("class MyCustomClass extends MyTests{}");
    assertTrue(JUnitUtil.isTestClass(customEngineAnnotationOnSuper));
  }
}
