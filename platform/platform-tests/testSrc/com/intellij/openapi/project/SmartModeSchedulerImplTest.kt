// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicInteger

/**
 * Checks that the project [SmartModeScheduler] takes its mode from the first [SmartModeProvider].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class SmartModeSchedulerImplTest {
  companion object {
    @ClassRule
    @JvmField
    val p: ProjectRule = ProjectRule(true, false, null)
  }

  @Rule
  @JvmField
  val edt: EdtRule = EdtRule()

  @Rule
  @JvmField
  val disposableRule: DisposableRule = DisposableRule()

  private val project: Project get() = p.project
  private val scheduler: SmartModeScheduler get() = project.service<SmartModeScheduler>()

  /** Reports the mode of its flow. */
  private class FakeProvider(mode: Int) : SmartModeProvider {
    val mode: MutableStateFlow<Int> = MutableStateFlow(mode)

    override fun getCurrentMode(project: Project): Int = mode.value

    override fun modeChanged(project: Project): Flow<*> = mode
  }

  /** Reports one mode and never a change. */
  private class ConstantProvider(private val mode: Int) : SmartModeProvider {
    override fun getCurrentMode(project: Project): Int = mode

    override fun modeChanged(project: Project): Flow<*> = emptyFlow<Nothing>()
  }

  /** Uses the scanner of the project, but reports its own scanning state. */
  private class FakeScanner(scanner: UnindexedFilesScannerExecutor, running: Boolean) : UnindexedFilesScannerExecutor by scanner {
    override val isRunning: MutableStateFlow<Boolean> = MutableStateFlow(running)
    override val startedOrStoppedEvent: Flow<*> get() = isRunning
  }

  @Before
  fun setUp() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    // The service is lazy. Create it before a test masks the providers.
    scheduler.getCurrentMode()
  }

  /** Masks the providers until [parentDisposable] is disposed. A mask must end before the next one starts. */
  private fun useProviders(vararg providers: SmartModeProvider, parentDisposable: Disposable = disposableRule.disposable) {
    ExtensionTestUtil.maskExtensions(SmartModeProvider.EP_NAME, providers.toList(), parentDisposable)
  }

  /** Schedules a runnable that checks the EDT contract, and returns its run counter. */
  private fun schedule(): AtomicInteger {
    val runs = AtomicInteger()
    scheduler.runWhenSmart {
      ThreadingAssertions.assertEventDispatchThread()
      assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed)
      runs.incrementAndGet()
    }
    return runs
  }

  private fun assertHeld(runs: AtomicInteger) {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertEquals(0, runs.get())
  }

  private fun awaitRun(runs: AtomicInteger) {
    PlatformTestUtil.waitWithEventsDispatching("The runnable did not run", { runs.get() == 1 }, 10)
  }

  @Test
  fun `a dumb provider holds the runnable until it reports smart mode`() {
    val provider = FakeProvider(SmartModeScheduler.DUMB)
    useProviders(provider)
    assertEquals(SmartModeScheduler.DUMB, scheduler.getCurrentMode())
    val runs = schedule()
    assertHeld(runs)

    provider.mode.value = 0
    awaitRun(runs)
    assertEquals(0, scheduler.getCurrentMode())
  }

  @Test
  fun `the first provider supplies the mode`() {
    val dumbFirst = Disposer.newDisposable(disposableRule.disposable)
    useProviders(ConstantProvider(SmartModeScheduler.DUMB), ConstantProvider(0), parentDisposable = dumbFirst)
    assertEquals(SmartModeScheduler.DUMB, scheduler.getCurrentMode())
    Disposer.dispose(dumbFirst)

    useProviders(ConstantProvider(0), ConstantProvider(SmartModeScheduler.DUMB))
    assertEquals(0, scheduler.getCurrentMode())
  }

  @Test
  fun `a change of the providers runs the held runnable`() {
    // The provider never emits, so only the change of the providers can release the runnable.
    val dumbProvider = Disposer.newDisposable(disposableRule.disposable)
    useProviders(ConstantProvider(SmartModeScheduler.DUMB), parentDisposable = dumbProvider)
    val runs = schedule()
    assertHeld(runs)

    Disposer.dispose(dumbProvider)
    awaitRun(runs)
  }

  @Test
  fun `the mode is smart without a provider`() {
    useProviders()
    assertEquals(0, scheduler.getCurrentMode())
    var runs = 0
    scheduler.runWhenSmart { runs++ }
    assertEquals(1, runs)
  }

  @Test
  fun `the default project is always smart`() {
    useProviders(ConstantProvider(SmartModeScheduler.DUMB))
    val defaultProject = ProjectManager.getInstance().defaultProject
    assertEquals(0, defaultProject.service<SmartModeScheduler>().getCurrentMode())
  }

  @Test
  fun `the scheduler stops the collection of a replaced provider`() {
    val provider = FakeProvider(SmartModeScheduler.DUMB)
    val providerDisposable = Disposer.newDisposable(disposableRule.disposable)
    useProviders(provider, parentDisposable = providerDisposable)
    // The scheduler of the default project can collect too.
    PlatformTestUtil.waitWithEventsDispatching("The scheduler did not collect the provider", { provider.mode.subscriptionCount.value > 0 }, 10)

    Disposer.dispose(providerDisposable)
    PlatformTestUtil.waitWithEventsDispatching("The scheduler did not stop the collection", { provider.mode.subscriptionCount.value == 0 }, 10)
  }

  @Test
  fun `the registered provider holds the runnable in dumb mode`() {
    val token = DumbModeTestUtils.startEternalDumbModeTask(project)
    val runs = try {
      assertEquals(SmartModeScheduler.DUMB, scheduler.getCurrentMode())
      schedule().also { assertHeld(it) }
    }
    finally {
      DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token)
    }
    awaitRun(runs)
    assertEquals(0, scheduler.getCurrentMode())
  }

  @Test
  fun `the registered provider holds the runnable while the scanning runs`() {
    val scanner = FakeScanner(UnindexedFilesScannerExecutor.getInstance(project), running = true)
    project.replaceService(UnindexedFilesScannerExecutor::class.java, scanner, disposableRule.disposable)
    // The same providers again, so the scheduler collects the fake scanner.
    useProviders(*SmartModeProvider.EP_NAME.extensionList.toTypedArray())
    assertEquals(SmartModeScheduler.SCANNING, scheduler.getCurrentMode())
    val runs = schedule()
    assertHeld(runs)

    scanner.isRunning.value = false
    awaitRun(runs)
  }
}
