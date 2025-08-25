// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages.impl

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.use
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.LoggedErrorProcessorEnabler
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.Topic
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import kotlin.coroutines.cancellation.CancellationException

private val TOPIC1 = Topic("T1", MessageBusTest.T1Listener::class.java, Topic.BroadcastDirection.TO_CHILDREN)
private val TOPIC2 = Topic("T2", MessageBusTest.T2Listener::class.java, Topic.BroadcastDirection.TO_CHILDREN)
private val RUNNABLE_TOPIC = Topic(Runnable::class.java, Topic.BroadcastDirection.TO_CHILDREN)
private val TO_PARENT_TOPIC = Topic(Runnable::class.java, Topic.BroadcastDirection.TO_PARENT)
private val IMMEDIATE_DELIVERY = Topic(MessageBusTest.T1Listener::class.java, Topic.BroadcastDirection.NONE, true)

@ExtendWith(DoNoRethrowMessageBusErrors::class)
class MessageBusTest : MessageBusOwner {
  private lateinit var bus: RootBus
  private val log: MutableList<String> = ArrayList()
  private var parentDisposable: CheckedDisposable? = Disposer.newCheckedDisposable()

  override fun createListener(descriptor: PluginListenerDescriptor): Any = throw UnsupportedOperationException()

  override fun isDisposed(): Boolean = parentDisposable!!.isDisposed

  override fun isParentLazyListenersIgnored(): Boolean = true

  interface T1Listener {
    fun t11()
    fun t12() {}
  }

  interface T2Listener {
    fun t21()
    fun t22()
  }

  private inner class T1Handler(private val id: String) : T1Listener {
    override fun t11() {
      log.add("$id:t11")
    }

    override fun t12() {
      log.add("$id:t12")
    }
  }

  private inner class T2Handler(private val id: String) : T2Listener {
    override fun t21() {
      log.add("$id:t21")
    }

    override fun t22() {
      log.add("$id:t22")
    }
  }

  @BeforeEach
  fun setUp() {
    bus = MessageBusFactoryImpl.createRootBus(this)
    Disposer.register(parentDisposable!!, bus)
  }

  @AfterEach
  fun tearDown() {
    Disposer.dispose(parentDisposable!!)
  }

  @Test
  fun noListenersSubscribed() {
    bus.syncPublisher(TOPIC1).t11()
    assertEvents()
  }

  @Test
  fun singleMessage() {
    val connection = bus.connect()
    connection.subscribe(TOPIC1, T1Handler("c"))
    bus.syncPublisher(TOPIC1).t11()
    assertEvents("c:t11")
  }

  @Test
  fun singleMessageToTwoConnections() {
    val c1 = bus.connect()
    c1.subscribe(TOPIC1, T1Handler("c1"))
    val c2 = bus.connect()
    c2.subscribe(TOPIC1, T1Handler("c2"))
    bus.syncPublisher(TOPIC1).t11()
    assertEvents("c1:t11", "c2:t11")
  }

  @Test
  fun sameTopicInOneConnection() {
    val connection = bus.connect()
    connection.subscribe(TOPIC1, T1Handler("c1"))
    connection.subscribe(TOPIC1, T1Handler("c2"))
    bus.syncPublisher(TOPIC1).t11()
    assertEvents("c1:t11", "c2:t11")
  }

  @Test
  fun twoMessagesWithSingleSubscription() {
    val connection = bus.connect()
    connection.subscribe(TOPIC1, T1Handler("c"))
    bus.syncPublisher(TOPIC1).t11()
    bus.syncPublisher(TOPIC1).t12()
    assertEvents("c:t11", "c:t12")
  }

  @Test
  fun twoMessagesWithDoubleSubscription() {
    val c1 = bus.connect()
    c1.subscribe(TOPIC1, T1Handler("c1"))
    val c2 = bus.connect()
    c2.subscribe(TOPIC1, T1Handler("c2"))
    bus.syncPublisher(TOPIC1).t11()
    bus.syncPublisher(TOPIC1).t12()
    assertEvents("c1:t11", "c2:t11", "c1:t12", "c2:t12")
  }

  @Test
  fun eventFiresAnotherEvent() {
    bus.connect().subscribe(TOPIC1, object : T1Listener {
      override fun t11() {
        log.add("inside:t11")
        bus.syncPublisher(TOPIC2).t21()
        log.add("inside:t11:done")
      }

      override fun t12() {
        log.add("c1:t12")
      }
    })
    val conn = bus.connect()
    conn.subscribe(TOPIC1, T1Handler("handler1"))
    conn.subscribe(TOPIC2, T2Handler("handler2"))
    bus.syncPublisher(TOPIC1).t12()
    assertEvents("c1:t12", "handler1:t12")
    bus.syncPublisher(TOPIC1).t11()
    assertEvents("""
  c1:t12
  handler1:t12
  inside:t11
  handler1:t11
  handler2:t21
  inside:t11:done
  """.trimIndent())
  }

  @Test
  fun connectionTerminatedInDispatch() {
    val conn1 = bus.connect()
    conn1.subscribe(TOPIC1, object : T1Listener {
      override fun t11() {
        conn1.disconnect()
        log.add("inside:t11")
        bus.syncPublisher(TOPIC2).t21()
        log.add("inside:t11:done")
      }

      override fun t12() {
        log.add("inside:t12")
      }
    })
    conn1.subscribe(TOPIC2, T2Handler("C1T2Handler"))
    val conn2 = bus.connect()
    conn2.subscribe(TOPIC1, T1Handler("C2T1Handler"))
    conn2.subscribe(TOPIC2, T2Handler("C2T2Handler"))
    bus.syncPublisher(TOPIC1).t11()
    assertEvents("inside:t11",
                 "C2T1Handler:t11",
                 "C2T2Handler:t21",
                 "inside:t11:done")
    bus.syncPublisher(TOPIC1).t12()
    assertEvents("inside:t11",
                 "C2T1Handler:t11",
                 "C2T2Handler:t21",
                 "inside:t11:done",
                 "C2T1Handler:t12")
  }

  @Test
  fun messageDeliveredDespitePce1() {
    val conn1 = bus.connect()
    conn1.subscribe(TOPIC1, object : T1Listener {
      override fun t11() {
        log.add("pce")
        throw ProcessCanceledException()
      }

      override fun t12() {
        throw UnsupportedOperationException()
      }
    })

    conn1.subscribe(TOPIC1, object : T1Listener {
      override fun t11() {
        log.add("cce")
        throw CancellationException()
      }

      override fun t12() {
        throw UnsupportedOperationException()
      }
    })

    val conn2 = bus.connect()
    conn2.subscribe(TOPIC1, T1Handler("handler2"))
    bus.syncPublisher(TOPIC1).t11()

    assertEvents("pce", "cce", "handler2:t11")
  }

  @Test
  fun runtimeErrorPropagationFromListener1() {
    val conn3 = bus.connect()
    conn3.subscribe(TOPIC1, T1Handler("handler3"))

    val conn1 = bus.connect()
    conn1.subscribe(TOPIC1, object : T1Listener {
      override fun t11() {
        log.add("pce")
        throw ProcessCanceledException()
      }

      override fun t12() {
        log.add("uoe")
        throw IllegalStateException()
      }
    })

    val conn2 = bus.connect()
    conn2.subscribe(TOPIC1, T1Handler("handler2"))

    bus.syncPublisher(TOPIC1).t12()

    // event is delivered to all subscribers, then the error is logged
    assertEvents("handler3:t12", "uoe", "handler2:t12")
  }

  @Test
  fun abstractErrorPropagationFromListener1() {
    val conn3 = bus.connect()
    conn3.subscribe(TOPIC1, T1Handler("handler3"))

    val conn1 = bus.connect()
    conn1.subscribe(TOPIC1, object : T1Listener {
      override fun t11() {
        log.add("pce")
        throw ProcessCanceledException()
      }

      override fun t12() {
        log.add("uoe")
        throw AbstractMethodError() // incompatible interface change
      }
    })

    val conn2 = bus.connect()
    conn2.subscribe(TOPIC1, T1Handler("handler2"))

    bus.syncPublisher(TOPIC1).t12()

    // event is delivered to all subscribers, the error is NOT rethrown
    assertEvents("handler3:t12", "uoe", "handler2:t12")
  }

  @Test
  fun messageDeliveredDespitePce2() {
    val conn1 = bus.connect()
    conn1.subscribe(IMMEDIATE_DELIVERY, object : T1Listener {
      override fun t11() {
        log.add("pce")
        throw ProcessCanceledException()
      }

      override fun t12() {
        throw UnsupportedOperationException()
      }
    })

    conn1.subscribe(IMMEDIATE_DELIVERY, object : T1Listener {
      override fun t11() {
        log.add("cce")
        throw CancellationException()
      }

      override fun t12() {
        throw UnsupportedOperationException()
      }
    })

    val conn2 = bus.connect()
    conn2.subscribe(IMMEDIATE_DELIVERY, T1Handler("handler2"))
    bus.syncPublisher(IMMEDIATE_DELIVERY).t11()

    assertEvents("pce", "cce", "handler2:t11")
  }

  @Test
  fun runtimeErrorPropagationFromListener2() {
    val conn3 = bus.connect()
    conn3.subscribe(IMMEDIATE_DELIVERY, T1Handler("handler3"))

    val conn1 = bus.connect()
    conn1.subscribe(IMMEDIATE_DELIVERY, object : T1Listener {
      override fun t11() {
        log.add("pce")
        throw ProcessCanceledException()
      }

      override fun t12() {
        log.add("uoe")
        throw IllegalStateException()
      }
    })

    val conn2 = bus.connect()
    conn2.subscribe(IMMEDIATE_DELIVERY, T1Handler("handler2"))

    bus.syncPublisher(IMMEDIATE_DELIVERY).t12()

    // event is delivered to all subscribers, then the error is logged
    assertEvents("handler3:t12", "uoe", "handler2:t12")
  }

  @Test
  fun abstractErrorPropagationFromListener2() {
    val conn3 = bus.connect()
    conn3.subscribe(IMMEDIATE_DELIVERY, T1Handler("handler3"))

    val conn1 = bus.connect()
    conn1.subscribe(IMMEDIATE_DELIVERY, object : T1Listener {
      override fun t11() {
        log.add("pce")
        throw ProcessCanceledException()
      }

      override fun t12() {
        log.add("uoe")
        throw AbstractMethodError() // incompatible interface change
      }
    })

    val conn2 = bus.connect()
    conn2.subscribe(IMMEDIATE_DELIVERY, T1Handler("handler2"))

    bus.syncPublisher(IMMEDIATE_DELIVERY).t12()

    // event is delivered to all subscribers, the error is NOT rethrown
    assertEvents("handler3:t12", "uoe", "handler2:t12")
  }

  @Test
  fun manyChildrenCreationDeletionPerformance() {
    Benchmark.newBenchmark("Child bus creation/deletion") {
      val children = ArrayList<MessageBus>()
      val count = 10000
      repeat(count) {
        children.add(MessageBusFactoryImpl().createMessageBus(this, bus))
      }
      // reverse iteration to avoid O(n^2) while deleting from list's beginning
      for (i in count - 1 downTo 0) {
        Disposer.dispose(children[i])
      }
    }
      .runAsStressTest()
      .start()
  }

  @Test
  fun testStress() {
    val threadsNumber = 10
    val exception = AtomicReference<Throwable?>()
    val latch = CountDownLatch(threadsNumber)
    val parentBus = MessageBusFactoryImpl.createRootBus(createSimpleMessageBusOwner("parent"))
    Disposer.register(parentDisposable!!, parentBus)
    val threads = ArrayList<Future<*>>()
    val iterationsNumber = 100
    repeat(threadsNumber) {
      val thread = AppExecutorUtil.getAppScheduledExecutorService().submit {
        try {
          var remains = iterationsNumber
          while (remains-- > 0) {
            if (exception.get() != null) {
              break
            }
            CompositeMessageBus(createSimpleMessageBusOwner(String.format("child-%s-%s", Thread.currentThread().name, remains)), parentBus)
          }
        }
        catch (e: Throwable) {
          exception.set(e)
        }
        finally {
          latch.countDown()
        }
      }
      threads.add(thread)
    }
    latch.await()
    val e = exception.get()
    if (e != null) {
      throw e
    }
    for (thread in threads) {
      thread.get()
    }
  }

  private fun assertEvents(vararg expected: String) {
    assertThat(java.lang.String.join("\n", log)).isEqualTo(java.lang.String.join("\n", *expected))
  }

  @Test
  fun hasUndeliveredEvents() {
    assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isFalse
    assertThat(bus.hasUndeliveredEvents(TOPIC2)).isFalse
    bus.connect().subscribe(RUNNABLE_TOPIC, Runnable {
      assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isTrue
      assertThat(bus.hasUndeliveredEvents(TOPIC2)).isFalse()
    })
    bus.connect().subscribe(RUNNABLE_TOPIC, Runnable {
      assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isFalse
      assertThat(bus.hasUndeliveredEvents(TOPIC2)).isFalse()
    })
    bus.syncPublisher(RUNNABLE_TOPIC).run()
  }

  @Test
  fun hasUndeliveredEventsInChildBus() {
    val childBus = MessageBusFactoryImpl().createMessageBus(this, bus)
    bus.connect().subscribe(RUNNABLE_TOPIC, Runnable {
      assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isTrue()
    })
    childBus.connect().subscribe(RUNNABLE_TOPIC, Runnable {
      assertThat(bus.hasUndeliveredEvents(RUNNABLE_TOPIC)).isFalse()
    })
    bus.syncPublisher(RUNNABLE_TOPIC).run()
  }

  @Test
  fun scheduleEmptyConnectionRemoval() {
    // this counter serves as a proxy to number of iterations inside the compaction task loop in root bus:
    var compactionIterationCount = 0
    object : MessageBusImpl(this@MessageBusTest, bus) {
      override fun removeEmptyConnectionsRecursively() {
        compactionIterationCount++
      }
    }

    // this child bus will block removal so that we can run asserts
    val slowRemovalStarted = Semaphore(1)
    slowRemovalStarted.acquire()
    val slowRemovalCanFinish = CountDownLatch(1)
    object : MessageBusImpl(this@MessageBusTest, bus) {
      override fun removeEmptyConnectionsRecursively() {
        slowRemovalStarted.release() // signal that slow removal has started
        try {
          slowRemovalCanFinish.await() // block until the test allows us to finish
        }
        catch (e: InterruptedException) {
          throw RuntimeException(e)
        }
      }
    }

    // scheduleEmptyConnectionRemoving is not invoked immediately, call it until we detect that slow removal has started:
    var callCountToTriggerRemoval = 0
    while (true) {
      bus.scheduleEmptyConnectionRemoving()
      if (slowRemovalStarted.tryAcquire()) {
        break
      }
      callCountToTriggerRemoval++
    }
    assertThat(compactionIterationCount).isEqualTo(1)

    // now compaction task is blocked in slow remove, schedule more compaction requests:
    repeat(callCountToTriggerRemoval) {
      bus.scheduleEmptyConnectionRemoving()
    }

    // no new compaction task was started:
    assertThat(compactionIterationCount).isEqualTo(1)

    // allow slow removal to finish:
    slowRemovalCanFinish.countDown()

    // wait until slow removal will finish one more time: we requested removal several times,
    // so we need one more loop iteration to handle pending removal requests
    if (!slowRemovalStarted.tryAcquire(30, TimeUnit.SECONDS)) {
      Fail.fail<Any>("Compaction requests were not served in 30 seconds")
    }
    Thread.sleep(50) // give compaction task a chance to do more iterations
    assertThat(compactionIterationCount).isEqualTo(2) // only one additional iteration is needed to handle pending removal requests
  }

  @Test
  fun disposingBusInsideEvent() {
    val child = MessageBusFactoryImpl().createMessageBus(this, bus)
    bus.connect().subscribe(TOPIC1, object : T1Listener {
      override fun t11() {
        log.add("root 11")
        bus.syncPublisher(TOPIC1).t12()
        Disposer.dispose(child)
      }

      override fun t12() {
        log.add("root 12")
      }
    })
    child.connect().subscribe(TOPIC1, object : T1Listener {
      override fun t11() {
        log.add("child 11")
      }

      override fun t12() {
        log.add("child 12")
      }
    })
    bus.syncPublisher(TOPIC1).t11()
    assertEvents("root 11", "child 11", "root 12", "child 12")
  }

  @Test
  fun twoHandlersBothDisconnecting() {
    val disposable = Disposer.newCheckedDisposable()
    repeat(2) {
      bus.connect(disposable).subscribe(RUNNABLE_TOPIC, Runnable { Disposer.dispose(disposable) })
    }
    bus.syncPublisher(RUNNABLE_TOPIC).run()
    assertThat(disposable.isDisposed).isTrue
  }

  @Test
  fun subscriberCacheClearedOnChildBusDispose() {
    // ensure that subscriber cache is cleared on child bus dispose
    val child = MessageBusFactoryImpl().createMessageBus(this, bus)
    val isDisposed = Ref(false)
    child.connect().subscribe(RUNNABLE_TOPIC, Runnable { check(!isDisposed.get()) { "already disposed" } })
    bus.syncPublisher(RUNNABLE_TOPIC).run()
    Disposer.dispose(child)
    isDisposed.set(true)
    bus.syncPublisher(RUNNABLE_TOPIC).run()
  }

  @Test
  fun publishToAnotherBus() {
    val childBus = MessageBusFactoryImpl().createMessageBus(this, bus)
    Disposer.register(parentDisposable!!, childBus)
    var counter = 0
    childBus.simpleConnect().subscribe(TOPIC1, object : T1Listener {
      override fun t11() {
        counter++
      }
    })
    bus.simpleConnect().subscribe(RUNNABLE_TOPIC, Runnable {
      // add childBus to a list of waiting buses
      childBus.syncPublisher(TOPIC1).t11()
    })
    bus.syncPublisher(RUNNABLE_TOPIC).run()
    assertThat(counter).isEqualTo(1)
  }

  @Test
  fun subscriberCacheClearedOnConnectionToParentBusForChildBusTopic() {
    // ensure that subscriber cache is cleared on connection to app level bus for topic that published to project level bus with TO_PARENT direction.
    val child = MessageBusFactoryImpl().createMessageBus(this, bus)
    // call to compute cache
    child.syncPublisher(TO_PARENT_TOPIC).run()
    val isCalled = Ref(false)
    bus.connect().subscribe(TO_PARENT_TOPIC, Runnable { isCalled.set(true) })
    child.syncPublisher(TO_PARENT_TOPIC).run()
    assertThat(isCalled.get()).isTrue
  }

  @Test
  fun subscriberCacheClearedOnConnectionToChildrenBusFoRootBusTopic() {
    // child must be created before to ensure that cache is not cleared on a new child
    val child = MessageBusFactoryImpl().createMessageBus(this, bus)
    // call to compute cache
    bus.syncPublisher(RUNNABLE_TOPIC).run()
    var isCalled = false
    child.connect().subscribe(RUNNABLE_TOPIC, Runnable { isCalled = true })
    bus.syncPublisher(RUNNABLE_TOPIC).run()
    assertThat(isCalled).isTrue
  }

  @Test
  fun disconnectOnPluginUnload() {
    // child must be created before to ensure that cache is not cleared on a new child
    val child: MessageBus = MessageBusFactoryImpl().createMessageBus(this, bus)
    // call to compute cache
    bus.syncPublisher(RUNNABLE_TOPIC).run()
    var callCounter = 0
    val listener = Runnable { callCounter++ }

    // add twice
    child.connect().subscribe(RUNNABLE_TOPIC, listener)
    child.connect().subscribe(RUNNABLE_TOPIC, listener)
    bus.disconnectPluginConnections(object : Predicate<Class<*>> {
      var isRemoved = false
      override fun test(aClass: Class<*>): Boolean {
        // remove first one
        if (isRemoved) {
          return false
        }
        isRemoved = true
        return true
      }
    })
    bus.syncPublisher(RUNNABLE_TOPIC).run()
    assertThat(callCounter).isEqualTo(1)
  }

  @Test
  fun removeEmptyConnectionsRecursively() {
    val threadsNumber = 10
    val exception = AtomicReference<Throwable>()
    val latch = CountDownLatch(threadsNumber)
    val threads = ArrayList<Future<*>>()
    val eventCounter = AtomicInteger()
    repeat(threadsNumber) {
      val thread = AppExecutorUtil.getAppScheduledExecutorService().submit {
        try {
          val connection = bus.connect()
          bus.removeEmptyConnectionsRecursively()
          connection.subscribe(RUNNABLE_TOPIC, Runnable { eventCounter.incrementAndGet() })
        }
        catch (e: Throwable) {
          exception.set(e)
        }
        finally {
          latch.countDown()
        }
      }
      threads.add(thread)
    }
    latch.await()
    exception.get()?.let {
      throw it
    }
    for (thread in threads) {
      thread.get()
    }
    bus.syncPublisher(RUNNABLE_TOPIC).run()
    assertThat(eventCounter.get()).isEqualTo(threadsNumber)
  }

  @Test
  fun disconnectOnDisposeForImmediateDeliveryTopic() {
    val topic = Topic(Runnable::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)
    Disposer.newDisposable().use { disposable ->
      bus.connect(disposable).subscribe(topic, Runnable { Fail.fail<Any>("must be not called") })
    }
    bus.syncPublisher(topic).run()
  }

  @Test
  fun disconnectAndDisposeOnDisposeForImmediateDeliveryTopic() {
    val topic = Topic(Runnable::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)
    val disposable = Disposer.newDisposable()
    bus.connect().subscribe(topic, Runnable { Disposer.dispose(disposable) })
    bus.connect(disposable).subscribe(topic, Runnable { Fail.fail<Any>("must be not called") })
    bus.syncPublisher(topic).run()
  }
}

private class DoNoRethrowMessageBusErrors : LoggedErrorProcessorEnabler {
  override fun createErrorProcessor(): LoggedErrorProcessor {
    return object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        if ("#com.intellij.util.messages.impl.MessageBusImpl" == category) {
          return setOf(Action.LOG)
        }
        return super.processError(category, message, details, t)
      }
    }
  }
}

private fun createSimpleMessageBusOwner(owner: String): MessageBusOwner {
  return object : MessageBusOwner {
    override fun createListener(descriptor: PluginListenerDescriptor) = throw UnsupportedOperationException()

    override fun isDisposed() = false

    override fun toString() = owner
  }
}
