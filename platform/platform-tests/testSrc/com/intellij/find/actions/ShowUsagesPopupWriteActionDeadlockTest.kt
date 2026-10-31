// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.Language
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.AbstractPopup
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageSearchPresentation
import com.intellij.usages.UsageSearcher
import com.intellij.util.Processor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Point
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

private const val TIMEOUT_SECONDS = 30L

class ShowUsagesPopupWriteActionDeadlockTest : BasePlatformTestCase() {
  fun testPopupRebuildDoesNotDeadlockWithPendingWriteAction() {
    val file = myFixture.configureByText("a.txt", "usage")
    val firstUsage = UsageInfo2UsageAdapter(UsageInfo(file))
    val secondUsage = UsageInfo2UsageAdapter(UsageInfo(file))

    val firstUsageAdded = CountDownLatch(1)
    val secondUsageAllowed = CountDownLatch(1)

    val producer = AtomicReference<Thread>()

    val handler = TestHandler(project) { processor ->
      processor.process(firstUsage)
      firstUsageAdded.countDown()
      secondUsageAllowed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      producer.set(Thread.currentThread())
      processor.process(secondUsage)
    }

    val scope = CoroutineScope(Dispatchers.Default)
    val watchdogFired = AtomicBoolean()
    lateinit var result: Future<Collection<Usage>>
    try {
      // The EDT holds write-intent while it runs the popup rebuild, so the pending write action waits for the EDT.
      WriteIntentReadAction.run {
        val parameters = ShowUsagesParameters.initial(project, null, RelativePoint(JPanel(), Point()))
        result = ShowUsagesAction.showElementUsagesWithResult(parameters, handler, handler.createUsageView(project))
        // The first usage pings the popup rebuild. The EDT does not dispatch events until the cycle is set up.
        assertTrue(firstUsageAdded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val writeAction = scope.launch { backgroundWriteAction {} }
        waitUntil { ApplicationManagerEx.getApplicationEx().isWriteActionPending }

        secondUsageAllowed.countDown()
        waitUntil { producer.get()?.let { it.state == Thread.State.WAITING } == true }

        val watchdog = scope.launch {
          delay(TIMEOUT_SECONDS.seconds)
          watchdogFired.set(true)
          writeAction.cancel()
        }
        val earlierEventsDispatched = AtomicBoolean()
        SwingUtilities.invokeLater { earlierEventsDispatched.set(true) }
        while (!earlierEventsDispatched.get()) {
          PlatformTestUtil.dispatchNextEventIfAny()
        }
        watchdog.cancel()
      }
      assertFalse("Deadlock detected", watchdogFired.get())
      PlatformTestUtil.waitForFuture(result, TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
    }
    finally {
      scope.cancel()
      handler.popup?.cancel()
    }
  }

  private fun waitUntil(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
    while (!condition()) {
      check(System.nanoTime() < deadline) { "The condition is not met in $TIMEOUT_SECONDS s" }
      Thread.onSpinWait()
    }
  }
}

private class TestHandler(private val project: Project, private val generate: (Processor<in Usage>) -> Unit) : ShowUsagesActionHandler {
  var popup: AbstractPopup? = null

  override fun isValid(): Boolean = true

  override fun getPresentation(): UsageSearchPresentation = object : UsageSearchPresentation {
    override fun getSearchTargetString(): String = "test"
    override fun getOptionsString(): String = ""
  }

  override fun createUsageSearcher(): UsageSearcher = UsageSearcher { generate(it) }

  override fun findUsages() {}

  override fun showDialog(): ShowUsagesActionHandler? = null

  override fun withScope(searchScope: SearchScope): ShowUsagesActionHandler? = null

  override fun moreUsages(parameters: ShowUsagesParameters): ShowUsagesParameters? = null

  // With the everything scope, ShowUsagesAction does not take a read action before it collects a usage.
  override fun getSelectedScope(): SearchScope = GlobalSearchScope.everythingScope(project)

  override fun getMaximalScope(): SearchScope = GlobalSearchScope.everythingScope(project)

  override fun getTargetLanguage(): Language? = null

  override fun getTargetClass(): Class<*> = javaClass

  override fun getEventData(): List<EventPair<*>> = mutableListOf()

  override fun navigateToSingleUsageImmediately(): Boolean = false

  override fun buildFinishEventData(selectedUsage: UsageInfo?): List<EventPair<*>> = mutableListOf()

  override fun afterOpen(popup: AbstractPopup) {
    this.popup = popup
  }
}
