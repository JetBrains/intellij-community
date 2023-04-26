// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static com.intellij.openapi.application.impl.UtilKt.assertReferenced;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotEquals;

@SkipInHeadlessEnvironment
public class LaterInvocatorTest extends HeavyPlatformTestCase {
  private static final Logger LOG = Logger.getInstance(LaterInvocatorTest.class);
  
  private final ArrayList<String> myOrder = new ArrayList<>();

  private Container myWindow1;
  private Container myWindow2;

  private final Runnable LEAVE_MODAL = new Runnable() {
    @Override
    public void run() {
      LaterInvocator.leaveModal(myWindow1);
    }
    public String toString() {
      return "leave modal later";
    }

  };
  private final Runnable ENTER_MODAL = new Runnable() {
    @Override
    public void run() {
      LaterInvocator.enterModal(myWindow1);
    }

    public String toString() {
      return "enter modal later";
    }
  };

  @Override
  protected void setUp() throws Exception {
    myWindow1 = new Container() {
      public String toString() {
        return "Window1";
      }
    };
    myWindow2 = new Container() {
      public String toString() {
        return "Window2";
      }
    };
    EdtTestUtil.runInEdtAndWait(() -> {
      super.setUp();
      final Object[] modalEntities = LaterInvocator.getCurrentModalEntities();
      if (modalEntities.length > 0) {
        LOG.error(
          "Expect no modal entries. Probably some of the previous tests didn't left their entries. Top entry is: " + modalEntities[0]);
      }
    });
    EdtTestUtil.runInEdtAndWait(() -> TestCase.assertFalse("Can't run test " + ModalityState.current(), LaterInvocator.isInModalContext()));

    flushSwingQueue();
  }

  @Override
  protected void tearDown() throws Exception {
    myOrder.clear();
    flushSwingQueue();
    UIUtil.invokeAndWaitIfNeeded(() -> LaterInvocator.leaveAllModals());
    EdtTestUtil.runInEdtAndWait(() -> super.tearDown());
  }

  @Override
  protected void runBareRunnable(@NotNull ThrowableRunnable<Throwable> runnable) throws Throwable {
    if (isStressTest()) {
      // this call is in hot path. make sure it's cached and local, to avoid remote crazy stuff
      ClientId.Companion.nullizeCachedServiceInTest(runnable);
    }
    else {
      runnable.run();
    }
  }

  public void testReorder() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      LaterInvocator.enterModal(myWindow1);
      synchronized (this) {
        blockSwingThread();
        ApplicationManager.getApplication().invokeLater(new MyRunnable("1"), ModalityState.NON_MODAL);
        SwingUtilities.invokeLater(ENTER_MODAL);
        LaterInvocator.leaveModal(myWindow1);
        SwingUtilities.invokeLater(LEAVE_MODAL);
        ApplicationManager.getApplication().invokeLater(new MyRunnable("2"), ModalityState.NON_MODAL);
      }
      flushSwingQueue();
      checkOrder(2);
    });
  }

  public void testTrueReorder() {
    SwingUtilities.invokeLater(() -> {
      LaterInvocator.enterModal(myWindow1);
      ApplicationManager.getApplication().invokeLater(new MyRunnable("1"), ModalityState.NON_MODAL);
      SwingUtilities.invokeLater(ENTER_MODAL);
      LaterInvocator.leaveModal(myWindow1);
      SwingUtilities.invokeLater(LEAVE_MODAL);
      ApplicationManager.getApplication().invokeLater(new MyRunnable("2"), ModalityState.NON_MODAL);
    });
    flushSwingQueue();
    flushSwingQueue();
    checkOrder(2);
  }

  public void testEverInvoked() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      LaterInvocator.enterModal(myWindow1);
      ApplicationManager.getApplication().invokeLater(ENTER_MODAL, ModalityState.NON_MODAL);
      ApplicationManager.getApplication().invokeLater(new MyRunnable("1"), ModalityState.NON_MODAL);
      LaterInvocator.leaveModal(myWindow1);
      flushSwingQueue();
      flushSwingQueue();
      checkOrder(0);
      SwingUtilities.invokeLater(LEAVE_MODAL);
      flushSwingQueue();
      flushSwingQueue();
      checkOrder(1);
    });
  }

  public void testDoesNotInvokeWhenModal() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      LaterInvocator.enterModal(myWindow1);

      ApplicationManager.getApplication().invokeLater(ENTER_MODAL, ModalityState.NON_MODAL);
      ApplicationManager.getApplication().invokeLater(new MyRunnable("1"), ModalityState.NON_MODAL);
      LaterInvocator.leaveModal(myWindow1);
      flushSwingQueue();
      checkOrder(0);
      LaterInvocator.leaveModal(myWindow1);
      flushSwingQueue();
      checkOrder(1);
    });
  }

  public void testStress() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      int N = 1000;
      for (int i = 0; i < N; i++) {
        UsefulTestCase.assertEmpty(LaterInvocator.getCurrentModalEntities());
        LaterInvocator.enterModal(myWindow2);
        //some weird things like MyFireIdleRequest may still sneak in
        //assertEquals(null, LaterInvocator.dumpQueue());
        TestCase.assertTrue(LaterInvocator.isInModalContext());
        TestCase.assertEquals(1, LaterInvocator.getCurrentModalEntities().length);

        LaterInvocator.invokeLater(ModalityState.NON_MODAL, Conditions.alwaysFalse(), new Runnable() {
          @Override
          public void run() {
            assertFalse(LaterInvocator.isInModalContext());
          }

          public String toString() {
            return "ass2";
          }
        });
        LaterInvocator.invokeLater(ModalityState.NON_MODAL, Conditions.alwaysFalse(), ENTER_MODAL);

        LaterInvocator.invokeLater(ModalityState.NON_MODAL, Conditions.alwaysFalse(), new MyRunnable("1"));

        //some weird things like MyFireIdleRequest may still sneak in
        //java.util.List<Object> dump = LaterInvocator.dumpQueue();
        //assertEquals(dump.toString(), 3, dump.size());

        LaterInvocator.leaveModal(myWindow2);
        flushSwingQueue();
        assertTrue(LaterInvocator.isInModalContext());

        checkOrder(0);

        SwingUtilities.invokeLater(LEAVE_MODAL);
        flushSwingQueue();
        assertFalse(LaterInvocator.isInModalContext());

        checkOrder(1);

        LaterInvocator.leaveAllModals();
        myOrder.clear();
        flushSwingQueue();
      }
    });
  }


  public void testOrderWithSwingInvokeLater2() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      LaterInvocator.enterModal(myWindow1);
      synchronized (this) {
        blockSwingThread();
        ApplicationManager.getApplication().invokeLater(new MyRunnable("2"), ModalityState.NON_MODAL);
        SwingUtilities.invokeLater(LEAVE_MODAL);
        SwingUtilities.invokeLater(new MyRunnable("1"));
        ApplicationManager.getApplication().invokeLater(new MyRunnable("3"), ModalityState.NON_MODAL);
      }
      flushSwingQueue();
      checkOrder(3);
    });
  }

  public void testOrderWithSwingInvokeLater3() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      LaterInvocator.enterModal(myWindow1);
      synchronized (this) {
        blockSwingThread();
        SwingUtilities.invokeLater(new MyRunnable("1"));
        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().invokeLater(new MyRunnable("3"), ModalityState.NON_MODAL), ModalityState.NON_MODAL);
        SwingUtilities.invokeLater(LEAVE_MODAL);
        ApplicationManager.getApplication().invokeLater(new MyRunnable("2"), ModalityState.NON_MODAL);
      }
      flushSwingQueue();
      checkOrder(3);
    });
  }

  public void testExpired() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        final ArrayList<String> consumed = new ArrayList<>();
        synchronized (LaterInvocatorTest.this) {
          blockSwingThread();
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              ApplicationManager.getApplication().invokeLater(new MyRunnable("1") {
                @Override
                public void run() {
                  super.run();
                  TestCase.fail("Should not be executed");
                }
              }, Conditions.alwaysTrue());
              consumed.add("1");
            }
          }, ModalityState.NON_MODAL);
          ApplicationManager.getApplication().invokeLater(() -> {
            new MyRunnable("2").run();
            consumed.add("2");
          }, ModalityState.NON_MODAL);
        }
        flushSwingQueue();

        TestCase.assertEquals(consumed.toString(), 2, consumed.size());
      }
    });
  }

  public void testOrderWithSwingInvokeLater4() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      SwingUtilities.invokeLater(ENTER_MODAL);
      flushSwingQueue();
      synchronized (this) {
        blockSwingThread();
        SwingUtilities.invokeLater(new MyRunnable("1"));
        ApplicationManager.getApplication().invokeLater(new MyRunnable("3"), ModalityState.NON_MODAL);
        SwingUtilities.invokeLater(LEAVE_MODAL);
        SwingUtilities.invokeLater(ENTER_MODAL);
        SwingUtilities.invokeLater(() -> {
          SwingUtilities.invokeLater(new MyRunnable("2"));
          ApplicationManager.getApplication().invokeLater(new MyRunnable("5"), ModalityState.NON_MODAL);
        });
        ApplicationManager.getApplication().invokeLater(new MyRunnable("4"), ModalityState.NON_MODAL);
      }
      flushSwingQueue();
      SwingUtilities.invokeLater(LEAVE_MODAL);
      flushSwingQueue();
      checkOrder(5);
    });
  }

  public void testDeadLock() throws InterruptedException, ExecutionException {
    final Object lock = new Object();
    final boolean[] started = { false };
    final AtomicReference<Future<?>> thread = new AtomicReference<>();

    UIUtil.invokeAndWaitIfNeeded(() -> {
      synchronized (this) {
        blockSwingThread();
        ApplicationManager.getApplication().invokeLater(() -> {
          thread.set(ApplicationManager.getApplication().executeOnPooledThread(() -> {
            synchronized (lock) {
              started[0] = true;
              ApplicationManager.getApplication().invokeLater(new MyRunnable("1"), ModalityState.NON_MODAL);
              lock.notifyAll();
            }
          }));
          while (!started[0]) {
            try {
              //noinspection BusyWait
              Thread.sleep(5);
            }
            catch (InterruptedException e) {
              TestCase.fail(e.getMessage());
            }
          }
          synchronized (lock) {
            checkOrder(0);
          }
        }, ModalityState.NON_MODAL);
      }
      flushSwingQueue();
      flushSwingQueue();
      checkOrder(1);
    });
    thread.get().get();
  }

  static void flushSwingQueue() {
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }
    //some weird things like MyFireIdleRequest may still sneak in
    //assertEquals(null, Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent());
  }

  private void blockSwingThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    SwingUtilities.invokeLater(new Lock(this));
  }

  private void checkOrder(int count) {
    synchronized(myOrder) {
      String order = printOrder();
      if (count != myOrder.size()) {
        TestCase.fail(order + "Expected " + count + " items but was " + myOrder.size());
      }
      for (int i = 1; i <= count; i++)
        if (!String.valueOf(i).equals(myOrder.get(i - 1))) {
          //System.out.println("Count: " + count);
          TestCase.fail(order + "Bad order");
        }
    }
  }

  private String printOrder() {
    StringBuilder out = new StringBuilder("Order: ");
    for (String order : myOrder) {
      out.append(order).append(" ");
    }
    out.append('\n');
    return out.toString();
  }

  private class MyRunnable implements Runnable {
    private final String myId;

    MyRunnable(@NotNull String id) {
      myId = id;
    }

    @Override
    public void run() {
      //System.out.println("executed: " + myId);
      synchronized(myOrder) {
        myOrder.add(myId);
      }
    }

    @Override
    public String toString() {
      return "run #" + myId;
    }
  }

  private static class Lock implements Runnable {
    private final Object myLock;

    Lock(Object lock) {
      myLock = lock;
    }

    @Override
    public void run() {
      //noinspection EmptySynchronizedStatement
      synchronized (myLock) {
        //System.out.print("");
      }
    }
  }

  public void testInvokeLaterWithNonexistentModalityStateIsInvokedInLowerModalityState() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      LaterInvocator.enterModal(myWindow2);
      ModalityState window2State = ModalityState.current();
      LaterInvocator.leaveModal(myWindow2);
      LaterInvocator.invokeLater(window2State, Conditions.alwaysFalse(), new MyRunnable("1"));

      LaterInvocator.enterModal(myWindow1);
      flushSwingQueue();
      checkOrder(0);

      LaterInvocator.leaveModal(myWindow1);
      flushSwingQueue();
      checkOrder(1);

      LaterInvocator.invokeLater(window2State, Conditions.alwaysFalse(), new MyRunnable("2"));
      flushSwingQueue();
      checkOrder(2);
    });
  }

  public void testModalityStateStaysTheSameBetweenInvocations() {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      Object modal1 = new Object();
      Object modal2 = new Object();

      LaterInvocator.enterModal(modal1);
      ModalityState modalityState1 = ModalityState.current();
      assertSame(modalityState1, ModalityState.current());

      LaterInvocator.enterModal(modal2);
      assertNotSame(modalityState1, ModalityState.current());
      LaterInvocator.leaveModal(modal2);

      assertSame(modalityState1, ModalityState.current());
    });
  }

  public void testNonNestedModalityState() { //happens with per-project modality
    Object modal1 = ObjectUtils.sentinel("modal1");
    Object modal2 = ObjectUtils.sentinel("modal2");
    UIUtil.invokeAndWaitIfNeeded(() -> {
      LaterInvocator.enterModal(modal1); // [modal1]
      ModalityState ms_1 = ModalityState.current();
      ApplicationManager.getApplication().invokeLater(new MyRunnable("m1"), ms_1);

      LaterInvocator.enterModal(modal2); //[modal1, modal2]
      ModalityState ms_12 = ModalityState.current();
      assertNotSame(ms_1, ms_12);
      assertTrue(ms_12.dominates(ms_1));

      UIUtil.dispatchAllInvocationEvents();
      assertEmpty(myOrder);

      ApplicationManager.getApplication().invokeLater(new MyRunnable("m12"), ms_12);


      LaterInvocator.leaveModal(modal1); // [modal2]
      assertEmpty(myOrder);
      UIUtil.dispatchAllInvocationEvents();
      assertOrderedEquals(myOrder, "m12");

      ModalityState ms_2 = ModalityState.current();
      assertSame(ms_12, ms_2);
      assertTrue(ms_2.dominates(ms_1));

      ApplicationManager.getApplication().invokeLater(new MyRunnable("m1x"), ms_1);
      ApplicationManager.getApplication().invokeLater(new MyRunnable("m2"), ms_2);
      UIUtil.dispatchAllInvocationEvents();
      assertOrderedEquals(myOrder, "m12", "m2");


      LaterInvocator.leaveModal(modal2); // NON_MODAL
      UIUtil.dispatchAllInvocationEvents();
      assertOrderedEquals(myOrder, "m12", "m2", "m1", "m1x");
    });
  }

  public void testModalityStateCurrentAllowedOnlyFromEDT() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    Future<ModalityState> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ModalityState.current());
    try {
      future.get(1000, TimeUnit.MILLISECONDS);
      fail("should fail");
    }
    catch (ExecutionException e) {
      assertThat(e.getMessage()).contains("EventQueue.isDispatchThread()=false");
    }
  }

  public void testDispatchInvocationEventsWorksForJustSubmitted() {
    Application app = ApplicationManager.getApplication();
    app.invokeAndWait(() -> {
      app.invokeLater(() -> {
        myOrder.add("1");
        assertOrderedEquals(myOrder, "1");
        UIUtil.dispatchAllInvocationEvents();
        assertOrderedEquals(myOrder, "1", "2");
      });
      app.invokeLater(new MyRunnable("2"));
      UIUtil.dispatchAllInvocationEvents();
    });
    assertOrderedEquals(myOrder, "1", "2");
  }

  public void testDispatchInvocationEventsVsInvokeLaterFromBgThreadRace() {
    Application app = ApplicationManager.getApplication();
    app.invokeAndWait(() -> {
      for (int i = 0; i < 20; i++) {
        AtomicBoolean executed = new AtomicBoolean();
        app.executeOnPooledThread(() -> app.invokeLater(EmptyRunnable.INSTANCE));
        app.invokeLater(() -> executed.set(true));
        UIUtil.dispatchAllInvocationEvents();
        assertTrue(executed.get());
      }
    });

  }

  @SuppressWarnings("StringOperationCanBeSimplified")
  public void testDifferentStatesAreNotEqualAfterGc() {
    String s1 = new String("foo");
    String s2 = new String("bar");
    ModalityStateEx state1 = new ModalityStateEx(Arrays.asList("common", s1));
    ModalityStateEx state2 = new ModalityStateEx(Arrays.asList("common", s2));
    assertNotEquals(state1, state2);

    GCWatcher watcher = GCWatcher.tracking(s1, s2);
    //noinspection UnusedAssignment
    s1 = s2 = null;
    watcher.ensureCollected();
    assertNotEquals(state1, state2);
  }

  public void testStateForComponentIdentity() {
    ApplicationManager.getApplication().invokeAndWait(() ->
      UITestUtil.runWithHeadlessProperty(false, () -> {
        myWindow1 = new Frame();
        myWindow2 = new Frame();
        JPanel panel = new JPanel();
        myWindow1.add(panel);
        LaterInvocator.enterModal(myWindow1);

        ModalityState state1 = ModalityState.stateForComponent(myWindow1);
        assertSame(state1, ModalityState.stateForComponent(myWindow1));
        assertSame(state1, ModalityState.stateForComponent(panel));

        LaterInvocator.enterModal(myWindow1);
        assertSame(state1, ModalityState.stateForComponent(panel));
        assertNotSame(state1, ModalityState.stateForComponent(myWindow2));
      })
    );
  }

  public void testProgressModality() {
    ApplicationManager.getApplication().invokeAndWait(() -> ProgressManager.getInstance().run(new Task.Modal(getProject(), "", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        ModalityState state = indicator.getModalityState();
        assertSame(state, ModalityState.defaultModalityState());
        ApplicationManager.getApplication().invokeAndWait(() -> assertSame(state, ModalityState.defaultModalityState()), state);
      }
    }));
  }

  public void testSwingThroughIdeEventQueuePerformance() {
    int N = 1_000_000;

    AtomicInteger counter = new AtomicInteger();
    Runnable r = () -> counter.incrementAndGet();

    PlatformTestUtil.startPerformanceTest(getTestName(false), 20_000, () -> {
      for (int i = 0; i < N; i++) {
        if (i % 8192 == 0) {
          // decrease GC pressure, we're not measuring that
          SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());
        }
        SwingUtilities.invokeLater(r);
      }
      SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());
      assertEquals(N, counter.getAndSet(0));
    }).assertTiming();
  }

  public void testApplicationInvokeLaterPerformance() {
    int N = 1_000_000;
    AtomicInteger counter = new AtomicInteger();
    Runnable r = () -> counter.incrementAndGet();
    PlatformTestUtil.startPerformanceTest(getTestName(false), 800, () -> {
      Application application = ApplicationManager.getApplication();
      for (int i = 0; i < N; i++) {
        if (i % 8192 == 0) {
          // decrease GC pressure, we're not measuring that
          application.invokeAndWait(EmptyRunnable.getInstance());
        }
        application.invokeLater(r);
      }
      application.invokeAndWait(EmptyRunnable.getInstance());
      assertEquals(N, counter.getAndSet(0));
    }).assertTiming();
  }

  public void testApplicationInvokeLaterInModalContextPerformance() {
    int N = 1_000_000;
    AtomicInteger counter = new AtomicInteger();
    Runnable r = () -> counter.incrementAndGet();
    Application application = ApplicationManager.getApplication();
    application.invokeAndWait(r);
    PlatformTestUtil.startPerformanceTest(getTestName(false), 900, () -> {
      counter.set(0);
      UIUtil.invokeAndWaitIfNeeded(() -> LaterInvocator.enterModal(myWindow1));
      for (int i = 0; i < N; i++) {
        application.invokeLater(r);
      }
      assertEquals(0, counter.get());
      UIUtil.invokeAndWaitIfNeeded(() -> LaterInvocator.leaveModal(myWindow1));
      application.invokeAndWait(EmptyRunnable.getInstance());
      assertEquals(N, counter.get());
    }).assertTiming();
  }

  private final JDialog myModalDialog = new JDialog((Dialog)null, true);

  public void testModalityStateForNonDisplayedDialogGetsActualizedWhenItIsDisplayed() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ModalityState state = ModalityState.stateForComponent(myModalDialog);
      AtomicBoolean invoked = new AtomicBoolean();
      ApplicationManager.getApplication().invokeLater(() -> invoked.set(true), state);
      
      LaterInvocator.enterModal("some object");

      UIUtil.dispatchAllInvocationEvents();
      assertFalse(invoked.get());
      
      LaterInvocator.enterModal(myModalDialog);

      UIUtil.dispatchAllInvocationEvents();
      assertTrue(invoked.get());
    });
  }

  public void testModalityStateWorksImmediately() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ModalityState state = ModalityState.stateForComponent(myModalDialog);
      AtomicBoolean invoked = new AtomicBoolean();
      ApplicationManager.getApplication().invokeLater(() -> invoked.set(true), state);
      
      UIUtil.dispatchAllInvocationEvents();
      assertTrue(invoked.get());
    });
  }

  public void testInvokeLaterGoesIntoTransparentModality() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AtomicBoolean invoked = new AtomicBoolean();
      ApplicationManager.getApplication().invokeLater(() -> invoked.set(true), ModalityState.NON_MODAL);
      LaterInvocator.enterModal(myWindow1);

      UIUtil.dispatchAllInvocationEvents();
      assertFalse(invoked.get());

      LaterInvocator.markTransparent(ModalityState.current());
      UIUtil.dispatchAllInvocationEvents();
      assertTrue(invoked.get());
    });
  }

  public void testInvokeLaterGoesIntoModalityDeclaredTransparentBeforeEntering() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AtomicBoolean invoked = new AtomicBoolean();
      LaterInvocator.markTransparent(ModalityState.stateForComponent(myModalDialog));
      ApplicationManager.getApplication().invokeLater(() -> invoked.set(true), ModalityState.NON_MODAL);
      LaterInvocator.enterModal(myModalDialog);
      UIUtil.dispatchAllInvocationEvents();
      assertTrue(invoked.get());
    });
  }

  public void testInvokeLaterAlwaysSchedulesFlush() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AtomicBoolean executed = new AtomicBoolean();
      for (int i = 0; i < 1_000_000; i++) {
        executed.set(false);
        ApplicationManager.getApplication().invokeLater(() -> executed.set(true));
        UIUtil.dispatchAllInvocationEvents();
        assertTrue(executed.get());
      }
    });
  }

  public void testInvokeAndWaitIsCancellable() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AtomicBoolean executed = new AtomicBoolean();
      Runnable testRunnable = () -> {
        executed.set(true);
      };
      AtomicBoolean gotPCE = new AtomicBoolean();
      LaterInvocator.enterModal(myWindow1);
      var indicator = new EmptyProgressIndicator(ModalityState.NON_MODAL);
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        ProgressManager.getInstance().runProcess(() -> {
          try {
            // this should schedule the runnable with NON_MODAL
            ApplicationManager.getApplication().invokeAndWait(testRunnable);
          }
          catch (ProcessCanceledException pce) {
            gotPCE.set(true);
            throw pce;
          }
        }, indicator);
      });
      LockSupport.parkNanos(50_000_000); // give chance to schedule testRunnable
      assertReferenced(LaterInvocator.class, testRunnable); // ensure testRunnable in the queue
      UIUtil.dispatchAllInvocationEvents();
      assertReferenced(LaterInvocator.class, testRunnable); // ensure testRunnable is still in the queue
      indicator.cancel();
      LockSupport.parkNanos(50_000_000); // give chance to loop and call checkCanceled
      assertFalse(executed.get());
      assertTrue(gotPCE.get());
      LeakHunter.checkLeak(LaterInvocator.class, testRunnable.getClass());
    });
  }
}