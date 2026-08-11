// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.Disposable
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@TestApplication
internal class PluginUpdatesServiceTest {
  @Test
  fun `merge updates deduplicates plugin ids independently in each bucket`() {
    val mergedUpdates = mergeUpdates(
      PluginUpdatesEvent(enabledUpdates = listOf(pluginDto("enabled.local", PluginSource.LOCAL), pluginDto("enabled.both", PluginSource.LOCAL)),
                         disabledUpdates = listOf(pluginDto("disabled.local", PluginSource.LOCAL), pluginDto("disabled.both", PluginSource.LOCAL)),
                         pluginNods = listOf(pluginDto("custom.local", PluginSource.LOCAL), pluginDto("custom.both", PluginSource.LOCAL))),
      PluginUpdatesEvent(enabledUpdates = listOf(pluginDto("enabled.both", PluginSource.REMOTE), pluginDto("enabled.remote", PluginSource.REMOTE)),
                         disabledUpdates = listOf(pluginDto("disabled.both", PluginSource.REMOTE), pluginDto("disabled.remote", PluginSource.REMOTE)),
                         pluginNods = listOf(pluginDto("custom.both", PluginSource.REMOTE), pluginDto("custom.remote", PluginSource.REMOTE))),
    )

    assertMergedPlugins(mergedUpdates.enabledUpdates, "enabled.local", "enabled.both", "enabled.remote")
    assertMergedPlugins(mergedUpdates.disabledUpdates, "disabled.local", "disabled.both", "disabled.remote")
    assertMergedPlugins(mergedUpdates.pluginNods, "custom.local", "custom.both", "custom.remote")
  }

  private fun mergeUpdates(first: PluginUpdatesEvent, second: PluginUpdatesEvent): PluginUpdatesEvent {
    return PluginUpdatesService(CoroutineScope(EmptyCoroutineContext)).mergeUpdates(listOf(first, second))
  }

  private fun assertMergedPlugins(plugins: List<PluginDto>, vararg pluginIds: String) {
    assertThat(plugins.map { it.pluginId.idString }).containsExactly(*pluginIds)
    assertThat(plugins.map { it.source }).containsExactly(PluginSource.LOCAL, PluginSource.BOTH, PluginSource.REMOTE)
  }

  private fun pluginDto(id: String, source: PluginSource): PluginDto =
    PluginDto(name = id, pluginId = PluginId.getId(id)).apply { this.source = source }

  @Test
  fun `a failing provider flow must not stop updates from other providers`(@TestDisposable disposable: Disposable): Unit =
    runTest {
      val healthy = MutableStateFlow<PluginUpdatesEvent?>(null)
      val failing = flow<PluginUpdatesEvent?> {
        throw RuntimeException("provider flow failed")
      }
      ExtensionTestUtil.maskExtensions(
        PluginUpdatesProvider.EP_NAME, listOf(FakeProvider(healthy), FakeProvider(failing)), disposable,
      )
      val service = PluginUpdatesService(backgroundScope)
      // A healthy provider now reports an update; it must still reach the subscriber.
      healthy.value = PluginUpdatesEvent(listOf(pluginDto("plugin.healthy", PluginSource.LOCAL)), emptyList(), emptyList())

      advanceUntilIdle()

      val received = withTimeout(1.seconds) { service.awaitUpdates() }
      assertThat(received).anySatisfy {
        assertThat( it.pluginId.idString == "plugin.healthy").isTrue()
      }
    }

  @Test
  fun `a provider that never emits must not block updates from other providers`(@TestDisposable disposable: Disposable): Unit =
    runTest {
      val healthy = MutableStateFlow<PluginUpdatesEvent?>(null)
      val silent = MutableSharedFlow<PluginUpdatesEvent>() // never emits
      ExtensionTestUtil.maskExtensions(
        PluginUpdatesProvider.EP_NAME, listOf(FakeProvider(healthy), FakeProvider(silent)), disposable,
      )

      val service = PluginUpdatesService(backgroundScope)
      healthy.value = PluginUpdatesEvent(listOf(pluginDto("plugin.healthy", PluginSource.LOCAL)), emptyList(), emptyList())

      advanceUntilIdle()

      val updates = withTimeout(1.seconds) { service.awaitUpdates() }
      assertThat(updates).anySatisfy {
        assertThat( it.pluginId.idString == "plugin.healthy").isTrue()
      }
    }

  @Test
  fun `subscribe delivers updates and late subscriber also receives updates`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val events = MutableStateFlow<PluginUpdatesEvent?>(null)
      ExtensionTestUtil.maskExtensions(PluginUpdatesProvider.EP_NAME, listOf(FakeProvider(events)), disposable)

      val serviceScope = childScope("PluginUpdatesServiceTest", Dispatchers.Default)
      try {
        val service = PluginUpdatesService(serviceScope)
        val earlyReceiver = CopyOnWriteArrayList<PluginUpdatesEvent>()
        service.subscribe { earlyReceiver.add(it) }
        events.value = PluginUpdatesEvent(listOf(pluginDto("plugin.first", PluginSource.LOCAL)), emptyList(), emptyList())

        waitUntil("early subscriber receives the plugin.first update", 1.seconds) {
          earlyReceiver.hasUpdateFor("plugin.first")
        }
        assertThat(earlyReceiver)
          .describedAs("early subscriber receives exactly one update once it is published")
          .hasSize(1)
        assertThat(earlyReceiver.single().all.map { it.pluginId.idString })
          .describedAs("the update delivered to the early subscriber is for plugin.first")
          .containsExactly("plugin.first")

        val lateReceived = CopyOnWriteArrayList<PluginUpdatesEvent>()
        service.subscribe { lateReceived.add(it) }
        assertThat(lateReceived)
          .describedAs("late subscriber immediately receives the current snapshot on subscribe")
          .hasSize(1)
        assertThat(lateReceived.single().all.map { it.pluginId.idString })
          .describedAs("late subscriber receives the same plugin.first update")
          .containsExactly("plugin.first")
      }
      finally {
        serviceScope.cancel()
      }
    }

  @Test
  fun `awaitUpdates and awaitHasUpdate reflect available updates`(@TestDisposable disposable: Disposable): Unit =
    runTest {
      val events = MutableStateFlow(eventWith("plugin.available"))
      ExtensionTestUtil.maskExtensions(PluginUpdatesProvider.EP_NAME, listOf(FakeProvider(events)), disposable)

      val service = PluginUpdatesService(backgroundScope)

      advanceUntilIdle()

      val updates = withTimeout(1.seconds) { service.awaitUpdates() }
      assertThat(updates.map { it.pluginId.idString }).contains("plugin.available")

      assertThat(service.awaitHasUpdate(PluginId.getId("plugin.available")))
        .describedAs("awaitHasUpdate is true for an available update").isTrue()
      assertThat(service.awaitHasUpdate(PluginId.getId("plugin.absent")))
        .describedAs("awaitHasUpdate is false for an unknown plugin").isFalse()
    }

  @Test
  fun `rerunCallbacks re-delivers the last snapshot to callbacks`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val events = MutableStateFlow(eventWith("plugin.snapshot"))
      ExtensionTestUtil.maskExtensions(PluginUpdatesProvider.EP_NAME, listOf(FakeProvider(events)), disposable)

      val serviceScope = childScope("PluginUpdatesServiceTest", Dispatchers.Default)
      try {
        val received = CopyOnWriteArrayList<PluginUpdatesEvent>()
        val service = PluginUpdatesService(serviceScope)
        service.subscribe { received.add(it) }
        waitUntil("subscriber receives the initial snapshot", 1.seconds) { received.hasUpdateFor("plugin.snapshot") }

        received.clear()
        service.rerunCallbacks()
        waitUntil("rerunCallbacks re-delivers the last snapshot", 1.seconds) { received.hasUpdateFor("plugin.snapshot") }
      }
      finally {
        serviceScope.cancel()
      }
    }

  @Test
  fun `recalculateUpdates re-runs provider update checks`(@TestDisposable disposable: Disposable): Unit =
    runTest {
      val nextResult = AtomicReference(emptyEvent())
      val events = MutableStateFlow<PluginUpdatesEvent?>(null)
      val provider = object : PluginUpdatesProvider {
        override suspend fun pluginUpdateEvents(): Flow<PluginUpdatesEvent?> = events
        override suspend fun update() {
          events.value = nextResult.get()
        }
      }
      ExtensionTestUtil.maskExtensions(PluginUpdatesProvider.EP_NAME, listOf(provider), disposable)

      val service = PluginUpdatesService(backgroundScope)

      // recalculateUpdates() runs the provider's update(), which publishes the staged result.
      nextResult.set(eventWith("plugin.first"))
      service.recalculateUpdates()
      advanceUntilIdle()
      withTimeout(1.seconds) {
        service.flow().filterNotNull().first { it.hasUpdateFor("plugin.first") }
      }

      // A second recalculateUpdates() re-runs the provider check and picks up the new result.
      nextResult.set(eventWith("plugin.second"))
      service.recalculateUpdates()
      advanceUntilIdle()
      val second = withTimeout(1.seconds) {
        service.flow().filterNotNull().first { it.hasUpdateFor("plugin.second") }
      }
      assertThat(second.all.map { it.pluginId.idString }).containsExactly("plugin.second")
    }

  @Test
  fun `incremental updates are delivered as each provider emits`(@TestDisposable disposable: Disposable): Unit =
    runTest {
      val providerA = MutableSharedFlow<PluginUpdatesEvent>(replay = 1)
      val providerB = MutableSharedFlow<PluginUpdatesEvent>(replay = 1)
      ExtensionTestUtil.maskExtensions(
        PluginUpdatesProvider.EP_NAME, listOf(FakeProvider(providerA), FakeProvider(providerB)), disposable,
      )

      val service = PluginUpdatesService(backgroundScope)

      // Provider A emits first; its update must reach the merged flow even though provider B has not emitted yet.
      providerA.emit(eventWith("plugin.a"))
      advanceUntilIdle()
      val afterA = withTimeout(1.seconds) {
        service.flow().filterNotNull().first { it.hasUpdateFor("plugin.a") }
      }
      assertThat(afterA.hasUpdateFor("plugin.b"))
        .describedAs("provider B's update must not be present before it emits")
        .isFalse()

      // Provider B emits; the merged result must now contain both providers' plugins.
      providerB.emit(eventWith("plugin.b"))
      advanceUntilIdle()
      withTimeout(1.seconds) {
        service.flow().filterNotNull().first { it.hasUpdateFor("plugin.a") && it.hasUpdateFor("plugin.b") }
      }
    }

  private class FakeProvider(private val events: Flow<PluginUpdatesEvent?>) : PluginUpdatesProvider {
    override suspend fun pluginUpdateEvents(): Flow<PluginUpdatesEvent?> = events
    override suspend fun update() {}
  }

  private fun emptyEvent(): PluginUpdatesEvent = PluginUpdatesEvent(emptyList(), emptyList(), emptyList())

  private fun eventWith(vararg pluginIds: String): PluginUpdatesEvent =
    PluginUpdatesEvent(pluginIds.map { pluginDto(it, PluginSource.LOCAL) }, emptyList(), emptyList())

  private fun PluginUpdatesEvent.hasUpdateFor(pluginId: String): Boolean =
    all.any { it.pluginId.idString == pluginId }

  private fun Collection<PluginUpdatesEvent>.hasUpdateFor(pluginId: String): Boolean =
    any { it.hasUpdateFor(pluginId) }
}
