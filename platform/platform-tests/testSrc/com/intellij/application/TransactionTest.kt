// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application

import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ThrowableRunnable
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.Test
import java.awt.Dialog
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities

private val guard: TransactionGuardImpl
  get() = TransactionGuard.getInstance() as TransactionGuardImpl

private val app: Application?
  get() = ApplicationManager.getApplication()

class TransactionTest : LightPlatformTestCase() {
  private val log = mutableListOf<String>()

  override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
    if (app != null) {
      guard.assertWriteActionAllowed()
    }

    SwingUtilities.invokeLater(testRunnable::run)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  override fun setUp() {
    super.setUp()
    assert(LaterInvocator.getCurrentModalityState() == ModalityState.NON_MODAL)
  }

  override fun tearDown() {
    try {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      log.clear()
      LaterInvocator.leaveAllModals()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `test write action without transaction prohibited`() {
    val app = app!!
    assertThat(app.isDispatchThread).isTrue()
    assertThat(app.isWriteAccessAllowed).isFalse()

    assertWritingProhibited()
    SwingUtilities.invokeLater(::assertWritingProhibited)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  fun `test write action allowed inside user activity but not in modal dialog shown from non-modal invokeLater`() {
    SwingUtilities.invokeLater {
      guard.performUserActivity { app!!.runWriteAction { log.add("1") } }

      LaterInvocator.enterModal(Object())
      guard.performUserActivity {
        assertWritingProhibited()
        log.add("2")
      }
      LaterInvocator.leaveAllModals()

      guard.performUserActivity { app!!.runWriteAction { log.add("3") } }
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).containsExactly("1", "2", "3")
  }

  private fun assertWritingProhibited() {
    var writeActionFailed = false
    val disposable = Disposer.newDisposable("assertWritingProhibited")
    DefaultLogger.disableStderrDumping(disposable)
    try {
      app!!.runWriteAction(::makeRootsChange)
    }
    catch (ignore: AssertionError) {
      writeActionFailed = true
    }
    finally {
      Disposer.dispose(disposable)
    }
    assertThat(writeActionFailed).describedAs("write action should fail $guard").isTrue()
  }

  private fun makeRootsChange() {
    ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
  }

  fun `test parent disposable`() {
    val parent = Disposer.newDisposable()

    SwingUtilities.invokeLater { TransactionGuard.submitTransaction(parent) { log.add("1") } }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assert(log == listOf("1"))

    Disposer.dispose(parent)
    SwingUtilities.invokeLater { TransactionGuard.submitTransaction(parent) { log.add("2") } }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assert(log == listOf("1"))
  }

  fun `test no context transaction inside invokeLater`() {
    TransactionGuard.submitTransaction(testRootDisposable) {
      SwingUtilities.invokeLater {
        log.add("2")
          assertThat(guard.contextTransaction).isNull()
      }
      log.add("1")
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      log.add("3")
    }
    assertThat(log).isEmpty() // the test is also run inside an invokeLater, so transaction is asynchronous
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).containsExactly("1", "2", "3")
  }

  fun `test has id inside nested transaction`() {
    TransactionGuard.submitTransaction(testRootDisposable) {
      log.add("1")
      val id = guard.contextTransaction
      assertThat(id).isNotNull()
      TransactionGuard.submitTransaction(testRootDisposable) {
        log.add("2")
        assertThat(guard.contextTransaction).isNotNull()
      }
      assertThat(log).containsExactly("1", "2")
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).containsExactly("1", "2")
  }

  fun `test no id on pooled thread`() {
    TransactionGuard.submitTransaction(testRootDisposable) {
      log.add("1")
      ApplicationManager.getApplication().executeOnPooledThread {
        assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
        assertThat(guard.contextTransaction).isNull()
        log.add("2")
      }.get()
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).containsExactly("1", "2")
  }

  fun `test submit with finished transaction id`() {
    TransactionGuard.submitTransaction(testRootDisposable) {
      log.add("1")
      var id: TransactionId? = null
      TransactionGuard.submitTransaction(testRootDisposable) {
        log.add("2")
        TransactionGuard.submitTransaction(testRootDisposable) {
          log.add("3")
          id = guard.contextTransaction
        }
      }
      SwingUtilities.invokeLater {
        guard.submitTransaction(testRootDisposable, id) { log.add("4") }
      }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      assertThat(log).containsExactly("1", "2", "3", "4")
    }
  }

  fun `test write access in modal invokeLater`() {
    LaterInvocator.enterModal(Object())
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val unsafeModality = ModalityState.current()

    TransactionGuard.submitTransaction(testRootDisposable) {
      log.add("1")

      val innerModal = Object()
      LaterInvocator.enterModal(innerModal)
      val safeModality = ModalityState.current()
      val app = app!!
      app.executeOnPooledThread {
        app.invokeLater({ app.runWriteAction { log.add("2") } }, safeModality)
        app.invokeLater({
                          assertWritingProhibited()
                          log.add("4")
                        }, unsafeModality)
        app.invokeLater({
                          assertWritingProhibited()
                          app.runWriteAction { log.add("3") }
                        }, ModalityState.any())
        app.invokeLater({ app.runWriteAction { log.add("5") } }, ModalityState.NON_MODAL)
      }.get()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      LaterInvocator.leaveModal(innerModal)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    LaterInvocator.leaveAllModals()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).containsExactly("1", "2", "3", "4", "5")
  }

  fun `test no synchronous transactions inside invokeLater`() {
    DefaultLogger.disableStderrDumping(testRootDisposable)
    SwingUtilities.invokeLater {
      log.add("1")
      try {
        guard.submitTransactionAndWait { log.add("not run") }
      }
      catch (ignore: AssertionError) {
        log.add("assert")
      }
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).containsExactly("1", "assert")
  }

  fun `test progress created on EDT and run on pooled thread`() {
    TransactionGuard.submitTransaction(testRootDisposable) {
      val progress = ProgressWindow(true, project)

      val process = {
        log.add("1")
        assertThat(progress.modalityState).isNotEqualTo(ModalityState.NON_MODAL)

        val writeAction = Runnable {
          makeRootsChange()
          log.add("2")
        }
        app!!.invokeLater({ app!!.runWriteAction(writeAction) }, progress.modalityState)
      }

      app!!.executeOnPooledThread { ProgressManager.getInstance().runProcess(process, progress) }.get()
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).containsExactly("1", "2")
  }

  fun `test nested background progresses`() {
    TransactionGuard.submitTransaction(testRootDisposable, Runnable {
      log.add("1")
      val id = guard.contextTransaction
      assertThat(id).isNotNull()
      ProgressManager.getInstance().runProcessWithProgressSynchronously({
        ProgressManager.getInstance().runProcess({
                                                   assertThat(ApplicationManager.getApplication().isDispatchThread).isFalse()
                                                   log.add("2")
                                                 }, ProgressIndicatorBase())
      }, "title", true, project)
    })
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).containsExactly("1", "2")
  }

  fun `test submitTransactionLater vs app invokeLater ordering in the same modality state`() {
    TransactionGuard.submitTransaction(testRootDisposable) {
      log.add("1")
      guard.submitTransactionLater(testRootDisposable) { log.add("2") }
      app!!.invokeLater { log.add("3") }
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).containsExactly("1", "2", "3")
  }

  fun `test submitTransaction does not go inside modal dialog`() {
    TransactionGuard.submitTransaction(testRootDisposable) {
      log.add("1")
    }
    LaterInvocator.enterModal(Object())
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log).isEmpty()
  }

  fun `test writing is allowed inside invokeLater on not yet shown modal dialog component`() {
    Assume.assumeFalse("Can't run in headless environment", GraphicsEnvironment.isHeadless())

    app!!.invokeLater {
      assertThat(guard.isWritingAllowed).isTrue()
      val dialog = Dialog(null, true)
      app!!.invokeLater(assertThat(guard.isWritingAllowed)::isTrue, ModalityState.stateForComponent(dialog))
      LaterInvocator.enterModal(dialog)
      log.add("x")
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(log.isEmpty()).isFalse()
  }
}
