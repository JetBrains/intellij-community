// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.TestTimeOut;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class IdeEventQueueTest extends LightPlatformTestCase {
  public void testManyEventsStress() {
    int N = 100000;
    PlatformTestUtil.startPerformanceTest("Event queue dispatch", 10000, () -> {
      UIUtil.dispatchAllInvocationEvents();
      AtomicInteger count = new AtomicInteger();
      for (int i = 0; i < N; i++) {
        SwingUtilities.invokeLater(count::incrementAndGet);
      }
      UIUtil.dispatchAllInvocationEvents();
      assertEquals(N, count.get());
    }).assertTiming();
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

    int posted = ideEventQueue.myKeyboardEventsPosted.get();
    int dispatched = ideEventQueue.myKeyboardEventsDispatched.get();
    KeyEvent pressX = new KeyEvent(new JLabel("mykeypress"), KeyEvent.KEY_PRESSED, 1, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, 11, 'x');
    postCarefully(pressX);
    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched, ideEventQueue.myKeyboardEventsDispatched.get());
    dispatchAllInvocationEventsUntilOtherEvent();
    // either it's dispatched by this method or the f*@$ing VCSRefresh activity stomped in, started modal progress and consumed all events via IdeEventQueue.pumpEventsForHierarchy
    assertTrue(isDispatched.contains(pressX) || isConsumed(pressX));

    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());

    // do not react to other events
    AWTEvent ev2 = new ActionEvent(new JLabel(), ActionEvent.ACTION_PERFORMED, "myCommand");
    postCarefully(ev2);

    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());
    dispatchAllInvocationEventsUntilOtherEvent();
    // either it's dispatched by this method or the f*@$ing VCSRefresh activity stomped in, started modal progress and dispatched all events via IdeEventQueue.pumpEventsForHierarchy by itself
    assertTrue(isDispatched.contains(ev2));

    assertEquals(posted+1, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());

    KeyEvent keyRelease = new KeyEvent(new JLabel("mykeyrelease"), KeyEvent.KEY_RELEASED, 1, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, 11, 'x');
    postCarefully(keyRelease);

    assertEquals(posted+2, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+1, ideEventQueue.myKeyboardEventsDispatched.get());

    dispatchAllInvocationEventsUntilOtherEvent();
    // either it's dispatched by this method or the f*@$ing VCSRefresh activity stomped in, started modal progress and consumed all events via IdeEventQueue.pumpEventsForHierarchy
    assertTrue(isDispatched.contains(keyRelease) || isConsumed(keyRelease));

    assertEquals(posted+2, ideEventQueue.myKeyboardEventsPosted.get());
    assertEquals(dispatched+2, ideEventQueue.myKeyboardEventsDispatched.get());
  }

  private static void postCarefully(AWTEvent event) {
    LOG.debug("posting " + event);
    IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
    boolean posted = ideEventQueue.doPostEvent(event);
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
      catch (MyException e) {
        break;
      }
      assertFalse(t.timedOut());
    }
  }

  public void testExceptionInAlarmMustThrowImmediatelyInTests() {
    Alarm alarm = new Alarm();
    alarm.addRequest(()-> throwMyException(), 1);
    checkMyExceptionThrownImmediately();
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
    EdtExecutorService.getInstance().execute(()->throwMyException(), ModalityState.NON_MODAL);
    checkMyExceptionThrownImmediately();
  }

  public void testEdtScheduledExecutorRunnableMustThrowImmediatelyInTests() {
    EdtExecutorService.getScheduledExecutorInstance().schedule(()->throwMyException(), 1, TimeUnit.MILLISECONDS);
    checkMyExceptionThrownImmediately();
  }

  public void testNoExceptionEvenCreatedByThanosExtensionNotApplicableExceptionMustKillEDT() {
    assert SwingUtilities.isEventDispatchThread();
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
    AtomicReference<Throwable> error = new AtomicReference<>();
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public boolean processError(@NotNull String category, String message, Throwable t, String @NotNull [] details) {
        assertNull(error.get());
        error.set(t);
        return false;
      }
    }, () -> {
      IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
      ideEventQueue.executeInProductionModeEvenThoughWeAreInTests(() -> ideEventQueue.dispatchEvent(event));
    });

    assertTrue(run.get());
    assertSame(expectedToBeLogged, error.get());
  }

  public void testPumpEventsForHierarchyMustExitOnIsCancelEventCondition() {
    assert SwingUtilities.isEventDispatchThread();
    IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
    CompletableFuture<Object> future = new CompletableFuture<>();
    TestTimeOut cancelEventTime = TestTimeOut.setTimeout(2, TimeUnit.SECONDS);
    JLabel component = new JLabel();
    long start = System.currentTimeMillis();
    ideEventQueue.pumpEventsForHierarchy(component, future, event -> {
      if (cancelEventTime.isTimedOut()) {
        ideEventQueue.postEvent(new TextEvent(component, -239){
          @Override
          public String paramString() {
            return "my";
          }
        });
      }
      // post InvocationEvent to give getNextEvent work to do
      SwingUtilities.invokeLater(EmptyRunnable.getInstance());
      return "my".equals(event.paramString());
    });
    long elapsedMs = System.currentTimeMillis() - start;
    // check that first, we did exit the pumpEventsForHierarchy and second, at the right moment
    assertTrue(String.valueOf(elapsedMs), cancelEventTime.isTimedOut());
  }

  public void testPumpEventsForHierarchyMustExitOnIsFutureDoneCondition() {
    assert SwingUtilities.isEventDispatchThread();
    IdeEventQueue ideEventQueue = IdeEventQueue.getInstance();
    CompletableFuture<Object> future = new CompletableFuture<>();
    TestTimeOut cancelEventTime = TestTimeOut.setTimeout(2, TimeUnit.SECONDS);
    JLabel component = new JLabel();
    long start = System.currentTimeMillis();
    ideEventQueue.pumpEventsForHierarchy(component, future, __ -> {
      if (cancelEventTime.isTimedOut()) {
        future.complete(null);
      }
      // post InvocationEvent to give getNextEvent work to do
      SwingUtilities.invokeLater(EmptyRunnable.getInstance());
      return false;
    });
    long elapsedMs = System.currentTimeMillis() - start;
    // check that first, we did exit the pumpEventsForHierarchy and second, at the right moment
    assertTrue(String.valueOf(elapsedMs), cancelEventTime.isTimedOut());
  }
}
