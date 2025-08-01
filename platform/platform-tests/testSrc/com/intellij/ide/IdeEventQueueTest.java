// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.TestOnlyThreading;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.TestTimeOut;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.platform.locking.impl.IntelliJLockingUtil.getGlobalThreadingSupport;

public class IdeEventQueueTest extends LightPlatformTestCase {
  public void testManyEventsStressPerformance() {
    int N = 100000;
    Benchmark.newBenchmark("Event queue dispatch", () -> {
      UIUtil.dispatchAllInvocationEvents();
      AtomicInteger count = new AtomicInteger();
      for (int i = 0; i < N; i++) {
        SwingUtilities.invokeLater(count::incrementAndGet);
      }
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(N, count.get());
    }).start();
  }

  public void testKeyboardEventsAreDetected() throws InterruptedException {
    assertTrue(EventQueue.isDispatchThread());
    IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
    assertSame(ideEventQueue, Toolkit.getDefaultToolkit().getSystemEventQueue());
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    Set<AWTEvent> isDispatched = new HashSet<>();
    ideEventQueue.addDispatcher(e -> {
      isDispatched.add(e);
      LOG.debug("dispatch: "+e);
      return false;
    }, getTestRootDisposable());
    ideEventQueue.addPostprocessor(e -> {
      LOG.debug("post dispatch: "+e);
      return false;
    }, getTestRootDisposable());
    ideEventQueue.addPostEventListener(e -> {
      LOG.debug("post event hook: "+e);
      return false;
    }, getTestRootDisposable());

    int posted = ideEventQueue.keyboardEventPosted.get();
    int dispatched = ideEventQueue.keyboardEventDispatched.get();
    KeyEvent pressX = new KeyEvent(new JLabel("myKeyPress"), KeyEvent.KEY_PRESSED, 1, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, 11, 'x');
    postCarefully(pressX);
    assertEquals(posted+1, ideEventQueue.keyboardEventPosted.get());
    assertEquals(dispatched, ideEventQueue.keyboardEventDispatched.get());
    dispatchAllInvocationEventsUntilOtherEvent();
    // either it's dispatched by this method or the f*@$ing VCSRefresh activity stomped in, started modal progress and consumed all events via IdeEventQueue.pumpEventsForHierarchy
    assertTrue(isDispatched.contains(pressX) || isConsumed(pressX));

    assertEquals(posted+1, ideEventQueue.keyboardEventPosted.get());
    assertEquals(dispatched+1, ideEventQueue.keyboardEventDispatched.get());

    // do not react to other events
    AWTEvent ev2 = new ActionEvent(new JLabel(), ActionEvent.ACTION_PERFORMED, "myCommand");
    postCarefully(ev2);

    assertEquals(posted+1, ideEventQueue.keyboardEventPosted.get());
    assertEquals(dispatched+1, ideEventQueue.keyboardEventDispatched.get());
    dispatchAllInvocationEventsUntilOtherEvent();
    // either it's dispatched by this method or the f*@$ing VCSRefresh activity stomped in, started modal progress and dispatched all events via IdeEventQueue.pumpEventsForHierarchy by itself
    assertTrue(isDispatched.contains(ev2));

    assertEquals(posted+1, ideEventQueue.keyboardEventPosted.get());
    assertEquals(dispatched+1, ideEventQueue.keyboardEventDispatched.get());

    KeyEvent keyRelease = new KeyEvent(new JLabel("myKeyRelease"), KeyEvent.KEY_RELEASED, 1, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, 11, 'x');
    postCarefully(keyRelease);

    assertEquals(posted+2, ideEventQueue.keyboardEventPosted.get());
    assertEquals(dispatched+1, ideEventQueue.keyboardEventDispatched.get());

    dispatchAllInvocationEventsUntilOtherEvent();
    // either it's dispatched by this method or the f*@$ing VCSRefresh activity stomped in, started modal progress and consumed all events via IdeEventQueue.pumpEventsForHierarchy
    assertTrue(isDispatched.contains(keyRelease) || isConsumed(keyRelease));

    assertEquals(posted+2, ideEventQueue.keyboardEventPosted.get());
    assertEquals(dispatched+2, ideEventQueue.keyboardEventDispatched.get());
  }

  private static void postCarefully(AWTEvent event) {
    LOG.debug("posting " + event);
    IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
    boolean posted = ideEventQueue.doPostEvent(event, false);
    assertTrue("Was not posted: "+event, posted);
    boolean mustBeConsumed = event.getID() == ActionEvent.ACTION_PERFORMED;
    assertEquals(mustBeConsumed, ReflectionUtil.getField(AWTEvent.class, event, boolean.class, "consumed").booleanValue());
    assertTrue(ReflectionUtil.getField(AWTEvent.class, event, boolean.class, "isPosted"));
  }
  private static boolean isConsumed(InputEvent event) {
    return event.isConsumed();
  }

  // need this because everybody can post some crazy stuff to IdeEventQueue, so we have to filter InvocationEvents out
  private static void dispatchAllInvocationEventsUntilOtherEvent() throws InterruptedException {
    while (true) {
      AWTEvent event = PlatformTestUtil.dispatchNextEventIfAny();
      LOG.debug("event dispatched in dispatchAll() "+event+"; -"+(event instanceof InvocationEvent ? "continuing" : "returning"));
      if (!(event instanceof InvocationEvent)) break;
    }
  }

  private static class MyException extends RuntimeException {
  }
  private static void throwMyException() {
    throw new MyException();
  }

  private static void checkMyExceptionThrownImmediately() {
    TestTimeOut t = TestTimeOut.setTimeout(10, TimeUnit.SECONDS);
    while (true) {
      try {
        UIUtil.dispatchAllInvocationEvents();
      }
      catch (Throwable e) {
        assertTrue(e.toString(), ExceptionUtil.causedBy(e, MyException.class));
        break;
      }
      assertFalse(t.timedOut());
    }
  }

  public void testExceptionInInvokeLateredRunnableMustThrowImmediatelyInTests() {
    SwingUtilities.invokeLater(() -> throwMyException());
    checkMyExceptionThrownImmediately();
  }

  public void testAppInvokeLateredRunnableMustThrowImmediatelyInTests() {
    SwingUtilities.invokeLater(()->ApplicationManager.getApplication().invokeLater(()->throwMyException()));
    checkMyExceptionThrownImmediately();
  }

  public void testEdtExecutorRunnableMustThrowImmediatelyInTests() {
    ApplicationManager.getApplication().invokeLater(() -> throwMyException(), ModalityState.nonModal());
    checkMyExceptionThrownImmediately();
  }

  public void testEdtScheduledExecutorRunnableMustThrowImmediatelyInTests() throws Exception {
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      EdtExecutorService.getScheduledExecutorInstance().schedule(() -> throwMyException(), 1, TimeUnit.MILLISECONDS);
      checkMyExceptionThrownImmediately();
    });
  }

  public void testNoExceptionEvenCreatedByThanosExtensionNotApplicableExceptionMustKillEDT() {
    assertTrue(SwingUtilities.isEventDispatchThread());
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    throwInIdeEventQueueDispatch(ExtensionNotApplicableException.create(), null); // ControlFlowException silently ignored
    throwInIdeEventQueueDispatch(new ProcessCanceledException(), null);  // ControlFlowException silently ignored
    Error error = new Error();
    throwInIdeEventQueueDispatch(error, error);
  }

  private void throwInIdeEventQueueDispatch(@NotNull Throwable toThrow, Throwable expectedToBeLogged) {
    AtomicBoolean run = new AtomicBoolean();
    InvocationEvent event = new InvocationEvent(this, () -> {
      run.set(true);
      ExceptionUtil.rethrow(toThrow);
    });
    Runnable runnable = () -> {
      IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
      ideEventQueue.executeInProductionModeEvenThoughWeAreInTests(() -> ideEventQueue.dispatchEvent(event));
    };
    
    Throwable error;
    if (expectedToBeLogged != null) {
      error = LoggedErrorProcessor.executeAndReturnLoggedError(runnable);
    }
    else {
      runnable.run();
      error = null;
    }
    assertTrue(run.get());
    assertSame(expectedToBeLogged, error);
  }

  public void testPumpEventsForHierarchyMustExitOnIsFutureDoneCondition() {
    assertTrue(SwingUtilities.isEventDispatchThread());
    var ideEventQueue = IdeEventQueue.getInstance();
    var future = new CompletableFuture<>();
    var cancelEventTime = TestTimeOut.setTimeout(2, TimeUnit.SECONDS);
    var component = new JLabel();
    AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> SwingUtilities.invokeLater(EmptyRunnable.getInstance()), 100, TimeUnit.MILLISECONDS);
    var start = System.nanoTime();
    ideEventQueue.pumpEventsForHierarchy(component, future, __ -> {
      if (cancelEventTime.isTimedOut()) {
        future.complete(null);
      }
      // post InvocationEvent to give getNextEvent work to do
      SwingUtilities.invokeLater(EmptyRunnable.getInstance());
    });
    var elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    // check that first, we did exit the pumpEventsForHierarchy and second, at the right moment
    assertTrue(String.valueOf(elapsedMs), cancelEventTime.isTimedOut());
  }

  public void testNonLockedEventDispatcherHasNoWriteIntentAccess() {
    TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
      assertFalse(ApplicationManager.getApplication().isWriteIntentLockAcquired());
      var ideEventQueue = IdeEventQueue.getInstance();
      var threadingSupport = getGlobalThreadingSupport();
      var hasWriteIntentAccess = new AtomicBoolean(false);
      var dispatcherCalled = new AtomicBoolean(false);

      // Create a NonLockedEventDispatcher that tries to check for write intent access
      IdeEventQueue.NonLockedEventDispatcher dispatcher = e -> {
        dispatcherCalled.set(true);
        // This should not have write intent access
        hasWriteIntentAccess.set(threadingSupport.isWriteIntentLocked());
        return false;
      };

      ideEventQueue.addDispatcher(dispatcher, getTestRootDisposable());

      // Post a test event to trigger the dispatcher
      var testEvent = new ActionEvent(new JLabel(), ActionEvent.ACTION_PERFORMED, "test");
      postCarefully(testEvent);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertTrue("NonLockedEventDispatcher should have been called", dispatcherCalled.get());
      assertFalse("NonLockedEventDispatcher should not have write intent access", hasWriteIntentAccess.get());
      return Unit.INSTANCE;
    });
  }

  public void testRegularEventDispatcherHasWriteIntentAccess() {
    TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
      assertFalse(ApplicationManager.getApplication().isWriteIntentLockAcquired());

      var ideEventQueue = IdeEventQueue.getInstance();
      var threadingSupport = getGlobalThreadingSupport();
      var hasWriteIntentAccess = new AtomicBoolean(false);
      var dispatcherCalled = new AtomicBoolean(false);

      // Create a regular EventDispatcher to verify it has write intent access (for comparison)
      @SuppressWarnings("deprecation")
      IdeEventQueue.EventDispatcher dispatcher = e -> {
        dispatcherCalled.set(true);
        // Regular dispatchers should have write intent access
        hasWriteIntentAccess.set(threadingSupport.isWriteIntentLocked());
        return false;
      };

      ideEventQueue.addDispatcher(dispatcher, getTestRootDisposable());

      // Post a test event to trigger the dispatcher
      var testEvent = new ActionEvent(new JLabel(), ActionEvent.ACTION_PERFORMED, "test");
      postCarefully(testEvent);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

      assertTrue("Regular EventDispatcher should have been called", dispatcherCalled.get());
      assertTrue("Regular EventDispatcher should have write intent access", hasWriteIntentAccess.get());
      return Unit.INSTANCE;
    });
  }
}
