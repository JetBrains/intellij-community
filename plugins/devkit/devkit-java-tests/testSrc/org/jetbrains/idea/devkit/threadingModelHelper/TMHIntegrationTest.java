// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.*;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

import static com.intellij.util.concurrency.ThreadingAssertions.*;

/**
 * Ensures that the instrumenter of the Threading Model annotations (such as {@link RequiresEdt}) is in sync with
 * the source code of the Platform.
 * <p/>
 * The instrumenter is a part of the devkit plugin. If you update the source code of the Platform and didn't update
 * the devkit accordingly, the tests here may fail. Specifically, failures might happen if you:
 * <ul>
 * <li> Change {@link RequiresEdt} and similar Threading Model annotations.
 * <li> Change assertion methods of the {@link Application} class, such as {@link Application#assertIsDispatchThread()}.
 * </ul>
 * Note that if this happens, you need to update the instrumenter and install the updated version of the devkit to IDEA.
 * <p/>
 *
 * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html">General Threading Rules</a>
 */
public class TMHIntegrationTest extends LightPlatformTestCase {
  private ExecutorService mySingleThreadExecutor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    mySingleThreadExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Testing thread"));
  }

  @Override
  protected void tearDown() throws Exception {
    mySingleThreadExecutor.shutdownNow();
    super.tearDown();
  }

  public void testEdtActionOnEdt() {
    runEdtAction();
  }

  public void testEdtActionInBackground() {
    assertThrows(RuntimeExceptionWithAttachments.class, MUST_EXECUTE_UNDER_EDT, ()-> throwExecutionExceptionCauseFromBackground(
      () -> runEdtAction()));
  }

  public void testBackgroundActionOnEdt() {
    ThreadingAssertions.assertEventDispatchThread();
    assertThrows(RuntimeException.class, ()->runBackgroundAction());
  }

  public void testBackgroundActionInBackground() throws Throwable {
    throwExecutionExceptionCauseFromBackground(() -> runBackgroundAction());
  }

  public void testReadActionOnEdt() {
    runReadAction();
  }

  public void testReadActionInBackgroundWithReadLock() throws Throwable {
    throwExecutionExceptionCauseFromBackground(() -> ReadAction.run(() -> runReadAction()));
  }

  public void testReadActionInBackground() {
    assertThrows(RuntimeExceptionWithAttachments.class, MUST_EXECUTE_INSIDE_READ_ACTION, () -> throwExecutionExceptionCauseFromBackground(
      () -> runReadAction()));
  }

  public void testWriteActionOnEdtWithWriteLock() {
    WriteAction.run(() -> runWriteAction());
  }

  public void testWriteActionOnEdt() {
    assertThrows(RuntimeExceptionWithAttachments.class, MUST_EXECUTE_INSIDE_WRITE_ACTION, () -> runWriteAction());
  }

  public void testWriteActionInBackground() {
    assertThrows(RuntimeExceptionWithAttachments.class, MUST_EXECUTE_INSIDE_WRITE_ACTION, () -> throwExecutionExceptionCauseFromBackground(
      () -> runWriteAction()));
  }

  public void testNonReadActionOnEdt() {
    assertThrows(RuntimeExceptionWithAttachments.class, MUST_NOT_EXECUTE_INSIDE_READ_ACTION, () -> runNonReadAction());
  }

  public void testNonReadActionInBackground() throws Throwable {
    throwExecutionExceptionCauseFromBackground(() -> runNonReadAction());
  }

  public void testNonReadActionInBackgroundWithReadLock() {
    assertThrows(RuntimeExceptionWithAttachments.class, MUST_NOT_EXECUTE_INSIDE_READ_ACTION,
                 () -> throwExecutionExceptionCauseFromBackground(() -> ReadAction.run(() -> runNonReadAction())));
  }

  public void testNonReadActionInBackgroundWithWriteLock() {
    assertThrows(RuntimeExceptionWithAttachments.class, MUST_EXECUTE_UNDER_EDT,
                 () -> throwExecutionExceptionCauseFromBackground(() -> WriteAction.run(() -> runNonReadAction())));
  }

  public void testEdtActionInBackgroundNoAssertion() throws Throwable {
    throwExecutionExceptionCauseFromBackground(() -> runEdtActionNoAssertion());
  }

  public void testBackgroundActionOnEdtNoAssertion() {
    runBackgroundActionNoAssertion();
  }

  public void testReadActionInBackgroundNoAssertion() throws Throwable {
    throwExecutionExceptionCauseFromBackground(() -> runReadActionNoAssertion());
  }

  public void testWriteActionInBackgroundNoAssertion() throws Throwable {
    throwExecutionExceptionCauseFromBackground(() -> runWriteActionNoAssertion());
  }

  @RequiresEdt
  private static void runEdtAction() {}

  @RequiresBackgroundThread
  private static void runBackgroundAction() {}

  @RequiresReadLock
  private static void runReadAction() {}

  @RequiresWriteLock
  private static void runWriteAction() {}

  @RequiresReadLockAbsence
  private static void runNonReadAction() {}

  @RequiresEdt(generateAssertion = false)
  private static void runEdtActionNoAssertion() {}

  @RequiresBackgroundThread(generateAssertion = false)
  private static void runBackgroundActionNoAssertion() {}

  @RequiresReadLock(generateAssertion = false)
  private static void runReadActionNoAssertion() {}

  @RequiresWriteLock(generateAssertion = false)
  private static void runWriteActionNoAssertion() {}

  private void throwExecutionExceptionCauseFromBackground(@NotNull Runnable action) throws Throwable {
    try {
      Future<?> future = mySingleThreadExecutor.submit(action);
      try {
        future.get(10, TimeUnit.MINUTES);
      }
      catch (InterruptedException | TimeoutException e) {
        e.printStackTrace();
        fail("Background computation didn't finish as expected");
      }
    }
    catch (ExecutionException e) {
      throw e.getCause();
    }
  }
}
