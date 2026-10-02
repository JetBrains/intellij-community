// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.useBackgroundWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Tests that [CodeInsightTestFixtureImpl.invokeIntention] returns only after the intention and the runnables it queued
 * have run (IJPL-257006).
 *
 * The test holds the write-intent lock on the EDT, so a background write action that it starts stays pending, and the
 * EDT queue holds back every `invokeLater` runnable until that write action ends. Each `test*` method only calls its
 * `doTest*` body, because JUnit 3 reports the synthetic `test...$lambda$N` method of a Kotlin lambda as a non-public test.
 */
class InvokeIntentionUnderBackgroundWriteActionTest : BasePlatformTestCase() {

  override fun shouldRunTest(): Boolean {
    return super.shouldRunTest() && useBackgroundWriteAction
  }

  /**
   * A background write action is pending before the intention runs (IJPL-257006); `invokeIntention` must release the
   * write-intent lock while it waits.
   */
  fun testIntentionRunsWhileBackgroundWriteActionIsPending() {
    this.doTestIntentionRunsWhileBackgroundWriteActionIsPending()
  }

  /**
   * The intention queues a runnable, and a background write action becomes pending before that runnable runs. Swift
   * create-from-usage fixes start their template in such a runnable.
   */
  fun testRunnableQueuedByIntentionRunsWhenWriteActionBecomesPending() {
    this.doTestRunnableQueuedByIntentionRuns()
  }

  fun testExceptionFromIntentionReachesCaller() {
    this.doTestExceptionFromIntentionReachesCaller()
  }

  private fun doTestIntentionRunsWhileBackgroundWriteActionIsPending() {
    this.myFixture.configureByText("a.txt", "<caret>before")
    val intention = ReplaceTextIntention("after")
    val writeActionRan = AtomicBoolean()
    val writeActionFinished = CompletableFuture<Unit>()
    startPendingBackgroundWriteAction(100, writeActionRan, writeActionFinished)
    try {
      this.myFixture.launchAction(intention)

      assertEquals("launchAction returned before the intention ran", 1, intention.invocationCount.get())
      assertTrue("The background write action did not run, so the case was not reproduced", writeActionRan.get())
      this.myFixture.checkResult("after")
      writeActionFinished.get(30, TimeUnit.SECONDS)
    }
    finally {
      // after a failure the intention is still queued; run it while the editor exists
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }

  private fun doTestRunnableQueuedByIntentionRuns() {
    this.myFixture.configureByText("a.txt", "<caret>text")
    val intention = QueueingIntention()

    this.myFixture.launchAction(intention)

    assertTrue("launchAction returned while the EDT queue held back a runnable that the intention queued",
               intention.queuedRunnableExecuted.get())
    assertTrue("The queued runnable ran before the write action, so the case was not reproduced",
               intention.queuedRunnableRanAfterWriteAction.get())
    intention.writeActionFinished.get(30, TimeUnit.SECONDS)
  }

  private fun doTestExceptionFromIntentionReachesCaller() {
    this.myFixture.configureByText("a.txt", "<caret>text")
    assertThrows(IllegalStateException::class.java, ThrowingIntention.MESSAGE, ThrowableRunnable<Throwable> {
      this.myFixture.launchAction(ThrowingIntention())
    })
  }
}

/**
 * Starts a background write action on a new thread and returns when a write action is pending. The write action sets
 * [ran], sleeps for [holdMillis] and then completes [finished].
 */
private fun startPendingBackgroundWriteAction(holdMillis: Long, ran: AtomicBoolean, finished: CompletableFuture<Unit>) {
  thread(name = "IJPL-257006 background write action") {
    try {
      runBlocking {
        backgroundWriteAction {
          ran.set(true)
          TimeoutUtil.sleep(holdMillis)
        }
      }
      finished.complete(Unit)
    }
    catch (t: Throwable) {
      finished.completeExceptionally(t)
    }
  }
  waitUntilWriteActionIsPending()
}

/**
 * Waits up to 10 seconds until a write action is pending.
 */
private fun waitUntilWriteActionIsPending() {
  val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
  while (!ApplicationManagerEx.getApplicationEx().isWriteActionPending) {
    check(System.nanoTime() < deadlineNs) { "The background write action did not become pending" }
    Thread.onSpinWait()
  }
}

/**
 * Intention that replaces the document text with [newText] and counts its runs.
 */
private class ReplaceTextIntention(private val newText: String) : IntentionAction {
  val invocationCount: AtomicInteger = AtomicInteger()

  override fun getText(): String {
    return "Replace text"
  }

  override fun getFamilyName(): String {
    return this.text
  }

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    this.invocationCount.incrementAndGet()
    editor!!.document.setText(this.newText)
  }

  /**
   * `true`, so the platform makes the read-only file writable before [invoke].
   */
  override fun startInWriteAction(): Boolean {
    return true
  }
}

/**
 * Intention that queues two runnables. The first starts a pending background write action, so the EDT queue holds the
 * second one back until that write action ends.
 */
private class QueueingIntention : IntentionAction {
  val writeActionRan: AtomicBoolean = AtomicBoolean()
  val writeActionFinished: CompletableFuture<Unit> = CompletableFuture()
  val queuedRunnableExecuted: AtomicBoolean = AtomicBoolean()
  val queuedRunnableRanAfterWriteAction: AtomicBoolean = AtomicBoolean()

  override fun getText(): String {
    return "Queue runnables"
  }

  override fun getFamilyName(): String {
    return this.text
  }

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    val application = ApplicationManager.getApplication()
    application.invokeLater {
      startPendingBackgroundWriteAction(200, this.writeActionRan, this.writeActionFinished)
    }
    application.invokeLater {
      this.queuedRunnableRanAfterWriteAction.set(this.writeActionRan.get())
      this.queuedRunnableExecuted.set(true)
    }
  }

  override fun startInWriteAction(): Boolean {
    return false
  }
}

/**
 * Intention that throws [IllegalStateException] from [invoke].
 */
private class ThrowingIntention : IntentionAction {
  override fun getText(): String {
    return "Throw"
  }

  override fun getFamilyName(): String {
    return this.text
  }

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    throw IllegalStateException(MESSAGE)
  }

  override fun startInWriteAction(): Boolean {
    return false
  }

  companion object {
    const val MESSAGE: String = "intention failed"
  }
}
