// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.*;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.intellij.testFramework.ServiceContainerUtil.createSimpleMessageBusOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

@SuppressWarnings("TypeMayBeWeakened")
public class MessageBusTest implements MessageBusOwner {
  private CompositeMessageBus myBus;
  private final List<String> myLog = new ArrayList<>();
  private Disposable myParentDisposable = Disposer.newDisposable();

  @Override
  public @NotNull Object createListener(@NotNull ListenerDescriptor descriptor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDisposed() {
    return Disposer.isDisposed(myParentDisposable);
  }

  @Override
  public boolean isParentLazyListenersIgnored() {
    return true;
  }

  public interface T1Listener {
    void t11();
    void t12();
  }

  public interface T2Listener {
    void t21();
    void t22();
  }

  private static final Topic<T1Listener> TOPIC1 = new Topic<>("T1", T1Listener.class, Topic.BroadcastDirection.TO_CHILDREN);
  private static final Topic<T2Listener> TOPIC2 = new Topic<>("T2", T2Listener.class, Topic.BroadcastDirection.TO_CHILDREN);
  private static final Topic<Runnable> RUNNABLE_TOPIC = new Topic<>(Runnable.class, Topic.BroadcastDirection.TO_CHILDREN);

  private final class T1Handler implements T1Listener {
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

  private final class T2Handler implements T2Listener {
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

  @Before
  public void setUp() throws Exception {
    myBus = new MessageBusImpl.RootBus(this);
    Disposer.register(myParentDisposable, myBus);
  }

  @After
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myParentDisposable);
    }
    finally {
      myBus = null;
      myParentDisposable = null;
    }
  }

  @Test
  public void testNoListenersSubscribed() {
    myBus.syncPublisher(TOPIC1).t11();
    assertEvents();
  }

  @Test
  public void testSingleMessage() {
    final MessageBusConnection connection = myBus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c"));
    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c:t11");
  }

  @Test
  public void testSingleMessageToTwoConnections() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(TOPIC1, new T1Handler("c1"));

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(TOPIC1, new T1Handler("c2"));

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t11", "c2:t11");
  }

  @Test
  public void testSameTopicInOneConnection() {
    MessageBusConnection connection = myBus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c1"));
    connection.subscribe(TOPIC1, new T1Handler("c2"));

    myBus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t11", "c2:t11");
  }

  @Test
  public void testTwoMessagesWithSingleSubscription() {
    final MessageBusConnection connection = myBus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c"));
    myBus.syncPublisher(TOPIC1).t11();
    myBus.syncPublisher(TOPIC1).t12();

    assertEvents("c:t11", "c:t12");
  }

  @Test
  public void testTwoMessagesWithDoubleSubscription() {
    final MessageBusConnection c1 = myBus.connect();
    c1.subscribe(TOPIC1, new T1Handler("c1"));

    final MessageBusConnection c2 = myBus.connect();
    c2.subscribe(TOPIC1, new T1Handler("c2"));

    myBus.syncPublisher(TOPIC1).t11();
    myBus.syncPublisher(TOPIC1).t12();

    assertEvents("c1:t11", "c2:t11", "c1:t12", "c2:t12");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testPostingPerformanceWithLowListenerDensityInHierarchy() {
    //simulating million fileWithNoDocumentChanged events on refresh in a thousand-module project
    CompositeMessageBus childBus = new CompositeMessageBus(this, myBus);
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

  @Test
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

  @Test
  public void testStress() throws Throwable {
    final int threadsNumber = 10;
    final AtomicReference<Throwable> exception = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(threadsNumber);
    CompositeMessageBus parentBus = new MessageBusImpl.RootBus(createSimpleMessageBusOwner("parent"));
    Disposer.register(myParentDisposable, parentBus);
    List<Future<?>> threads = new ArrayList<>();
    final int iterationsNumber = 100;
    for (int i = 0; i < threadsNumber; i++) {
      Future<?> thread = AppExecutorUtil.getAppScheduledExecutorService().submit(() -> {
        try {
          int remains = iterationsNumber;
          while (remains-- > 0) {
            if (exception.get() != null) {
              break;
            }
            new CompositeMessageBus(createSimpleMessageBusOwner(String.format("child-%s-%s", Thread.currentThread().getName(), remains)), parentBus);
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
    assertThat(String.join("\n", myLog)).isEqualTo(String.join("\n", expected));
  }

  @Test
  public void testHasUndeliveredEvents() {
    assertFalse(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC));
    assertFalse(myBus.hasUndeliveredEvents(TOPIC2));

    myBus.connect().subscribe(RUNNABLE_TOPIC, () -> {
      assertThat(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isTrue();
      assertThat(myBus.hasUndeliveredEvents(TOPIC2)).isFalse();
    });
    myBus.connect().subscribe(RUNNABLE_TOPIC, () -> {
      assertFalse(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC));
      assertFalse(myBus.hasUndeliveredEvents(TOPIC2));
    });
    myBus.syncPublisher(RUNNABLE_TOPIC).run();
  }

  @Test
  public void testHasUndeliveredEventsInChildBys() {
    MessageBusImpl childBus = new MessageBusImpl(this, myBus);
    myBus.connect().subscribe(RUNNABLE_TOPIC, () -> assertThat(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isTrue());
    childBus.connect().subscribe(RUNNABLE_TOPIC, () -> assertFalse(myBus.hasUndeliveredEvents(RUNNABLE_TOPIC)));
    myBus.syncPublisher(RUNNABLE_TOPIC).run();
  }

  @Test
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

  @Test
  public void testTwoHandlersBothDisconnecting() {
    Disposable disposable = Disposer.newDisposable();
    for (int i = 0; i < 2; i++) {
      myBus.connect(disposable).subscribe(RUNNABLE_TOPIC, () -> Disposer.dispose(disposable));
    }
    myBus.syncPublisher(RUNNABLE_TOPIC).run();
    assertThat(Disposer.isDisposed(disposable)).isTrue();
  }

  @Test
  public void subscriberCacheClearedOnChildBusDispose() {
    // ensure that subscriber cache is cleared on child bus dispose
    MessageBusImpl child = new MessageBusImpl(this, myBus);
    Ref<Boolean> isDisposed = new Ref<>(false);
    child.connect().subscribe(RUNNABLE_TOPIC, () -> {
      if (isDisposed.get()) {
        throw new IllegalStateException("already disposed");
      }
    });
    myBus.syncPublisher(RUNNABLE_TOPIC).run();
    Disposer.dispose(child);
    isDisposed.set(true);
    myBus.syncPublisher(RUNNABLE_TOPIC).run();
  }

  private static final Topic<Runnable> TO_PARENT_TOPIC = new Topic<>(Runnable.class, Topic.BroadcastDirection.TO_PARENT);

  @Test
  public void subscriberCacheClearedOnConnectionToParentBusForChildBusTopic() {
    // ensure that subscriber cache is cleared on connection to app level bus for topic that published to project level bus with TO_PARENT direction.
    MessageBus child = new CompositeMessageBus(this, myBus);
    // call to compute cache
    child.syncPublisher(TO_PARENT_TOPIC).run();

    Ref<Boolean> isCalled = new Ref<>(false);
    myBus.connect().subscribe(TO_PARENT_TOPIC, () -> {
      isCalled.set(true);
    });
    child.syncPublisher(TO_PARENT_TOPIC).run();
    assertThat(isCalled.get()).isTrue();
  }

  @Test
  public void subscriberCacheClearedOnConnectionToChildrenBusFoRootBusTopic() {
    // child must be created before to ensure that cache is not cleared on a new child
    MessageBus child = new CompositeMessageBus(this, myBus);
    // call to compute cache
    myBus.syncPublisher(RUNNABLE_TOPIC).run();

    Ref<Boolean> isCalled = new Ref<>(false);
    child.connect().subscribe(RUNNABLE_TOPIC, () -> {
      isCalled.set(true);
    });
    myBus.syncPublisher(RUNNABLE_TOPIC).run();
    assertThat(isCalled.get()).isTrue();
  }

  @Test
  public void disconnectOnPluginUnload() {
    // child must be created before to ensure that cache is not cleared on a new child
    MessageBus child = new CompositeMessageBus(this, myBus);
    // call to compute cache
    myBus.syncPublisher(RUNNABLE_TOPIC).run();

    AtomicInteger callCounter = new AtomicInteger();
    Runnable listener = () -> {
      callCounter.incrementAndGet();
    };

    // add twice
    child.connect().subscribe(RUNNABLE_TOPIC, listener);
    child.connect().subscribe(RUNNABLE_TOPIC, listener);

    myBus.disconnectPluginConnections(new Predicate<Class<?>>() {
      boolean isRemoved;
      @Override
      public boolean test(Class<?> aClass) {
        // remove first one
        if (isRemoved) {
          return false;
        }
        isRemoved = true;
        return true;
      }
    });

    myBus.syncPublisher(RUNNABLE_TOPIC).run();
    assertThat(callCounter.get()).isEqualTo(1);
  }

  @Test
  public void removeEmptyConnectionsRecursively() throws Throwable {
    int threadsNumber = 10;
    AtomicReference<Throwable> exception = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(threadsNumber);
    List<Future<?>> threads = new ArrayList<>();
    AtomicInteger eventCounter = new AtomicInteger();
    for (int i = 0; i < threadsNumber; i++) {
      Future<?> thread = AppExecutorUtil.getAppScheduledExecutorService().submit(() -> {
        try {
          MessageBusConnection connection = myBus.connect();
          ((MessageBusImpl.RootBus)myBus)._removeEmptyConnectionsRecursively();
          connection.subscribe(RUNNABLE_TOPIC, () -> eventCounter.incrementAndGet());
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

    Throwable e = exception.get();
    if (e != null) {
      throw e;
    }
    for (Future<?> thread : threads) {
      thread.get();
    }

    myBus.syncPublisher(RUNNABLE_TOPIC).run();
    assertThat(eventCounter.get()).isEqualTo(threadsNumber);
  }

  @Test
  public void disconnectOnDisposeForImmediateDeliveryTopic() {
    Topic<Runnable> TOPIC = new Topic<>(Runnable.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

    Disposable disposable = Disposer.newDisposable();
    MessageBusConnectionImpl connection = myBus.connect(disposable);
    connection.subscribe(TOPIC, () -> {
      fail("must be not called");
    });
    Disposer.dispose(disposable);
    myBus.syncPublisher(TOPIC).run();
  }
}
