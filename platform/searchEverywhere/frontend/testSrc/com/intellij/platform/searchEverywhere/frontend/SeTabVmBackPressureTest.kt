// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeResultSkippedEvent
import com.intellij.platform.searchEverywhere.frontend.vm.SeTabVm
import com.intellij.platform.searchEverywhere.frontend.vm.SeTabVmImpl
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Checks that [SeTabVmImpl] reports the back pressure each time its results start to wait for the list.
 *
 * The popup tells the search listeners that the search paused only after this report.
 */
@TestApplication
class SeTabVmBackPressureTest {
  @Test
  fun `tab reports back pressure when the list stops the load during the result flow`(): Unit = timeoutRunBlocking(TEST_TIMEOUT) {
    val popupScope = childScope("SeTabVmBackPressureTest", Dispatchers.Default.limitedParallelism(STRESS_PARALLELISM))
    try {
      val tabs = List(TAB_COUNT) { index -> popupScope.startEndlessSearch("tab$index") }
      tabs.forEach { it.waitForFirstReport() }

      repeat(ROUND_COUNT) {
        tabs.forEach { it.loadMoreAndStop() }
        tabs.forEach { it.assertBackPressureReported() }
      }
    }
    finally {
      popupScope.cancel()
    }
  }
}

private fun CoroutineScope.startEndlessSearch(tabId: String): RunningSearch {
  val vm = SeTabVmImpl(
    project = null,
    coroutineScope = this,
    tab = EndlessTab(tabId),
    customizedTabInfo = SeTabInfo(0, tabId),
    searchPattern = MutableStateFlow("pattern"),
    availableLegacyContributors = emptyMap(),
  )
  val search = RunningSearch(vm)

  launch {
    vm.resultsHitBackPressureFlow.collect { search.reports.send(Unit) }
  }
  launch {
    vm.searchResults.filterNotNull().first().resultsFlow.collect { search.loadedCount.incrementAndGet() }
  }
  vm.setActive(true)
  vm.shouldLoadMore = false
  return search
}

private class RunningSearch(private val vm: SeTabVm) {
  val reports = Channel<Unit>(Channel.CONFLATED)
  val loadedCount = AtomicLong()

  /** The first report shows that the collector of the reports is subscribed. A report before that moment has no receiver. */
  suspend fun waitForFirstReport() {
    repeat(FIRST_REPORT_ATTEMPTS) {
      loadMoreAndStop()
      if (withTimeoutOrNull(1.seconds) { reports.receive() } != null) return
    }
    fail("${vm.tabId} reported no back pressure in $FIRST_REPORT_ATTEMPTS attempts")
  }

  /** Stops the load at a moment when the results flow to the list. */
  suspend fun loadMoreAndStop() {
    reports.tryReceive()
    val loadedBefore = loadedCount.get()
    vm.shouldLoadMore = true
    val loaded = withTimeoutOrNull(LOAD_TIMEOUT) {
      while (loadedCount.get() < loadedBefore + LOADED_BEFORE_STOP) {
        delay(1.milliseconds)
      }
    }
    assertNotNull(loaded, "${vm.tabId} loaded less than $LOADED_BEFORE_STOP results in $LOAD_TIMEOUT")
    vm.shouldLoadMore = false
  }

  suspend fun assertBackPressureReported() {
    val report = withTimeoutOrNull(REPORT_TIMEOUT) { reports.receive() }
    assertNotNull(report, "The results wait for the list, but ${vm.tabId} did not report the back pressure")
  }
}

private class EndlessTab(override val id: String) : SeTab {
  override val name: String get() = id
  override val priority: Int get() = 0

  override fun getItems(params: SeParams): Flow<SeResultEvent> = flow {
    val event = SeResultSkippedEvent(SeProviderId(id))
    while (true) {
      emit(event)
      yield()
    }
  }

  override suspend fun getFilterEditor(): SeFilterEditor? = null
  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean = false
  override suspend fun canBeShownInFindResults(): Boolean = false
  override suspend fun performExtendedAction(item: SeItemData): Boolean = false
  override suspend fun isPreviewEnabled(): Boolean = false
  override suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo? = null
  override suspend fun isExtendedInfoEnabled(): Boolean = false
  override suspend fun isCommandsSupported(): Boolean = false
  override fun dispose() {}
}

private const val TAB_COUNT = 32
private const val ROUND_COUNT = 20
private const val LOADED_BEFORE_STOP = 100

/**
 * The result flows of the tabs spin in `emit(); yield()` loops. They share this many workers of [Dispatchers.Default],
 * so the other workers stay free for the rest of the application.
 */
private const val STRESS_PARALLELISM = 1

/** The collector of the reports subscribes asynchronously. Each attempt gives it one second. */
private const val FIRST_REPORT_ATTEMPTS = 30

/** A tab yields after each result, so the results arrive within milliseconds while the load is on. */
private val LOAD_TIMEOUT = 10.seconds

/** The tab reports the back pressure 300 ms after the results start to wait. */
private val REPORT_TIMEOUT = 10.seconds

/** The test takes about 20 s. The limit stays below the 300 s limit of the Bazel target. */
private val TEST_TIMEOUT = 2.minutes
