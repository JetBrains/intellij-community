// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.messages.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.testFramework.ServiceContainerUtil.createSimpleMessageBusOwner;

public class MessageBusTest extends LightPlatformTestCase implements MessageBusOwner {
  private MessageBusImpl myBus;
  private List<String> myLog;
  private Disposable myParentDisposable = Disposer.newDisposable();

  @NotNull
  @Override
  public Object createListener(@NotNull ListenerDescriptor descriptor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDisposed() {
    return Disposer.isDisposed(myParentDisposable);
  }

  public interface T1Listener {
    void t11();
    void t12();
  }

  public interface T2Listener {
    void t21();
    void t22();
  }

  private static final Topic<T1Listener> TOPIC1 = new Topic<>("T1", T1Listener.class);
  private static final Topic<T2Listener> TOPIC2 = new Topic<>("T2", T2Listener.class);
  private static final Topic<Runnable> RUNNABLE_TOPIC = new Topic<>("runnableTopic", Runnable.class);

  private class T1Handler implements T1Listener {
    private final String id;

    T1Handler(final String id) {
      this.id = id;
    }

    @Override
    public void t11() {
      myLog.add(id + ":" + "t11");
    }

    @Override
    public void t12() {
      myLog.add(id + ":" + "t12");
    }
  }
  private class T2Handler implements T2Listener {
    private final String id;

    T2Handler(final String id) {
      this.id = id;
    }

    @Override
    public void t21() {
      myLog.add(id + ":" + "t21");
    }

    @Override
    public void t22() {
      myLog.add(id + ":" + "t22");
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBus = (MessageBusImpl)MessageBusFactory.newMessageBus(this);
    Disposer.register(myParentDisposable, myBus);
    myLog = new ArrayList<>();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myParentDisposable);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myBus = null;
      myParentDisposable = null;
      super.tearDown();
    }
  }

  public void testNoListenersSubscribed() {
    myBus.syncPublisher(TOPIC1).t11();
    assertEvents();
  }

  public void testSingleMessage() {
    final MessageBusConnection connection = myBus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c"));
    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c:t11");
  }

  public void testSingleMessageToTwoConnections() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(TOPIC1, new T1Handler("c1"));

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(TOPIC1, new T1Handler("c2"));

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t11", "c2:t11");
  }

  public void testSameTopicInOneConnection() {
    MessageBusConnection connection = myBus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c1"));
    connection.subscribe(TOPIC1, new T1Handler("c2"));

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t11", "c2:t11");
  }

  public void testSameTopicInOneConnectionForList() {
    MessageBusConnectionImpl connection = myBus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c1"));
    connection.subscribe(TOPIC1, Arrays.asList(new T1Handler("c2"), new T1Handler("c3")));

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t11", "c2:t11", "c3:t11");
  }

  public void testTwoMessagesWithSingleSubscription() {
    final MessageBusConnection connection = myBus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c"));
    myBus.syncPublisher(TOPIC1).t11();
    myBus.syncPublisher(TOPIC1).t12();

    assertEvents("c:t11", "c:t12");
  }

  public void testTwoMessagesWithDoubleSubscription() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(TOPIC1, new T1Handler("c1"));

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(TOPIC1, new T1Handler("c2"));

    myBus.syncPublisher(TOPIC1).t11();
    myBus.syncPublisher(TOPIC1).t12();

    assertEvents("c1:t11", "c2:t11", "c1:t12", "c2:t12");
  }

  public void testEventFiresAnotherEvent() {
    myBus.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        myLog.add("inside:t11");
        myBus.syncPublisher(TOPIC2).t21();
        myLog.add("inside:t11:done");
      }

      @Override
      public void t12() {
        myLog.add("c1:t12");
      }
    });

    final MessageBusConnection conn = myBus.connect();
    conn.subscribe(TOPIC1, new T1Handler("handler1"));
    conn.subscribe(TOPIC2, new T2Handler("handler2"));

    myBus.syncPublisher(TOPIC1).t12();
    assertEvents("c1:t12", "handler1:t12");

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t12\n" +
                 "handler1:t12\n" +
                 "inside:t11\n" +
                 "handler1:t11\n" +
                 "handler2:t21\n" +
                 "inside:t11:done");
  }

  public void testConnectionTerminatedInDispatch() {
    final MessageBusConnection conn1 = myBus.connect();
    conn1.subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        conn1.disconnect();
        myLog.add("inside:t11");
        myBus.syncPublisher(TOPIC2).t21();
        myLog.add("inside:t11:done");
      }

      @Override
      public void t12() {
        myLog.add("inside:t12");
      }
    });
    conn1.subscribe(TOPIC2, new T2Handler("C1T2Handler"));

    final MessageBusConnection conn2 = myBus.connect();
    conn2.subscribe(TOPIC1, new T1Handler("C2T1Handler"));
    conn2.subscribe(TOPIC2, new T2Handler("C2T2Handler"));

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("inside:t11",
                 "C2T1Handler:t11",
                 "C2T2Handler:t21",
                 "inside:t11:done");
    myBus.syncPublisher(TOPIC1).t12();

    assertEvents("inside:t11",
                 "C2T1Handler:t11",
                 "C2T2Handler:t21",
                 "inside:t11:done",
                 "C2T1Handler:t12");
  }

  public void testMessageDeliveredDespitePCE() {
    final MessageBusConnection conn1 = myBus.connect();
    conn1.subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        myLog.add("pce");
        throw new ProcessCanceledException();
      }

      @Override
      public void t12() {
        throw new UnsupportedOperationException();
      }
    });

    final MessageBusConnection conn2 = myBus.connect();
    conn2.subscribe(TOPIC1, new T1Handler("handler2"));

    try {
      myBus.syncPublisher(TOPIC1).t11();
      fail("PCE expected");
    }
    catch (ProcessCanceledException ignored) {
    }
    assertEvents("pce", "handler2:t11");
  }

  public void testPostingPerformanceWithLowListenerDensityInHierarchy() {
    //simulating million fileWithNoDocumentChanged events on refresh in a thousand-module project
    MessageBusImpl childBus = new MessageBusImpl(this, myBus);
    childBus.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
      }

      @Override
      public void t12() {
      }
    });
    for (int i = 0; i < 1000; i++) {
      new MessageBusImpl(this, childBus);
    }

    PlatformTestUtil.assertTiming("Too long", 3000, () -> {
      T1Listener publisher = myBus.syncPublisher(TOPIC1);
      for (int i = 0; i < 1000000; i++) {
        publisher.t11();
      }
    });
  }

  public void testManyChildrenCreationDeletionPerformance() {
    PlatformTestUtil.startPerformanceTest("Child bus creation/deletion", 1_000, () -> {
      List<MessageBusImpl> children = new ArrayList<>();
      int count = 10_000;
      for (int i = 0; i < count; i++) {
        children.add(new MessageBusImpl(this, myBus));
      }
      // reverse iteration to avoid O(n^2) while deleting from list's beginning
      for (int i = count - 1; i >= 0; i--) {
        Disposer.dispose(children.get(i));
      }
    }).assertTiming();
  }

  public void testStress() throws Throwable {
    final int threadsNumber = 10;
    final AtomicReference<Throwable> exception = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(threadsNumber);
    MessageBusImpl parentBus = (MessageBusImpl)MessageBusFactory.newMessageBus(createSimpleMessageBusOwner("parent"));
    Disposer.register(myParentDisposable, parentBus);
    List<Future<?>> threads = new ArrayList<>();
    final int iterationsNumber = 100;
    for (int i = 0; i < threadsNumber; i++) {
      Future<?> thread = ApplicationManager.getApplication().executeOnPooledThread(() -> {
          try {
            int remains = iterationsNumber;
            while (remains-- > 0) {
              if (exception.get() != null) {
                break;
              }
              new MessageBusImpl(createSimpleMessageBusOwner(String.format("child-%s-%s", Thread.currentThread().getName(), remains)), parentBus);
            }
          }
          catch (Throwable e) {
            exception.set(e);
          }
          finally {
            latch.countDown();
          }
        });
      threads.add(thread);
    }
    latch.await();
    final Throwable e = exception.get();
    if (e != null) {
      throw e;
    }
    for (Future<?> thread : threads) {
      thread.get();
    }
  }


  private void assertEvents(String... expected) {
    String joinExpected = StringUtil.join(expected, "\n");
    String joinActual = StringUtil.join(myLog, "\n");

    assertEquals("events mismatch", joinExpected, joinActual);
  }

  public void testHasUndeliveredEvents() {
    assertFalse(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC));
    assertFalse(myBus.hasUndeliveredEvents(TOPIC2));

    myBus.connect().subscribe(RUNNABLE_TOPIC, () -> {
      assertTrue(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC));
      assertFalse(myBus.hasUndeliveredEvents(TOPIC2));
    });
    myBus.connect().subscribe(RUNNABLE_TOPIC, () -> {
      assertFalse(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC));
      assertFalse(myBus.hasUndeliveredEvents(TOPIC2));
    });
    myBus.syncPublisher(RUNNABLE_TOPIC).run();
  }

  public void testHasUndeliveredEventsInChildBys() {
    MessageBusImpl childBus = new MessageBusImpl(this, myBus);
    myBus.connect().subscribe(RUNNABLE_TOPIC, () -> assertTrue(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC)));
    childBus.connect().subscribe(RUNNABLE_TOPIC, () -> assertFalse(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC)));
    myBus.syncPublisher(RUNNABLE_TOPIC).run();
  }

  public void testDisposingBusInsideEvent() {
    MessageBusImpl child = new MessageBusImpl(this, myBus);
    myBus.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        myLog.add("root 11");
        myBus.syncPublisher(TOPIC1).t12();
        Disposer.dispose(child);
      }

      @Override
      public void t12() {
        myLog.add("root 12");
      }
    });
    child.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        myLog.add("child 11");
      }

      @Override
      public void t12() {
        myLog.add("child 12");
      }
    });
    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("root 11", "child 11", "root 12", "child 12");
  }

  public void testTwoHandlersBothDisconnecting() {
    Disposable disposable = Disposer.newDisposable();
    for (int i = 0; i < 2; i++) {
      myBus.connect(disposable).subscribe(RUNNABLE_TOPIC, () -> Disposer.dispose(disposable));
    }
    myBus.syncPublisher(RUNNABLE_TOPIC).run();
    assertTrue(Disposer.isDisposed(disposable));
  }

  public void testMessageBusOwnerIsCorrect() {
    checkLevel(ApplicationManager.getApplication(), "Application");
    checkLevel(getProject(), "Project");
    checkLevel(getModule(), "Module");
  }

  private static void checkLevel(ComponentManager component, String level) {
    MessageBusImpl bus = (MessageBusImpl)component.getPicoContainer().getComponentInstanceOfType(MessageBus.class);
    assertTrue(bus.getOwner().contains(level));
    MessageBusImpl bus1 = (MessageBusImpl)component.getMessageBus();
    assertSame(bus, bus1);
  }
}
