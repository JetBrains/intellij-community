// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.threadingModelHelper;

import java.util.Arrays;

import static com.intellij.util.concurrency.ThreadingAssertions.MUST_EXECUTE_IN_EDT;
import static com.intellij.util.concurrency.ThreadingAssertions.MUST_NOT_EXECUTE_IN_EDT;

public class TMHInstrumenter1Test extends TMHInstrumenterTestBase {

  public TMHInstrumenter1Test() {
    super("dependencies1", false);
  }

  public void testSimple() throws Exception {
    doEdtTest();
  }

  public void testSecondMethod() throws Exception {
    doEdtTest();
  }

  public void testMethodHasOtherAnnotationBefore() throws Exception {
    doEdtTest();
  }

  public void testMethodHasOtherAnnotationAfter() throws Exception {
    doEdtTest();
  }

  public void testEmptyBody() throws Exception {
    doEdtTest();
  }

  public void testConstructor() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    testClass.aClass.getDeclaredConstructor().newInstance();
    assertThrows(Throwable.class, MUST_EXECUTE_IN_EDT,
                 () -> executeInBackground(() -> testClass.aClass.getDeclaredConstructor().newInstance()));
  }

  public void testDoNotInstrument() throws Exception {
    TestClass testClass = getNotInstrumentedTestClass();
    invokeMethod(testClass.aClass);
    executeInBackground(() -> invokeMethod(testClass.aClass));
  }

  public void testRequiresBackgroundThreadAssertion() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    executeInBackground(() -> invokeMethod(testClass.aClass));
    assertThrows(Throwable.class, MUST_NOT_EXECUTE_IN_EDT, () -> invokeMethod(testClass.aClass));
  }

  public void testRequiresReadLockAssertion() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertReadAccessAllowed"));
  }

  public void testRequiresWriteLockAssertion() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertWriteAccessAllowed"));
  }

  public void testRequiresReadLockAbsenceAssertion() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertReadAccessNotAllowed"));
  }

  public void testLineNumber() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertIsDispatchThread"));
    assertEquals(Arrays.asList(5, 8, 8), TMHTestUtil.getLineNumbers(testClass.classBytes));
  }

  public void testLineNumberWhenBodyHasTwoStatements() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertIsDispatchThread"));
    assertEquals(Arrays.asList(5, 8, 8, 9), TMHTestUtil.getLineNumbers(testClass.classBytes));
  }

  public void testLineNumberWhenEmptyBody() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertIsDispatchThread"));
    assertEquals(Arrays.asList(5, 7, 7), TMHTestUtil.getLineNumbers(testClass.classBytes));
  }

  public void testLineNumberWhenOtherMethodBefore() throws Exception {
    TestClass testClass = getInstrumentedTestClass();
    assertTrue(TMHTestUtil.containsMethodCall(testClass.classBytes, "assertIsDispatchThread"));
    assertEquals(Arrays.asList(5, 7, 12, 12), TMHTestUtil.getLineNumbers(testClass.classBytes));
  }
}
