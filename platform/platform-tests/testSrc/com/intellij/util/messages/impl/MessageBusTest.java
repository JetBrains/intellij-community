// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.*;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.intellij.testFramework.ServiceContainerUtil.createSimpleMessageBusOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

@SuppressWarnings("TypeMayBeWeakened")
public class MessageBusTest implements MessageBusOwner {
  private CompositeMessageBus bus;
  private final List<String> log = new ArrayList<>();
  private CheckedDisposable parentDisposable = Disposer.newCheckedDisposable();

  @Override
  public @NotNull Object createListener(@NotNull ListenerDescriptor descriptor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDisposed() {
    return parentDisposable.isDisposed();
  }

  @Override
  public boolean isParentLazyListenersIgnored() {
    return true;
  }

  public interface T1Listener {
    void t11();
    default void t12() {
    }
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
      log.add(id + ":" + "t11");
    }

    @Override
    public void t12() {
      log.add(id + ":" + "t12");
    }
  }

  private final class T2Handler implements T2Listener {
    private final String id;

    T2Handler(final String id) {
      this.id = id;
    }

    @Override
    public void t21() {
      log.add(id + ":" + "t21");
    }

    @Override
    public void t22() {
      log.add(id + ":" + "t22");
    }
  }

  @Before
  public void setUp() throws Exception {
    bus = new MessageBusImpl.RootBus(this);
    Disposer.register(parentDisposable, bus);
  }

  @After
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(parentDisposable);
    }
    finally {
      bus = null;
      parentDisposable = null;
    }
  }

  @Test
  public void noListenersSubscribed() {
    bus.syncPublisher(TOPIC1).t11();
    assertEvents();
  }

  @Test
  public void singleMessage() {
    final MessageBusConnection connection = bus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c"));
    bus.syncPublisher(TOPIC1).t11();
    assertEvents("c:t11");
  }

  @Test
  public void singleMessageToTwoConnections() {
    final MessageBusConnection c1 = bus.connect();
    c1.subscribe(TOPIC1, new T1Handler("c1"));

    final MessageBusConnection c2 = bus.connect();
    c2.subscribe(TOPIC1, new T1Handler("c2"));

    bus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t11", "c2:t11");
  }

  @Test
  public void sameTopicInOneConnection() {
    MessageBusConnection connection = bus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c1"));
    connection.subscribe(TOPIC1, new T1Handler("c2"));

    bus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t11", "c2:t11");
  }

  @Test
  public void twoMessagesWithSingleSubscription() {
    final MessageBusConnection connection = bus.connect();
    connection.subscribe(TOPIC1, new T1Handler("c"));
    bus.syncPublisher(TOPIC1).t11();
    bus.syncPublisher(TOPIC1).t12();

    assertEvents("c:t11", "c:t12");
  }

  @Test
  public void twoMessagesWithDoubleSubscription() {
    final MessageBusConnection c1 = bus.connect();
    c1.subscribe(TOPIC1, new T1Handler("c1"));

    final MessageBusConnection c2 = bus.connect();
    c2.subscribe(TOPIC1, new T1Handler("c2"));

    bus.syncPublisher(TOPIC1).t11();
    bus.syncPublisher(TOPIC1).t12();

    assertEvents("c1:t11", "c2:t11", "c1:t12", "c2:t12");
  }

  @Test
  public void eventFiresAnotherEvent() {
    bus.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        log.add("inside:t11");
        bus.syncPublisher(TOPIC2).t21();
        log.add("inside:t11:done");
      }

      @Override
      public void t12() {
        log.add("c1:t12");
      }
    });

    final MessageBusConnection conn = bus.connect();
    conn.subscribe(TOPIC1, new T1Handler("handler1"));
    conn.subscribe(TOPIC2, new T2Handler("handler2"));

    bus.syncPublisher(TOPIC1).t12();
    assertEvents("c1:t12", "handler1:t12");

    bus.syncPublisher(TOPIC1).t11();
    assertEvents("c1:t12\n" +
                 "handler1:t12\n" +
                 "inside:t11\n" +
                 "handler1:t11\n" +
                 "handler2:t21\n" +
                 "inside:t11:done");
  }

  @Test
  public void connectionTerminatedInDispatch() {
    final MessageBusConnection conn1 = bus.connect();
    conn1.subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        conn1.disconnect();
        log.add("inside:t11");
        bus.syncPublisher(TOPIC2).t21();
        log.add("inside:t11:done");
      }

      @Override
      public void t12() {
        log.add("inside:t12");
      }
    });
    conn1.subscribe(TOPIC2, new T2Handler("C1T2Handler"));

    final MessageBusConnection conn2 = bus.connect();
    conn2.subscribe(TOPIC1, new T1Handler("C2T1Handler"));
    conn2.subscribe(TOPIC2, new T2Handler("C2T2Handler"));

    bus.syncPublisher(TOPIC1).t11();
    assertEvents("inside:t11",
                 "C2T1Handler:t11",
                 "C2T2Handler:t21",
                 "inside:t11:done");
    bus.syncPublisher(TOPIC1).t12();

    assertEvents("inside:t11",
                 "C2T1Handler:t11",
                 "C2T2Handler:t21",
                 "inside:t11:done",
                 "C2T1Handler:t12");
  }

  @Test
  public void messageDeliveredDespitePce() {
    final MessageBusConnection conn1 = bus.connect();
    conn1.subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        log.add("pce");
        throw new ProcessCanceledException();
      }

      @Override
      public void t12() {
        throw new UnsupportedOperationException();
      }
    });

    final MessageBusConnection conn2 = bus.connect();
    conn2.subscribe(TOPIC1, new T1Handler("handler2"));

    try {
      bus.syncPublisher(TOPIC1).t11();
      Assertions.fail("PCE expected");
    }
    catch (ProcessCanceledException ignored) {
    }
    assertEvents("pce", "handler2:t11");
  }

  @Test
  public void postingPerformanceWithLowListenerDensityInHierarchy() {
    // simulating a million fileWithNoDocumentChanged events on refresh in a thousand-module project
    CompositeMessageBus childBus = new CompositeMessageBus(this, bus);
    childBus.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
      }

      @Override
      public void t12() {
      }
    });
    for (int i = 0; i < 1_000; i++) {
      new MessageBusImpl(this, childBus);
    }

    PlatformTestUtil.assertTiming("Too long", 3_000, () -> {
      T1Listener publisher = bus.syncPublisher(TOPIC1);
      for (int i = 0; i < 1_000_000; i++) {
        publisher.t11();
      }
    });
  }

  @Test
  public void manyChildrenCreationDeletionPerformance() {
    PlatformTestUtil.startPerformanceTest("Child bus creation/deletion", 1_000, () -> {
      List<MessageBusImpl> children = new ArrayList<>();
      int count = 10_000;
      for (int i = 0; i < count; i++) {
        children.add(new MessageBusImpl(this, bus));
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
    Disposer.register(parentDisposable, parentBus);
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
    assertThat(String.join("\n", log)).isEqualTo(String.join("\n", expected));
  }

  @Test
  public void hasUndeliveredEvents() {
    assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isFalse();
    assertThat(bus.hasUndeliveredEvents(TOPIC2)).isFalse();

    bus.connect().subscribe(RUNNABLE_TOPIC, () -> {
      assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isTrue();
      assertThat(bus.hasUndeliveredEvents(TOPIC2)).isFalse();
    });
    bus.connect().subscribe(RUNNABLE_TOPIC, () -> {
      assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isFalse();
      assertThat(bus.hasUndeliveredEvents(TOPIC2)).isFalse();
    });
    bus.syncPublisher(RUNNABLE_TOPIC).run();
  }

  @Test
  public void hasUndeliveredEventsInChildBus() {
    MessageBusImpl childBus = new MessageBusImpl(this, bus);
    bus.connect().subscribe(RUNNABLE_TOPIC, () -> assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isTrue());
    childBus.connect().subscribe(RUNNABLE_TOPIC, () -> assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isFalse());
    bus.syncPublisher(RUNNABLE_TOPIC).run();
  }

  @Test
  public void scheduleEmptyConnectionRemoval() throws Exception {
    // this counter serves as a proxy to number of iterations inside the compaction task loop in root bus:
    AtomicInteger compactionIterationCount = new AtomicInteger();
    new MessageBusImpl(this, bus) {
      @Override
      void removeEmptyConnectionsRecursively() {
        compactionIterationCount.incrementAndGet();
      }
    };

    // this child bus will block removal so that we can run asserts:
    Semaphore slowRemovalStarted = new Semaphore(1);
    slowRemovalStarted.acquire();
    CountDownLatch slowRemovalCanFinish = new CountDownLatch(1);
    new MessageBusImpl(this, bus) {
      @Override
      void removeEmptyConnectionsRecursively() {
        slowRemovalStarted.release(); // signal that slow removal has started
        try {
          slowRemovalCanFinish.await(); // block until the test allows us to finish
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    };

    // scheduleEmptyConnectionRemoving is not invoked immediately, call it until we detect that slow removal has started:
    int callCountToTriggerRemoval = 0;
    while (true) {
      ((MessageBusImpl.RootBus)bus).scheduleEmptyConnectionRemoving();
      if (slowRemovalStarted.tryAcquire()) {
        break;
      }
      callCountToTriggerRemoval++;
    }

    assertThat(compactionIterationCount.get()).isEqualTo(1);

    // now compaction task is blocked in slow remove, schedule more compaction requests:
    for (int i = 0; i < 10 * callCountToTriggerRemoval; i++) {
      ((MessageBusImpl.RootBus)bus).scheduleEmptyConnectionRemoving();
    }

    // no new compaction task was started:
    assertThat(compactionIterationCount.get()).isEqualTo(1);

    // allow slow removal to finish:
    slowRemovalCanFinish.countDown();

    // wait until slow removal will finish one more time: we requested removal several times,
    // so we need one more loop iteration to handle pending removal requests
    if (!slowRemovalStarted.tryAcquire(30, TimeUnit.SECONDS)) {
      fail("Compaction requests were not served in 30 seconds");
    }

    Thread.sleep(50); // give compaction task a chance to do more iterations

    assertThat(compactionIterationCount.get()).isEqualTo(2); // only one additional iteration is needed to handle pending removal requests
  }

  @Test
  public void disposingBusInsideEvent() {
    MessageBusImpl child = new MessageBusImpl(this, bus);
    bus.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        log.add("root 11");
        bus.syncPublisher(TOPIC1).t12();
        Disposer.dispose(child);
      }

      @Override
      public void t12() {
        log.add("root 12");
      }
    });
    child.connect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        log.add("child 11");
      }

      @Override
      public void t12() {
        log.add("child 12");
      }
    });
    bus.syncPublisher(TOPIC1).t11();
    assertEvents("root 11", "child 11", "root 12", "child 12");
  }

  @Test
  public void twoHandlersBothDisconnecting() {
    CheckedDisposable disposable = Disposer.newCheckedDisposable();
    for (int i = 0; i < 2; i++) {
      bus.connect(disposable).subscribe(RUNNABLE_TOPIC, () -> Disposer.dispose(disposable));
    }
    bus.syncPublisher(RUNNABLE_TOPIC).run();
    assertThat(disposable.isDisposed()).isTrue();
  }

  @Test
  public void subscriberCacheClearedOnChildBusDispose() {
    // ensure that subscriber cache is cleared on child bus dispose
    MessageBusImpl child = new MessageBusImpl(this, bus);
    Ref<Boolean> isDisposed = new Ref<>(false);
    child.connect().subscribe(RUNNABLE_TOPIC, () -> {
      if (isDisposed.get()) {
        throw new IllegalStateException("already disposed");
      }
    });
    bus.syncPublisher(RUNNABLE_TOPIC).run();
    Disposer.dispose(child);
    isDisposed.set(true);
    bus.syncPublisher(RUNNABLE_TOPIC).run();
  }

  @Test
  public void publishToAnotherBus() {
    MessageBusImpl childBus = new CompositeMessageBus(this, bus);
    Disposer.register(parentDisposable, childBus);

    AtomicInteger counter = new AtomicInteger();
    childBus.simpleConnect().subscribe(TOPIC1, new T1Listener() {
      @Override
      public void t11() {
        counter.incrementAndGet();
      }
    });

    bus.simpleConnect().subscribe(RUNNABLE_TOPIC, () -> {
      // add childBus to a list of waiting buses
      childBus.syncPublisher(TOPIC1).t11();
    });

    bus.syncPublisher(RUNNABLE_TOPIC).run();

    assertThat(counter.get()).isEqualTo(1);
  }
  private static final Topic<Runnable> TO_PARENT_TOPIC = new Topic<>(Runnable.class, Topic.BroadcastDirection.TO_PARENT);

  @Test
  public void subscriberCacheClearedOnConnectionToParentBusForChildBusTopic() {
    // ensure that subscriber cache is cleared on connection to app level bus for topic that published to project level bus with TO_PARENT direction.
    MessageBus child = new CompositeMessageBus(this, bus);
    // call to compute cache
    child.syncPublisher(TO_PARENT_TOPIC).run();

    Ref<Boolean> isCalled = new Ref<>(false);
    bus.connect().subscribe(TO_PARENT_TOPIC, () -> isCalled.set(true));
    child.syncPublisher(TO_PARENT_TOPIC).run();
    assertThat(isCalled.get()).isTrue();
  }

  @Test
  public void subscriberCacheClearedOnConnectionToChildrenBusFoRootBusTopic() {
    // child must be created before to ensure that cache is not cleared on a new child
    MessageBus child = new CompositeMessageBus(this, bus);
    // call to compute cache
    bus.syncPublisher(RUNNABLE_TOPIC).run();

    Ref<Boolean> isCalled = new Ref<>(false);
    child.connect().subscribe(RUNNABLE_TOPIC, () -> isCalled.set(true));
    bus.syncPublisher(RUNNABLE_TOPIC).run();
    assertThat(isCalled.get()).isTrue();
  }

  @Test
  public void disconnectOnPluginUnload() {
    // child must be created before to ensure that cache is not cleared on a new child
    MessageBus child = new CompositeMessageBus(this, bus);
    // call to compute cache
    bus.syncPublisher(RUNNABLE_TOPIC).run();

    AtomicInteger callCounter = new AtomicInteger();
    Runnable listener = () -> callCounter.incrementAndGet();

    // add twice
    child.connect().subscribe(RUNNABLE_TOPIC, listener);
    child.connect().subscribe(RUNNABLE_TOPIC, listener);

    bus.disconnectPluginConnections(new Predicate<>() {
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

    bus.syncPublisher(RUNNABLE_TOPIC).run();
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
          MessageBusConnection connection = bus.connect();
          bus.removeEmptyConnectionsRecursively();
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

    bus.syncPublisher(RUNNABLE_TOPIC).run();
    assertThat(eventCounter.get()).isEqualTo(threadsNumber);
  }

  @Test
  public void disconnectOnDisposeForImmediateDeliveryTopic() {
    Topic<Runnable> TOPIC = new Topic<>(Runnable.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

    Disposable disposable = Disposer.newDisposable();
    MessageBusConnectionImpl connection = bus.connect(disposable);
    connection.subscribe(TOPIC, () -> fail("must be not called"));
    Disposer.dispose(disposable);
    bus.syncPublisher(TOPIC).run();
  }

  @Test
  public void disconnectAndDisposeOnDisposeForImmediateDeliveryTopic() {
    Topic<Runnable> TOPIC = new Topic<>(Runnable.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

    Disposable disposable = Disposer.newDisposable();

    MessageBusConnection connection2 = bus.connect();
    connection2.subscribe(TOPIC, () -> {
      Disposer.dispose(disposable);
    });

    MessageBusConnectionImpl connection = bus.connect(disposable);
    connection.subscribe(TOPIC, () -> fail("must be not called"));

    bus.syncPublisher(TOPIC).run();
  }
}
