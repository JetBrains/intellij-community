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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Conditions;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.SkipInHeadlessEnvironment;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

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
  protected void setUp() throws Exception {
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
    final Exception[] exception = {null};
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        super.setUp();
        final Object[] modalEntities = LaterInvocator.getCurrentModalEntities();
        if (modalEntities.length > 0) {
          LOG.error(
            "Expect no modal entries. Probably some of the previous tests didn't left their entries. Top entry is: " + modalEntities[0]);
        }
      }
      catch (Exception e) {
        exception[0] = e;
      }
    });
    if (exception[0] != null) throw exception[0];
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> TestCase.assertFalse("Can't run test", LaterInvocator.isInModalContext()));

    flushSwingQueue();
  }

  @Override
  protected void runBareRunnable(ThrowableRunnable<Throwable> runnable) throws Throwable {
    runnable.run();
  }

  @Override
  protected void tearDown() throws Exception {
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

    final Exception[] exception = {null};
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          LaterInvocatorTest.super.tearDown();
        }
        catch (Exception e) {
          exception[0] = e;
        }
      }

      public String toString() {
        return "super teardown";
      }
    });
    if (exception[0] != null) throw exception[0];
  }

  public void testReorder() throws InterruptedException, InvocationTargetException {
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

  public void testTrueReorder() throws InvocationTargetException, InterruptedException {
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

  public void testEverInvoked() throws InvocationTargetException, InterruptedException {
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

  public void testDoesNotInvokeWhenModal() throws InvocationTargetException, InterruptedException {
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

  public void testStress() throws Exception {
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


  public void testOrderWithSwingInvokeLater2() throws InterruptedException, InvocationTargetException {
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

  public void testOrderWithSwingInvokeLater3() throws InterruptedException, InvocationTargetException {
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

  public void testExpired() throws Exception {
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

  public void testOrderWithSwingInvokeLater4() throws InterruptedException, InvocationTargetException {
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

  public void testDeadLock() throws InvocationTargetException, InterruptedException {
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

  private static void flushSwingQueue() {

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

    public MyRunnable(String id) {
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
}
