// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase;

import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.RunMethodInEdt;
import com.intellij.util.ui.EDT;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import java.util.List;
import java.util.stream.Stream;

final class JUnit5RunInEdtTest {

  static final class UnannotatedTest {

    @BeforeAll
    static void beforeAll() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    UnannotatedTest() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @BeforeEach
    void beforeEach() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @Test
    void testMethod() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @TestTemplate
    @ExtendWith(EmptyTestTemplateInvocationContextProvider.class)
    void testTemplate() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertFalse(EDT.isCurrentThreadEdt());
      }));
    }

    @AfterEach
    void afterEach() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @AfterAll
    static void afterAll() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }
  }

  @RunInEdt(allMethods = false) // extension is registered, but methods are not annotated
  static final class ExtensionRegisteredTest {

    @BeforeAll
    static void beforeAll() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    ExtensionRegisteredTest() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @BeforeEach
    void beforeEach() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @Test
    void testMethod() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @TestTemplate
    @ExtendWith(EmptyTestTemplateInvocationContextProvider.class)
    void testTemplate() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertFalse(EDT.isCurrentThreadEdt());
      }));
    }

    @AfterEach
    void afterEach() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @AfterAll
    static void afterAll() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }
  }

  @RunInEdt(allMethods = false) // required to make the extension able to handle lifecycle methods
  static final class MethodLevelAnnotationTest {

    @RunMethodInEdt
    @BeforeAll
    static void beforeAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @RunMethodInEdt
    MethodLevelAnnotationTest() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @RunMethodInEdt
    @BeforeEach
    void beforeEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @RunMethodInEdt
    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @Test
    void testMethodUnannotated() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @RunMethodInEdt
    @TestTemplate
    @ExtendWith(EmptyTestTemplateInvocationContextProvider.class)
    void testTemplate() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @RunMethodInEdt
    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertTrue(EDT.isCurrentThreadEdt());
      }));
    }

    @RunMethodInEdt
    @AfterEach
    void afterEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @RunMethodInEdt
    @AfterAll
    static void afterAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }
  }

  @RunInEdt
  static final class ClassLevelAnnotationTest {

    @BeforeAll
    static void beforeAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    ClassLevelAnnotationTest() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @BeforeEach
    void beforeEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @TestTemplate
    @ExtendWith(EmptyTestTemplateInvocationContextProvider.class)
    void testTemplate() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertTrue(EDT.isCurrentThreadEdt());
      }));
    }

    @AfterEach
    void afterEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }

    @AfterAll
    static void afterAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }
  }

  @RunInEdt
  static abstract class AnnotatedClass {
  }

  static final class InheritorOfAnnotatedClassTest extends AnnotatedClass {

    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
    }
  }

  private static final class EmptyTestTemplateInvocationContextProvider implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
      return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
      return Stream.of(new TestTemplateInvocationContext() {
      });
    }
  }
}
