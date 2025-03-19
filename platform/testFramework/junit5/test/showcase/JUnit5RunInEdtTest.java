// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase;

import com.intellij.ide.IdeEventQueue;
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

  @Nested
  final class UnannotatedTest {

    @BeforeAll
    static void beforeAll() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
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
  @Nested
  final class ExtensionRegisteredTest {

    @BeforeAll
    static void beforeAll() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
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
  @Nested
  final class MethodLevelAnnotationTest {

    @RunMethodInEdt
    @BeforeAll
    static void beforeAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
    @RunMethodInEdt
    MethodLevelAnnotationTest() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt
    @BeforeEach
    void beforeEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt
    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
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
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt
    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertTrue(EDT.isCurrentThreadEdt());
        Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
      }));
    }

    @RunMethodInEdt
    @AfterEach
    void afterEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt
    @AfterAll
    static void afterAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }
  }

  @RunInEdt(allMethods = false, writeIntent = true) // required to make the extension able to handle lifecycle methods
  @Nested
  final class MethodLevelAnnotationTestWithDefaultWriteIntent {

    @RunMethodInEdt
    @BeforeAll
    static void beforeAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
    @RunMethodInEdt
    MethodLevelAnnotationTestWithDefaultWriteIntent() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt
    @BeforeEach
    void beforeEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt
    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
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
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt
    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertTrue(EDT.isCurrentThreadEdt());
        Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
      }));
    }

    @RunMethodInEdt
    @AfterEach
    void afterEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt
    @AfterAll
    static void afterAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }
  }

  @RunInEdt(allMethods = false) // required to make the extension able to handle lifecycle methods
  @Nested
  final class MethodLevelAnnotationTestWithPerMethodWriteIntent {

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
    @BeforeAll
    static void beforeAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
    MethodLevelAnnotationTestWithPerMethodWriteIntent() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
    @BeforeEach
    void beforeEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @Test
    void testMethodUnannotated() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
    @TestTemplate
    @ExtendWith(EmptyTestTemplateInvocationContextProvider.class)
    void testTemplate() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertTrue(EDT.isCurrentThreadEdt());
        Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
      }));
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
    @AfterEach
    void afterEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.True)
    @AfterAll
    static void afterAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }
  }


  @RunInEdt(allMethods = false, writeIntent = true) // required to make the extension able to handle lifecycle methods
  @Nested
  final class MethodLevelAnnotationTestWithoutPerMethodWriteIntent {

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.False)
    @BeforeAll
    static void beforeAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.False)
    MethodLevelAnnotationTestWithoutPerMethodWriteIntent() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.False)
    @BeforeEach
    void beforeEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.False)
    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @Test
    void testMethodUnannotated() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.False)
    @TestTemplate
    @ExtendWith(EmptyTestTemplateInvocationContextProvider.class)
    void testTemplate() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.False)
    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertTrue(EDT.isCurrentThreadEdt());
        Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
      }));
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.False)
    @AfterEach
    void afterEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @RunMethodInEdt(writeIntent = RunMethodInEdt.WriteIntentMode.False)
    @AfterAll
    static void afterAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }
  }

  @RunInEdt
  @Nested
  final class ClassLevelAnnotationTest {

    @BeforeAll
    static void beforeAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
    ClassLevelAnnotationTest() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @BeforeEach
    void beforeEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @TestTemplate
    @ExtendWith(EmptyTestTemplateInvocationContextProvider.class)
    void testTemplate() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertTrue(EDT.isCurrentThreadEdt());
        Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
      }));
    }

    @AfterEach
    void afterEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @AfterAll
    static void afterAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }
  }

  @RunInEdt(writeIntent = true)
  @Nested
  final class ClassLevelAnnotationTestWithGlobalWriteIntent {

    @BeforeAll
    static void beforeAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
    ClassLevelAnnotationTestWithGlobalWriteIntent() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @BeforeEach
    void beforeEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @TestTemplate
    @ExtendWith(EmptyTestTemplateInvocationContextProvider.class)
    void testTemplate() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @TestFactory
    List<DynamicTest> testFactory() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      return List.of(DynamicTest.dynamicTest("dynamic test", () -> {
        Assertions.assertTrue(EDT.isCurrentThreadEdt());
        Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
      }));
    }

    @AfterEach
    void afterEach() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }

    @AfterAll
    static void afterAll() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertTrue(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
    }
  }

  @RunInEdt
  static abstract class AnnotatedClass {
  }

  @Nested
  final class InheritorOfAnnotatedClassTest extends AnnotatedClass {

    @Test
    void testMethod() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt());
      Assertions.assertFalse(IdeEventQueue.getInstance().getThreadingSupport().isWriteIntentLocked());
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
