/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.*;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"SSBasedInspection", "SynchronizeOnThis"})
@SkipInHeadlessEnvironment
public class LaterInvocatorTest extends PlatformTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.LaterInvokatorTest");
  
  private final ArrayList<String> myOrder = new ArrayList<>();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private Window myWindow1;
  private Window myWindow2;

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
  protected void setUp() {
    myWindow1 = new Frame() {
      public String toString() {
        return "Window1";
      }
    };
    myWindow2 = new Frame() {
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
  protected void runBareRunnable(ThrowableRunnable<Throwable> runnable) throws Throwable {
    runnable.run();
  }

  @Override
  protected void tearDown() {
    myOrder.clear();
    final boolean[] inModalState = {true};
    ApplicationManager.getApplication().invokeLater(() -> {
      synchronized (inModalState) {
        inModalState[0] = false;
      }
    }, ModalityState.NON_MODAL);
    flushSwingQueue();
    flushSwingQueue();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      while (true) {
        synchronized (inModalState) {
          if (!inModalState[0]) break;
        }
        flushSwingQueue();
        synchronized (inModalState) {
          if (inModalState[0] && LaterInvocator.isInModalContext()) LaterInvocator.leaveAllModals();
        }
      }
    });

    EdtTestUtil.runInEdtAndWait(() -> super.tearDown());
  }

  public void testReorder() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      int N = 1000;
      //long start = System.currentTimeMillis();
      for (int i = 0; i < N; i++) {
        /*
        if (i % 10 == 0) {
          long elapsed = System.currentTimeMillis() - start;
          System.out.println("i = " + i+"; elapsed="+elapsed);
          start = System.currentTimeMillis();
        }
        */

        //assertEquals(null, Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent());
        //assertEquals(null, LaterInvocator.dumpQueue());

        UsefulTestCase.assertEmpty(LaterInvocator.getCurrentModalEntities());
        LaterInvocator.enterModal(myWindow2);
        //some weird things like MyFireIdleRequest may still sneak in
        //assertEquals(null, LaterInvocator.dumpQueue());
        TestCase.assertTrue(LaterInvocator.isInModalContext());
        TestCase.assertEquals(1, LaterInvocator.getCurrentModalEntities().length);

        LaterInvocator.invokeLater(new Runnable() {
          @Override
          public void run() {
            TestCase.assertTrue(!LaterInvocator.isInModalContext());
          }

          public String toString() {
            return "ass2";
          }
        }, ModalityState.NON_MODAL);
        LaterInvocator.invokeLater(ENTER_MODAL, ModalityState.NON_MODAL);

        LaterInvocator.invokeLater(new MyRunnable("1"), ModalityState.NON_MODAL);

        //some weird things like MyFireIdleRequest may still sneak in
        //java.util.List<Object> dump = LaterInvocator.dumpQueue();
        //assertEquals(dump.toString(), 3, dump.size());

        LaterInvocator.leaveModal(myWindow2);
        flushSwingQueue();
        if (!LaterInvocator.isInModalContext()) {
          //System.out.println("inv queue" + LaterInvocator.dumpQueue());
          TestCase.fail();
        }

        checkOrder(0);

        SwingUtilities.invokeLater(LEAVE_MODAL);
        flushSwingQueue();
        TestCase.assertTrue(!LaterInvocator.isInModalContext());

        checkOrder(1);


        LaterInvocator.leaveAllModals();
        myOrder.clear();
        flushSwingQueue();
      }
    });
  }


  public void testOrderWithSwingInvokeLater2() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
          ApplicationManager.getApplication().getInvokator().invokeLater(new Runnable() {
            @Override
            public void run() {
              ApplicationManager.getApplication().invokeLater(new MyRunnable("1") {
                @Override
                public void run() {
                  super.run();
                  TestCase.fail("Should not be executed");
                }
              }, Conditions.alwaysTrue());
            }
          }, ModalityState.NON_MODAL).doWhenDone(() -> consumed.add("1"));
          ApplicationManager.getApplication().getInvokator().invokeLater(new MyRunnable("2"), ModalityState.NON_MODAL)
            .doWhenDone(() -> consumed.add("2"));
        }
        flushSwingQueue();

        TestCase.assertEquals(consumed.toString(), 2, consumed.size());
      }
    });
  }

  public void testOrderWithSwingInvokeLater4() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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

  public void testDeadLock() throws InterruptedException {
    final Object lock = new Object();
    final boolean[] started = { false };
    final Thread thread = new Thread("later invokator test") {
      @Override
      public void run() {
        synchronized (lock) {
          started[0] = true;
          ApplicationManager.getApplication().invokeLater(new MyRunnable("1"), ModalityState.NON_MODAL);
          lock.notifyAll();
        }
      }
    };

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      synchronized (this) {
        blockSwingThread();
        ApplicationManager.getApplication().invokeLater(() -> {
          thread.start();
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
    thread.join();
  }

  static void flushSwingQueue() {

    try {
      Thread.sleep(10);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    else {
      UIUtil.pump();
    }
    /*
    final AtomicBoolean hasEventsInQueue = new AtomicBoolean(true);
    while (hasEventsInQueue.get()) {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          hasEventsInQueue.set(Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent() != null);
        }
      });
    }
    */

    //some weird things like MyFireIdleRequest may still sneak in
    //assertEquals(null, Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent());
  }

  private void blockSwingThread() {
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

    MyRunnable(String id) {
      myId = id;
    }

    @Override
    public void run() {
      //System.out.println("executed: " + myId);
      synchronized(myOrder) {
        myOrder.add(myId);
      }
    }

    public String toString() {
      return "myrun " + myId;
    }
  }

  private static class Lock implements Runnable {
    private final Object myLock;

    public Lock(Object lock) {
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
      LaterInvocator.invokeLater(new MyRunnable("1"), window2State);

      LaterInvocator.enterModal(myWindow1);
      flushSwingQueue();
      checkOrder(0);

      LaterInvocator.leaveModal(myWindow1);
      flushSwingQueue();
      checkOrder(1);

      LaterInvocator.invokeLater(new MyRunnable("2"), window2State);
      flushSwingQueue();
      checkOrder(2);
    });
  }

  public void testModalityStateStaysTheSameBetweenInvocations() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
    Object modal1 = new Object();
    Object modal2 = new Object();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
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
    LoggedErrorProcessor.getInstance().disableStderrDumping(getTestRootDisposable());
    Future<ModalityState> future = ApplicationManager.getApplication().executeOnPooledThread(() -> ModalityState.current());
    try {
      future.get(1000, TimeUnit.MILLISECONDS);
      fail("should fail");
    }
    catch (ExecutionException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("Access is allowed from event dispatch thread only"));
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

  public void testDifferentStatesAreNotEqualAfterGc() {
    ModalityStateEx state1 = new ModalityStateEx("common", new String("foo"));
    ModalityStateEx state2 = new ModalityStateEx("common", new String("bar"));
    
    assertFalse(state1.equals(state2));

    GCUtil.tryGcSoftlyReachableObjects();
    assertFalse(state1.equals(state2));
  }

  public void testStateForComponentIdentity() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      JPanel panel = new JPanel();
      myWindow1.add(panel);
      LaterInvocator.enterModal(myWindow1);

      ModalityState state1 = ModalityState.stateForComponent(myWindow1);
      assertSame(state1, ModalityState.stateForComponent(myWindow1));
      assertSame(state1, ModalityState.stateForComponent(panel));

      LaterInvocator.enterModal(myWindow1);
      assertSame(state1, ModalityState.stateForComponent(panel));
      assertNotSame(state1, ModalityState.stateForComponent(myWindow2));
    });
  }

  public void testProgressModality() {
    ApplicationManager.getApplication().invokeAndWait(() -> ProgressManager.getInstance().run(new Task.Modal(myProject, "", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        ModalityState state = indicator.getModalityState();
        assertSame(state, ModalityState.defaultModalityState());
        ApplicationManager.getApplication().invokeAndWait(() -> assertSame(state, ModalityState.defaultModalityState()), state);
      }
    }));
  }

  public void testAppVsSwingPerformance() {
    int N = 1_000_000;

    AtomicInteger counter = new AtomicInteger();
    Runnable r = () -> counter.incrementAndGet();

    PlatformTestUtil.startPerformanceTest("Swing invokeLater", 13_000, () -> {
      for (int i = 0; i < N; i++) {
        SwingUtilities.invokeLater(r);
      }
      SwingUtilities.invokeAndWait(EmptyRunnable.getInstance());
      assertEquals(N, counter.getAndSet(0));
    }).assertTiming();

    PlatformTestUtil.startPerformanceTest("Application invokeLater", 800, () -> {
      for (int i = 0; i < N; i++) {
        ApplicationManager.getApplication().invokeLater(r);
      }
      ApplicationManager.getApplication().invokeAndWait(EmptyRunnable.getInstance());
      assertEquals(N, counter.getAndSet(0));
    }).assertTiming();

    PlatformTestUtil.startPerformanceTest("Application invokeLater in modal context", 800, () -> {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> LaterInvocator.enterModal(myWindow1));
      for (int i = 0; i < N; i++) {
        ApplicationManager.getApplication().invokeLater(r);
      }
      assertEquals(0, counter.get());
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> LaterInvocator.leaveModal(myWindow1));
      ApplicationManager.getApplication().invokeAndWait(EmptyRunnable.getInstance());
      assertEquals(N, counter.getAndSet(0));
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
}
