// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.intellij.util.concurrency.ThreadingAssertions.MUST_EXECUTE_IN_EDT;
import static com.intellij.util.concurrency.ThreadingAssertions.MUST_EXECUTE_IN_READ_ACTION;
import static com.intellij.util.concurrency.ThreadingAssertions.MUST_EXECUTE_IN_WRITE_ACTION;
import static com.intellij.util.concurrency.ThreadingAssertions.MUST_NOT_EXECUTE_IN_READ_ACTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Ensures that the instrumenter of the Threading Model annotations (such as [RequiresEdt]) is in sync with
/// the source code of the Platform.
///
/// The instrumenter is a part of the DevKit plugin. If you update the source code of the Platform and didn't update
/// the DevKit accordingly, the tests here may fail. Specifically, failures might happen if you:
///
///   - Change [RequiresEdt] and similar Threading Model annotations.
///   - Change assertion methods of the [Application] class, such as [Application#assertIsDispatchThread()].
///
/// Note that if this happens, you need to update the instrumenter and install the updated version of the DevKit to IDEA.
///
/// @see <a href="https://plugins.jetbrains.com/docs/intellij/threading-model.html">Threading Model</a>
public class TMHIntegrationTest extends BareTestFixtureTestCase {
  @Rule public EdtRule edtRule = new EdtRule();

  private ExecutorService mySingleThreadExecutor;

  @Before
  public void setUp() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    mySingleThreadExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Testing thread"));
  }

  @After
  public void tearDown() {
    mySingleThreadExecutor.shutdownNow();
  }

  @Test
  @RunsInEdt
  public void testEdtActionOnEdt() {
    runEdtAction();
  }

  @Test
  public void testEdtActionInBackground() {
    assertThatThrownBy(() -> runEdtAction())
      .isInstanceOf(RuntimeExceptionWithAttachments.class)
      .hasMessageContaining(MUST_EXECUTE_IN_EDT);
  }

  @Test
  @RunsInEdt
  public void testBackgroundActionOnEdt() {
    ThreadingAssertions.assertEventDispatchThread();
    assertThatThrownBy(() -> runBackgroundAction()).isInstanceOf(RuntimeException.class);
  }

  @Test
  public void testBackgroundActionInBackground() {
    runBackgroundAction();
  }

  @Test
  @RunsInEdt
  public void testReadActionOnEdt() {
    runReadAction();
  }

  @Test
  public void testReadActionInBackgroundWithReadLock() {
    ReadAction.runBlocking(() -> runReadAction());
  }

  @Test
  public void testReadActionInBackground() {
    assertThat(LoggedErrorProcessor.executeAndReturnLoggedError(() -> runReadAction()))
      .isInstanceOf(RuntimeExceptionWithAttachments.class)
      .hasMessageContaining(MUST_EXECUTE_IN_READ_ACTION);
  }

  @Test
  @RunsInEdt
  public void testWriteActionOnEdtWithWriteLock() {
    WriteAction.run(() -> runWriteAction());
  }

  @Test
  @RunsInEdt
  public void testWriteActionOnEdt() {
    assertThatThrownBy(() -> runWriteAction())
      .isInstanceOf(RuntimeExceptionWithAttachments.class)
      .hasMessageContaining(MUST_EXECUTE_IN_WRITE_ACTION);
  }

  @Test
  public void testWriteActionInBackground() {
    assertThatThrownBy(() -> runWriteAction())
      .isInstanceOf(RuntimeExceptionWithAttachments.class)
      .hasMessageContaining(MUST_EXECUTE_IN_WRITE_ACTION);
  }

  @Test
  @RunsInEdt
  public void testNonReadActionOnEdt() {
    assertThatThrownBy(() -> runNonReadAction())
      .isInstanceOf(RuntimeExceptionWithAttachments.class)
      .hasMessageContaining(MUST_NOT_EXECUTE_IN_READ_ACTION);
  }

  @Test
  public void testNonReadActionInBackground() {
    runNonReadAction();
  }

  @Test
  public void testNonReadActionInBackgroundWithReadLock() {
    assertThatThrownBy(() -> ReadAction.runBlocking(() -> runNonReadAction()))
      .isInstanceOf(RuntimeExceptionWithAttachments.class)
      .hasMessageContaining(MUST_NOT_EXECUTE_IN_READ_ACTION);
  }

  @Test
  public void testEdtActionInBackgroundNoAssertion() {
    runEdtActionNoAssertion();
  }

  @Test
  @RunsInEdt
  public void testBackgroundActionOnEdtNoAssertion() {
    runBackgroundActionNoAssertion();
  }

  @Test
  public void testReadActionInBackgroundNoAssertion() {
    runReadActionNoAssertion();
  }

  @Test
  public void testWriteActionInBackgroundNoAssertion() {
    runWriteActionNoAssertion();
  }

  @RequiresEdt
  private static void runEdtAction() { }

  @RequiresBackgroundThread
  private static void runBackgroundAction() { }

  @RequiresReadLock
  private static void runReadAction() { }

  @RequiresWriteLock
  private static void runWriteAction() { }

  @RequiresReadLockAbsence
  private static void runNonReadAction() { }

  @RequiresEdt(generateAssertion = false)
  private static void runEdtActionNoAssertion() { }

  @RequiresBackgroundThread(generateAssertion = false)
  private static void runBackgroundActionNoAssertion() { }

  @RequiresReadLock(generateAssertion = false)
  private static void runReadActionNoAssertion() { }

  @RequiresWriteLock(generateAssertion = false)
  private static void runWriteActionNoAssertion() { }
}
