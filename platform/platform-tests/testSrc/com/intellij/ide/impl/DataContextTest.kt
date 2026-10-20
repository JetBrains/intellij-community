package com.intellij.ide.impl

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.actionSystem.get
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import java.awt.Component
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import kotlin.time.Duration.Companion.seconds

@TestApplication
class DataContextTest {

  @Test
  fun `one level`() {
    val context = buildContext(K2FromK1) {
      uiDataProvider {
        it[K1] = "K1"
      }
    }
    assertEquals("K1", context[K1])
    assertEquals("rule K2 from 'K1'", context[K2])
  }

  @Test
  fun `one level snapshot override`() {
    val context = buildContext(K2FromK1) {
      uiDataProvider {
        it[K1] = "K1"
        it[K1] = "K1 override"
      }
    }
    assertEquals("K1 override", context[K1])
    assertEquals("rule K2 from 'K1 override'", context[K2])
  }

  @Test
  fun `one level lazy override`() {
    val context = buildContext(K2FromK1) {
      uiDataProvider {
        it.lazy(K3) { "lazy K3" }
        it[K1] = "K1"
        it.lazy(K1) { "K1 override" }
      }
    }
    assertEquals("K1 override", context[K1])
    assertEquals(null, context[K2])
    assertEquals("lazy K3", context[K3])
  }

  @Test
  fun `one level snapshot with nullable lazy override`() {
    val context = buildContext(K2FromK1) {
      uiDataProvider {
        it[K1] = "K1"
        it.lazy(K1) { null }
      }
    }
    assertEquals("K1", context[K1])
    assertEquals(null, context[K2])
  }

  @Test
  fun `one level snapshot with nullable lazy override and rule`() {
    val context = buildContext(LazyK2FromLazyK1) {
      uiDataProvider {
        it[K1] = "K1"
        it.lazy(K1) { null }
      }
    }
    assertEquals("K1", context[K1])
    assertEquals("rule K2 from 'K1'", context[K2])
  }

  @Test
  fun `one level lazy`() {
    val context = buildContext(K2FromK1) {
      uiDataProvider {
        it.lazy(K1) { "K1" }
      }
    }
    assertEquals("K1", context[K1])
    assertEquals(null, context[K2])
  }

  @Test
  fun `two level no lazy`() {
    val context = buildContext(K2FromK1) {
      uiDataProvider("outer") {
        it[K1] = "outer K1"
      }
      uiDataProvider("inner") {
        it[K1] = "inner K1"
      }
    }
    assertEquals("inner K1", context[K1])
    assertEquals("rule K2 from 'inner K1'", context[K2])
  }

  @Test
  fun `lazy overrides parent snapshot key`() {
    val context = buildContext(LazyK2FromLazyK1) {
      uiDataProvider("outer") {
        it[K1] = "outer K1"
      }
      uiDataProvider("inner") {
        it.lazy(K1) { "inner K1" }
      }
    }
    assertEquals("inner K1", context[K1])
    assertEquals("rule K2 from 'inner K1'", context[K2])
  }

  @Test
  fun `snapshot overrides parent lazy key`() {
    val context = buildContext(LazyK2FromLazyK1) {
      uiDataProvider("inner") {
        it.lazy(K1) { "inner K1" }
      }
      uiDataProvider("outer") {
        it[K1] = "outer K1"
      }
    }
    assertEquals("outer K1", context[K1])
    assertEquals("rule K2 from 'outer K1'", context[K2])
  }

  @Test
  fun `two level lazy`() {
    val context = buildContext(K2FromK1) {
      uiDataProvider("outer") {
        it.lazy(K1) { "outer K1" }
      }
      uiDataProvider("inner") {
        it.lazy(K1) { "inner K1" }
      }
    }
    assertEquals("inner K1", context[K1])
    assertEquals(null, context[K2])
  }

  @Test
  fun `two level lazy 2`() {
    val context = buildContext(LazyK2FromK1) {
      uiDataProvider("outer") {
        it.lazy(K1) { "outer K1" }
      }
      uiDataProvider("inner") {
        it.lazy(K1) { "inner K1" }
      }
    }
    assertEquals("inner K1", context[K1])
    assertEquals(null, context[K2])
  }

  @Test
  fun `two level lazy 3`() {
    val context = buildContext(LazyK2FromLazyK1) {
      uiDataProvider("outer") {
        it.lazy(K1) { "outer K1" }
      }
      uiDataProvider("inner") {
        it.lazy(K1) { "inner K1" }
      }
    }
    assertEquals("inner K1", context[K1])
    assertEquals("rule K2 from 'inner K1'", context[K2])
  }

  @Test
  fun `lazy value`() {
    val context = buildContext {
      uiDataProvider {
        it.lazy(K1) { "K1" }
        it.lazyValue(K2) { map -> "K2 from '${map[K1]}'" }
      }
    }
    assertEquals("K1", context[K1])
    assertEquals("K2 from 'K1'", context[K2])
  }

  @Test
  fun `lazy value fails on closed sink use`() {
    val context = buildContext {
      uiDataProvider {
        it.lazy(K1) { "K1" }
        it.lazyValue(K2) { map ->
          assertThrows<AssertionError> {
            it[K2] = null
          }
          assertThrows<AssertionError> {
            it.setNull(K2)
          }
          assertThrows<AssertionError> {
            it.lazy(K2) { null }
          }
          assertThrows<AssertionError> {
            it.lazyValue(K2) { null }
          }
          "K2 from '${map[K1]}'"
        }
      }
    }
    assertEquals("K1", context[K1])
    assertEquals("K2 from 'K1'", context[K2])
  }

  @Test
  fun `lazy value 2`() {
    val context = buildContext {
      uiDataProvider("outer") {
        it.lazy(K1) { "outer K1" } // editor
        it.lazy(K2) { "outer K2" } // offset
        it.lazyValue(K3) { map -> "outer K3 from '${map[K1]}' and '${map[K2]}'" } // psi
      }
      uiDataProvider("inner") {
        it.lazy(K1) { "inner K1" } // editor
        //it.lazy(K2) { "inner K2" } // offset
        //it.lazyValue(K3) { map -> "inner K3 from '${map[K1]}' and '${map[K2]}'" } // psi
      }
    }
    assertEquals("inner K1", context[K1])
    assertEquals("outer K2", context[K2])
    assertEquals("outer K3 from 'inner K1' and 'outer K2'", context[K3]) // null
  }

  @Test
  fun `rules keep cached lazy intact`() {
    val context = buildContext(LazyK2FromLazyK1) {
      uiDataProvider {
        it.lazy(K1) { "K1" }
        it.lazy(K2) { "K2" }
      }
    }
    assertEquals("K1", context[K1])
    assertEquals("K2", context[K2])
  }

  @Test
  fun `rules are reused when possible`() {
    var count = 0
    val context = buildContext {
      uiDataProvider {
        count ++
        it.lazy(K1) { "K1" }
      }
      cacheContext()
      count = 0
    }
    assertEquals(0, count)
    assertEquals("K1", context[K1])
  }

  @Test
  fun `rules not skipped when partially cached`() {
    val context = buildContext(LazyK2FromLazyK1) {
      uiDataProvider {
        it.lazy(K1) { "K1" }
        it.lazy(K2) { "K2" }
      }
      cacheContext()
      uiDataProvider {
        it.lazy(K3) { "K3" }
      }
    }
    assertEquals("K1", context[K1])
    assertEquals("K2", context[K2])
    assertEquals("K3", context[K3])
  }


  @Test
  fun `rules that reference each other do not block two threads`() {
    // `VirtualFileRule` and `VirtualFileArrayRule` form such a cycle in the platform.
    val bothInsideRule = CyclicBarrier(2)
    val k1FromK2 = object : UiDataRule {
      override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        sink.lazyValue(K1) { map ->
          bothInsideRule.await(1, TimeUnit.MINUTES)
          map[K2]
        }
      }
    }
    val k2FromK1 = object : UiDataRule {
      override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        sink.lazyValue(K2) { map ->
          bothInsideRule.await(1, TimeUnit.MINUTES)
          map[K1]
        }
      }
    }
    val context = buildContext(k1FromK2, k2FromK1) {
      uiDataProvider {
        it[K3] = "K3"
      }
    }
    val pool = Executors.newFixedThreadPool(2)
    try {
      // Each thread owns one key of the cycle and then asks for the other one.
      val first = pool.submit<String?> { context[K1] }
      val second = pool.submit<String?> { context[K2] }
      assertEquals(null, first.get(1, TimeUnit.MINUTES))
      assertEquals(null, second.get(1, TimeUnit.MINUTES))
    }
    finally {
      pool.shutdownNow()
    }
  }

  @Test
  fun `concurrent requests share one rule run`() = withSlowK1Rule({ "K1" }) { rule, first, second ->
    rule.release.countDown()
    assertEquals("K1", first.get(1, TimeUnit.MINUTES))
    assertEquals("K1", second.get(1, TimeUnit.MINUTES))
    assertEquals(1, rule.runs.get())
  }

  @Test
  fun `concurrent requests share one rule run that yields null`() = withSlowK1Rule({ null }) { rule, first, second ->
    rule.release.countDown()
    assertEquals(null, first.get(1, TimeUnit.MINUTES))
    assertEquals(null, second.get(1, TimeUnit.MINUTES))
    assertEquals(1, rule.runs.get())
  }

  @Test
  fun `canceled owner lets waiter run the rule`() {
    val ownerIndicator = EmptyProgressIndicator()
    withSlowK1Rule({ "K1" }, ownerIndicator) { rule, first, second ->
      ownerIndicator.cancel()
      rule.release.countDown()
      assertTrue(assertThrows<ExecutionException> { first.get(1, TimeUnit.MINUTES) }.cause is ProcessCanceledException)
      assertEquals("K1", second.get(1, TimeUnit.MINUTES))
      assertEquals(2, rule.runs.get())
    }
  }

  @Test
  fun `canceled waiter leaves owner running`() {
    val waiterIndicator = EmptyProgressIndicator()
    withSlowK1Rule({ "K1" }, waiterIndicator = waiterIndicator) { rule, first, second ->
      waiterIndicator.cancel()
      assertTrue(assertThrows<ExecutionException> { second.get(1, TimeUnit.MINUTES) }.cause is ProcessCanceledException)
      rule.release.countDown()
      assertEquals("K1", first.get(1, TimeUnit.MINUTES))
      assertEquals(1, rule.runs.get())
    }
  }

  @Test
  fun `EDT does not wait for a background owner`(): Unit = timeoutRunBlocking {
    val disposable = Disposer.newDisposable()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val pool = Executors.newSingleThreadExecutor { Thread(it, "DataContextTest owner") }
    try {
      Registry.get("actionSystem.update.actions.suppress.dataRules.on.edt").setValue(false, disposable)
      val context = buildContext {
        uiDataProvider { sink ->
          sink.lazy(K1) {
            if (EDT.isCurrentThreadEdt()) "EDT"
            else {
              entered.countDown()
              assertTrue(release.await(1, TimeUnit.MINUTES))
              "BGT"
            }
          }
        }
      }
      val owner = pool.submit<String?> { context[K1] }
      assertTrue(entered.await(1, TimeUnit.MINUTES))
      val edt = async(Dispatchers.UI) { context[K1] }
      assertEquals("EDT", withTimeout(10.seconds) { edt.await() })
      assertFalse(owner.isDone)
      release.countDown()
      assertEquals("BGT", owner.get(1, TimeUnit.MINUTES))
    }
    finally {
      release.countDown()
      pool.shutdownNow()
      try {
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
      }
      finally {
        Disposer.dispose(disposable)
      }
    }
  }

  @Test
  fun `independent keys run concurrently`() {
    val bothInsideRule = CyclicBarrier(2)
    val context = buildContext {
      uiDataProvider { sink ->
        for (key in listOf(K1, K2)) {
          sink.lazy(key) {
            bothInsideRule.await(1, TimeUnit.MINUTES)
            key.name
          }
        }
      }
    }
    val pool = Executors.newFixedThreadPool(2)
    try {
      val first = pool.submit<String?> { context[K1] }
      val second = pool.submit<String?> { context[K2] }
      assertEquals("K1", first.get(1, TimeUnit.MINUTES))
      assertEquals("K2", second.get(1, TimeUnit.MINUTES))
    }
    finally {
      pool.shutdownNow()
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
    }
  }

  /** A `K1` rule that counts its runs and blocks each run until [release] */
  private class SlowK1Rule(private val compute: () -> String?) : UiDataRule {
    val runs = AtomicInteger()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
      sink.lazyValue(K1) {
        runs.incrementAndGet()
        entered.countDown()
        release.await(1, TimeUnit.MINUTES)
        ProgressManager.checkCanceled()
        compute()
      }
    }
  }

  /**
   * Requests `K1` on two threads: the first one enters the rule and blocks, the second one arrives while
   * the first is inside and parks on its run. [block] then drives both to completion.
   */
  private fun withSlowK1Rule(
    compute: () -> String?,
    ownerIndicator: ProgressIndicator = EmptyProgressIndicator(),
    waiterIndicator: ProgressIndicator = EmptyProgressIndicator(),
    block: (rule: SlowK1Rule, first: Future<String?>, second: Future<String?>) -> Unit,
  ) {
    val rule = SlowK1Rule(compute)
    val context = buildContext(rule) {
      uiDataProvider {
        it[K3] = "K3"
      }
    }
    val pool = Executors.newFixedThreadPool(2)
    try {
      val first = pool.submit<String?> {
        ProgressManager.getInstance().runProcess<String?>({ context[K1] }, ownerIndicator)
      }
      assertTrue(rule.entered.await(1, TimeUnit.MINUTES))
      val secondThread = AtomicReference<Thread>()
      val second = pool.submit<String?> {
        secondThread.set(Thread.currentThread())
        ProgressManager.getInstance().runProcess<String?>({ context[K1] }, waiterIndicator)
      }
      // the second thread must park on the first one's run instead of starting its own
      val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1)
      while (secondThread.get()?.state != Thread.State.TIMED_WAITING) {
        assertTrue(System.nanoTime() < deadline, "second thread never waited")
        Thread.onSpinWait()
      }
      block(rule, first, second)
    }
    finally {
      pool.shutdownNow()
    }
  }

  //region Implementation
  interface Builder {
    fun uiDataProvider(debugName: String = "", uiDataProvider: UiDataProvider)
    fun cacheContext()
  }

  fun buildContext(vararg rules: UiDataRule, block: Builder.() -> Unit): DataContext {
    val ep = ExtensionPointName.create<UiDataRule>("com.intellij.uiDataRule").point as ExtensionPointImpl
    val disposable = Disposer.newDisposable()
    try {
      ep.maskAll(rules.toList(), disposable, false)
      var forceCaches = false
      var deepest: JComponent? = null
      block(object : Builder {
        override fun uiDataProvider(debugName: String, uiDataProvider: UiDataProvider) {
          val next = object : JComponent(), UiDataProvider {
            override fun uiDataSnapshot(sink: DataSink) = DataSink.uiDataSnapshot(sink, uiDataProvider)
          }
          deepest?.add(next)
          deepest = next
        }

        override fun cacheContext() {
          deepest?.toContext(false)
          forceCaches = true
        }
      })
      if (deepest == null) fail { "At least one uiDataProvider is required" }
      return deepest.toContext(forceCaches)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun Component.toContext(forceCaches: Boolean): DataContext = invokeAndWaitIfNeeded {
    when {
      forceCaches -> Utils.forceUseCachesAndCreateAsyncDataContextInTestsOnly(this)
      else -> Utils.createAsyncDataContext(this)
    }.also {
      assertTrue(Utils.isAsyncDataContext(it))
    }
  }

  companion object {
    val K1 = DataKey.create<String>("K1")
    val K2 = DataKey.create<String>("K2")
    val K3 = DataKey.create<String>("K3")

    object K2FromK1 : UiDataRule {
      override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        val v1 = snapshot[K1]
        if (v1 != null) {
          sink[K2] = "rule K2 from '$v1'"
        }
      }
    }

    object LazyK2FromK1 : UiDataRule {
      override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        val v1 = snapshot[K1]
        if (v1 != null) {
          sink.lazy(K2) {
            "rule K2 from '$v1'"
          }
        }
      }
    }

    object LazyK2FromLazyK1 : UiDataRule {
      override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        sink.lazyValue(K2) { map ->
          val v1 = map[K1]
          if (v1 != null) {
            "rule K2 from '$v1'"
          }
          else {
            null
          }
        }
      }
    }
  }
  //endregion
}