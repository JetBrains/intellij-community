// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.providers.BaseLayerService
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the refreshes of [BaseLayerService]: their order within one storage unit, and that a refresh of one locality
 * reads and pushes only that locality. A test service holds its store in memory and can hold a read after it took the
 * data. The refreshes run on one thread, so the test knows when the work of a refresh is done.
 */
@TestApplication
class LayerRefreshOrderTest {
  companion object {
    private val key = SettingsKey("test.refreshOrder.key", default = "def")
    private val fence = SettingsKey("test.refreshOrder.fence", default = "def")
    private val other = SettingsKey("test.refreshOrder.other", default = "def")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()
  private val app = SettingsContext.app()

  @Test
  fun `a refresh that read an older state does not push after a refresh that read a newer state`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    app.awaitReady()
    val refreshDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val refreshScope = CoroutineScope(SupervisorJob() + refreshDispatcher)
    try {
      val service = HeldReadLayerService(refreshScope)
      service.stored[LayerLocality.TEAM] = MapValue(mapOf(key.identity to StringValue("old")))
      val heldRead = service.holdNextRead()

      service.requestRefresh(app, LayerLocality.TEAM)
      withTimeout(10.seconds) { heldRead.dataTaken.await() }
      service.stored[LayerLocality.TEAM] = MapValue(emptyMap())
      service.requestRefresh(app, LayerLocality.TEAM)
      heldRead.release.complete(Unit)

      // The second read and its pushes run in one task of the refresh thread, so the hop waits until they are done.
      awaitEquals(2) { service.reads(LayerLocality.TEAM) }
      withContext(refreshDispatcher) {}
      // The vault takes the pushes in order, so the fence applies after every push of the refreshes. The fence goes to
      // another locality, because a seed replaces the whole layer of its locality.
      provider.seed(app, fence, "fenced", LayerLocality.LOCAL)
      awaitEquals("fenced") { fence[app] }

      assertEquals("def", key[app])
    }
    finally {
      refreshScope.cancel()
      refreshDispatcher.close()
    }
  }

  @Test
  fun `a refresh of one locality reads and pushes only that locality`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    app.awaitReady()
    val refreshDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val refreshScope = CoroutineScope(SupervisorJob() + refreshDispatcher)
    try {
      val service = HeldReadLayerService(refreshScope)
      service.stored[LayerLocality.TEAM] = MapValue(mapOf(key.identity to StringValue("team")))
      service.stored[LayerLocality.ENVIRONMENTAL] = MapValue(mapOf(other.identity to StringValue("environmental")))

      service.requestRefresh(app, LayerLocality.TEAM)
      awaitEquals("team") { key[app] }
      // The read and the pushes of the refresh run in one task of the refresh thread, so the hop waits until they are done.
      withContext(refreshDispatcher) {}

      assertEquals(0, service.reads(LayerLocality.ENVIRONMENTAL))
      assertEquals("def", other[app])
    }
    finally {
      refreshScope.cancel()
      refreshDispatcher.close()
    }
  }

  /**
   * Backs [TestLayerProvider] at the application with the in-memory [stored] layers of the [LayerLocality.TEAM] and the
   * [LayerLocality.ENVIRONMENTAL] locality. It counts the [reads] of each locality and can hold one read after it took
   * the data.
   */
  private class HeldReadLayerService(scope: CoroutineScope) : BaseLayerService(scope) {
    override val localities: List<LayerLocality> = listOf(LayerLocality.TEAM, LayerLocality.ENVIRONMENTAL)

    override val providerClass: Class<out LayerProvider> = TestLayerProvider::class.java

    /** Holds the layer of each locality. A locality with no entry has an empty layer. */
    val stored: MutableMap<LayerLocality, MapValue> = ConcurrentHashMap()

    private val readCounts = ConcurrentHashMap<LayerLocality, AtomicInteger>()

    /** Returns how many times a [read] of [locality] took its data. */
    fun reads(locality: LayerLocality): Int = readCounts[locality]?.get() ?: 0

    @Volatile
    private var heldRead: HeldRead? = null

    /**
     * Holds the next [read] after it took the [stored] data and before it returns.
     *
     * @return the handle that reports the taken data and releases the read
     */
    fun holdNextRead(): HeldRead = HeldRead().also { heldRead = it }

    /** Requests a [refresh] of [storageContext] for [locality]. */
    fun requestRefresh(storageContext: SettingsContext, locality: LayerLocality) = refresh(storageContext, locality)

    override suspend fun read(storageContext: SettingsContext, locality: LayerLocality, preferVfsContent: Boolean): MapValue {
      val data = stored[locality] ?: MapValue(emptyMap())
      readCounts.computeIfAbsent(locality) { AtomicInteger() }.incrementAndGet()
      heldRead?.let { held ->
        heldRead = null
        held.dataTaken.complete(Unit)
        held.release.await()
      }
      return data
    }

    override suspend fun write(storageContext: SettingsContext, locality: LayerLocality, data: MapValue) {}

    override fun availableLocalities(context: SettingsContext): Set<LayerLocality> = emptySet()

    override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> = emptyList()
  }

  /** Reports that a held read took its data, and releases it. See [HeldReadLayerService.holdNextRead]. */
  private class HeldRead {
    /** Completes when the held read has taken the data. */
    val dataTaken: CompletableDeferred<Unit> = CompletableDeferred()

    /** Lets the held read return when completed. */
    val release: CompletableDeferred<Unit> = CompletableDeferred()
  }
}
