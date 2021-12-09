// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.threadingModelHelper;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;

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
  private static final String EDT_ASSERTION_MESSAGE = "Access is allowed from event dispatch thread only";
  private static final String READ_ACCESS_ASSERTION_MESSAGE = "Read access is allowed from inside read-action (or EDT) only";
  private static final String WRITE_ACCESS_ASSERTION_MESSAGE = "Write access is allowed inside write-action only";
  private static final String NON_READ_ACCESS_ASSERTION_MESSAGE = "Read access is not allowed";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
  }

  public void testEdtActionOnEdt() {
    runEdtAction();
  }

  public void testEdtActionInBackground() {
    Throwable exception = getExecutionException(startInBackground(() -> runEdtAction()));
    assertNotNull(exception);
    assertUserMessageContains(exception, EDT_ASSERTION_MESSAGE);
  }

  /**
   * This test doesn't fail because {@link Application#assertIsNonDispatchThread()} does nothing in the unit test mode.
   * Still the test assures that the method exists.
   */
  public void testBackgroundActionOnEdt() {
    runBackgroundAction();
  }

  public void testBackgroundActionInBackground() {
    assertNull(getExecutionException(startInBackground(() -> runBackgroundAction())));
  }

  public void testReadActionOnEdt() {
    runReadAction();
  }

  public void testReadActionInBackgroundWithReadLock() {
    assertNull(getExecutionException(startInBackground(() -> ReadAction.run(() -> runReadAction()))));
  }

  public void testReadActionInBackground() {
    assertThrows(Throwable.class, READ_ACCESS_ASSERTION_MESSAGE, () -> waitResult(startInBackground(() -> runReadAction())));
  }

  public void testWriteActionOnEdtWithWriteLock() {
    WriteAction.run(() -> runWriteAction());
  }

  public void testWriteActionOnEdt() {
    assertThrows(Throwable.class, WRITE_ACCESS_ASSERTION_MESSAGE, () -> runWriteAction());
  }

  public void testWriteActionInBackground() {
    assertThrows(Throwable.class, WRITE_ACCESS_ASSERTION_MESSAGE, () -> waitResult(startInBackground(() -> runWriteAction())));
  }

  public void _testNonReadActionOnEdt() {
    assertThrows(Throwable.class, NON_READ_ACCESS_ASSERTION_MESSAGE, () -> runNonReadAction());
  }

  public void _testNonReadActionInBackground() {
    assertNull(getExecutionException(startInBackground(() -> runNonReadAction())));
  }

  public void _testNonReadActionInBackgroundWithReadLock() {
    assertThrows(Throwable.class, NON_READ_ACCESS_ASSERTION_MESSAGE,
                 () -> waitResult(startInBackground(() -> ReadAction.run(() -> runNonReadAction()))));
  }

  public void _testNonReadActionInBackgroundWithWriteLock() {
    assertThrows(Throwable.class, NON_READ_ACCESS_ASSERTION_MESSAGE,
                 () -> waitResult(startInBackground(() -> WriteAction.run(() -> runNonReadAction()))));
  }

  public void testEdtActionInBackgroundNoAssertion() throws ExecutionException {
    waitResult(startInBackground(() -> runEdtActionNoAssertion()));
  }

  public void testBackgroundActionOnEdtNoAssertion() {
    runBackgroundActionNoAssertion();
  }

  public void testReadActionInBackgroundNoAssertion() throws ExecutionException {
    waitResult(startInBackground(() -> runReadActionNoAssertion()));
  }

  public void testWriteActionInBackgroundNoAssertion() throws ExecutionException {
    waitResult(startInBackground(() -> runWriteActionNoAssertion()));
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

  private static @NotNull Future<?> startInBackground(@NotNull Runnable runnable) {
    return Executors.newSingleThreadExecutor(r -> new Thread(r, "Testing thread")).submit(runnable);
  }

  private static @Nullable Throwable getExecutionException(@NotNull Future<?> future) {
    try {
      waitResult(future);
      return null;
    }
    catch (ExecutionException e) {
      return e.getCause();
    }
  }

  private static void waitResult(@NotNull Future<?> future) throws ExecutionException {
    try {
      future.get(10, TimeUnit.MINUTES);
    }
    catch (InterruptedException | TimeoutException e) {
      e.printStackTrace();
      fail("Background computation didn't finish as expected");
    }
  }

  @SuppressWarnings("SameParameterValue")
  private static void assertUserMessageContains(@NotNull Throwable exception, @NotNull String message) {
    String userMessage = ObjectUtils.doIfCast(exception, RuntimeExceptionWithAttachments.class, e -> e.getUserMessage());
    assertNotNull(userMessage);
    assertTrue(userMessage.contains(message));
  }
}
