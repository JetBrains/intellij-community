// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.testFramework.TestLoggerKt;

/**
 * @author Roman Chernyatchik
 */
public class TestSuiteStackTest extends BaseSMTRunnerTestCase {
  private TestSuiteStack myTestSuiteStack;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myTestSuiteStack = new TestSuiteStack("from tests");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      disableDebugMode();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testPushSuite() {
    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(1, myTestSuiteStack.getStackSize());
    assertEquals(mySuite, myTestSuiteStack.getCurrentSuite());

    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(2, myTestSuiteStack.getStackSize());
    assertEquals(mySuite, myTestSuiteStack.getCurrentSuite());

    final SMTestProxy newSuite = createSuiteProxy();
    myTestSuiteStack.pushSuite(newSuite);
    assertEquals(3, myTestSuiteStack.getStackSize());
    assertEquals(newSuite, myTestSuiteStack.getCurrentSuite());
  }

  public void testGetStackSize() {
    assertEquals(0, myTestSuiteStack.getStackSize());

    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(1, myTestSuiteStack.getStackSize());

    myTestSuiteStack.popSuite(mySuite.getName());
    assertEquals(0, myTestSuiteStack.getStackSize());
  }

  public void testGetCurrentSuite() {
    assertNull(myTestSuiteStack.getCurrentSuite());

    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(mySuite, myTestSuiteStack.getCurrentSuite());
  }

  public void testPopEmptySuite_DebugMode() throws Throwable {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());

    enableDebugMode();

    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      assertThrows(Throwable.class, () -> myTestSuiteStack.popSuite("some suite"));
    });
  }

  public void testPopEmptySuite_NormalMode() {
    assertNull(myTestSuiteStack.popSuite("some suite"));
  }

  public void testPopInconsistentSuite_DebugMode() throws Throwable {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      enableDebugMode();

      final String suiteName = mySuite.getName();

      myTestSuiteStack.pushSuite(createSuiteProxy("0"));
      myTestSuiteStack.pushSuite(mySuite);
      myTestSuiteStack.pushSuite(createSuiteProxy("2"));
      myTestSuiteStack.pushSuite(createSuiteProxy("3"));

      assertEquals(4, myTestSuiteStack.getStackSize());
      assertEquals("3", myTestSuiteStack.getCurrentSuite().getName());

      assertThrows(Throwable.class, () -> myTestSuiteStack.popSuite(suiteName));
      assertEquals(4, myTestSuiteStack.getStackSize());
    });
  }

  public void testPopInconsistentSuite_NormalMode() {
    final String suiteName = mySuite.getName();

    myTestSuiteStack.pushSuite(createSuiteProxy("0"));
    myTestSuiteStack.pushSuite(mySuite);
    myTestSuiteStack.pushSuite(createSuiteProxy("2"));
    myTestSuiteStack.pushSuite(createSuiteProxy("3"));

    assertEquals(4, myTestSuiteStack.getStackSize());
    assertEquals("3", myTestSuiteStack.getCurrentSuite().getName());


    assertNotNull(myTestSuiteStack.popSuite(suiteName));
    assertEquals(1, myTestSuiteStack.getStackSize());
  }

  public void testPopSuite() {
    final String suiteName = mySuite.getName();

    myTestSuiteStack.pushSuite(mySuite);
    assertEquals(mySuite, myTestSuiteStack.popSuite(suiteName));
    assertEquals(0, myTestSuiteStack.getStackSize());
  }

  public void testGetSuitePath() {
    assertEmpty(myTestSuiteStack.getSuitePath());

    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    myTestSuiteStack.pushSuite(createSuiteProxy("2"));
    myTestSuiteStack.pushSuite(createSuiteProxy("3"));

    assertSameElements(myTestSuiteStack.getSuitePath(), "1", "2", "3");
  }

  public void testGetSuitePathPresentation() {
    assertEquals("empty", myTestSuiteStack.getSuitePathPresentation());

    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    myTestSuiteStack.pushSuite(createSuiteProxy("2"));
    myTestSuiteStack.pushSuite(createSuiteProxy("3"));

    assertEquals("[1]->[2]->[3]", myTestSuiteStack.getSuitePathPresentation());    
  }

  public void testClear() {
    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    myTestSuiteStack.pushSuite(createSuiteProxy("2"));
    myTestSuiteStack.pushSuite(createSuiteProxy("3"));
    myTestSuiteStack.clear();

    assertEquals(0, myTestSuiteStack.getStackSize());
  }

  public void testIsEmpty() {
    assertTrue(myTestSuiteStack.isEmpty());

    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    assertFalse(myTestSuiteStack.isEmpty());

    myTestSuiteStack.popSuite("1");
    assertTrue(myTestSuiteStack.isEmpty());

    myTestSuiteStack.pushSuite(createSuiteProxy("1"));
    myTestSuiteStack.pushSuite(createSuiteProxy("2"));
    myTestSuiteStack.clear();
    assertTrue(myTestSuiteStack.isEmpty());    
  }

  private static void enableDebugMode() {
    // enable debug mode
    System.setProperty("idea.smrunner.debug", "true");
  }
  private static void disableDebugMode() {
    // enable debug mode
    System.setProperty("idea.smrunner.debug", "false");
  }
}
