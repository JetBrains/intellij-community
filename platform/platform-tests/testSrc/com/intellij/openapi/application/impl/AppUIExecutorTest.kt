// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import java.util.concurrent.LinkedBlockingQueue
import javax.swing.SwingUtilities

/**
 * @author eldar
 */
class AppUIExecutorTest : LightPlatformTestCase() {
  override fun runInDispatchThread() = false

  override fun invokeTestRunnable(runnable: Runnable) {
    SwingUtilities.invokeLater(runnable)
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun Deferred<Unit>.joinNonBlocking() {
    var countDown = 300
    object : Runnable {
      override fun run() {
        if (!isCompleted) {
          if (countDown-- <= 0) fail("Too many EDT reschedules")
          SwingUtilities.invokeLater(this)
          return
        }
        getCompleted()
      }
    }.run()
  }

  fun `test coroutine onUiThread`() {
    val executor = AppUIExecutor.onUiThread(ModalityState.any())
    async((executor as AppUIExecutorEx).createJobContext()) {
      ApplicationManager.getApplication().assertIsDispatchThread()
    }.joinNonBlocking()
  }

  fun `test coroutine withExpirable`() {
    val queue = LinkedBlockingQueue<String>()
    val disposable = Disposable {
      queue.add("disposed")
    }.also { Disposer.register(testRootDisposable, it) }

    val executor = AppUIExecutor.onUiThread(ModalityState.any())
      .expireWith(disposable)

    async(Unconfined) {
      queue.add("start")

      launch((executor as AppUIExecutorEx).createJobContext(coroutineContext)) {
        ApplicationManager.getApplication().assertIsDispatchThread()

        queue.add("coroutine start")
        Disposer.dispose(disposable)
        try {
          queue.add("coroutine before yield")
          yield()
          queue.add("coroutine after yield")
        }
        catch (e: Exception) {
          ApplicationManager.getApplication().assertIsDispatchThread()
          queue.add("coroutine yield caught ${e.javaClass.simpleName} because of ${e.cause?.javaClass?.simpleName}")
          throw e
        }
        queue.add("coroutine end")
      }.join()

      queue.add("end")

      assertOrderedEquals(queue,
                          "start",
                          "coroutine start",
                          "disposed",
                          "coroutine before yield",
                          "coroutine yield caught JobCancellationException because of DisposedException",
                          "end")
    }.joinNonBlocking()
  }

  private fun createDocument(): Document {
    val file = PsiFileFactory.getInstance(getProject()).createFileFromText("a.txt", PlainTextFileType.INSTANCE, "")
    return file.viewProvider.document!!
  }

  fun `test withDocumentsCommitted`() {
    val executor = AppUIExecutor.onUiThread(ModalityState.NON_MODAL)
      .inWriteAction()
      .withDocumentsCommitted(getProject())

    val transactionExecutor = AppUIExecutor.onUiThread(ModalityState.NON_MODAL)
      .inTransaction(getProject())
      .inWriteAction()

    async(Unconfined) {
      val pdm = PsiDocumentManager.getInstance(getProject())
      val commitChannel = Channel<Unit>()
      val job = launch((executor as AppUIExecutorEx).createJobContext(coroutineContext)) {
        commitChannel.receive()
        assertFalse(pdm.hasUncommitedDocuments())

        val document = createDocument()
        document.insertString(0, "a")
        assertTrue(pdm.hasUncommitedDocuments())

        commitChannel.receive()
        assertFalse(pdm.hasUncommitedDocuments())
        assertEquals("a", document.text)

        document.insertString(1, "b")
        assertTrue(pdm.hasUncommitedDocuments())

        commitChannel.receive()
        assertFalse(pdm.hasUncommitedDocuments())
        assertEquals("ab", document.text)

        commitChannel.close()
      }
      launch((transactionExecutor as AppUIExecutorEx).createJobContext(coroutineContext, job)) {
        while (true) {
          pdm.commitAllDocuments()
          commitChannel.send(Unit)
        }
      }.join()
      coroutineContext.cancelChildren()

    }.joinNonBlocking()
  }

  fun `test custom simple constraints`() {
    var scheduled = false

    val executor = (AppUIExecutor.onUiThread() as AppUIExecutorEx)
      .withConstraint(object : AsyncExecution.SimpleContextConstraint {

        override val isCorrectContext: Boolean
          get() = scheduled

        override fun schedule(runnable: Runnable) {
          scheduled = true
          runnable.run()
          scheduled = false
        }

        override fun toString() = "test"
      })

    async(executor.createJobContext()) {
      assertTrue(scheduled)
      yield()
      assertTrue(scheduled)
    }.joinNonBlocking()
  }

  fun `test custom expirable constraints disposed before dispatching`() {
    val queue = LinkedBlockingQueue<String>()

    var scheduled = false

    val disposable = Disposable {
      queue.add("disposable.dispose()")
    }.also { Disposer.register(testRootDisposable, it) }

    val executor = (AppUIExecutor.onUiThread() as AppUIExecutorEx)
      .withConstraint(object : AsyncExecution.ExpirableContextConstraint {
        override val expirable = disposable

        override val isCorrectContext: Boolean
          get() = scheduled

        override fun scheduleExpirable(runnable: Runnable) {
          if (Disposer.isDisposed(disposable)) {
            queue.add("refuse to run already disposed")
            return
          }
          scheduled = true
          runnable.run()
          scheduled = false
        }

        override fun toString() = "test"
      })

    queue.add("start")

    async(Unconfined) {
      launch(executor.createJobContext()) {
        queue.add("coroutine start")
        assertTrue(scheduled)
        yield()
        assertTrue(scheduled)
        queue.add("disposing")
        Disposer.dispose(disposable)
        try {
          queue.add("before yield disposed")
          yield()
          queue.add("after yield disposed")
        }
        catch (e: Throwable) {
          queue.add("coroutine yield caught ${e.javaClass.simpleName} because of ${e.cause?.javaClass?.simpleName}")
          throw e
        }
        queue.add("coroutine end")
      }.join()
      queue.add("end")

      assertOrderedEquals(queue,
                          "start",
                          "coroutine start",
                          "disposing",
                          "disposable.dispose()",
                          "before yield disposed",
                          "coroutine yield caught JobCancellationException because of DisposedException",
                          "end")
    }.joinNonBlocking()
  }

  fun `test custom expirable constraints disposed during dispatching`() {
    val queue = LinkedBlockingQueue<String>()

    var scheduled = false
    var shouldDisposeOnDispatch = false

    val disposable = Disposable {
      queue.add("disposable.dispose()")
    }.also { Disposer.register(testRootDisposable, it) }

    val uiExecutor = AppUIExecutor.onUiThread()
    val executor = (uiExecutor as AppUIExecutorEx)
      .withConstraint(object : AsyncExecution.ExpirableContextConstraint {
        override val expirable = disposable

        override val isCorrectContext: Boolean
          get() = scheduled

        override fun scheduleExpirable(runnable: Runnable) {
          if (shouldDisposeOnDispatch) {
            queue.add("disposing")
            Disposer.dispose(disposable)
          }
          if (Disposer.isDisposed(disposable)) {
            queue.add("refuse to run already disposed")
            return
          }
          scheduled = true
          runnable.run()
          scheduled = false
        }

        override fun toString() = "test"
      })

    async(Unconfined) {
      queue.add("start")

      val channel = Channel<Unit>()
      val job = launch(executor.createJobContext(coroutineContext)) {
        queue.add("coroutine start")
        assertTrue(scheduled)
        channel.receive()
        assertTrue(scheduled)
        try {
          shouldDisposeOnDispatch = true
          queue.add("before yield")
          channel.receive()
          queue.add("after yield disposed")
        }
        catch (e: Throwable) {
          queue.add("coroutine yield caught ${e.javaClass.simpleName} because of ${e.cause?.javaClass?.simpleName}")
          throw e
        }
        queue.add("coroutine end")

        channel.close()
      }
      launch(uiExecutor.createJobContext(coroutineContext, parent = job)) {
        try {
          while (true) {
            channel.send(Unit)
          }
        }
        catch (e: Throwable) {
          throw e
        }
      }
      job.join()
      queue.add("end")

      assertOrderedEquals(queue,
                          "start",
                          "coroutine start",
                          "before yield",
                          "disposing",
                          "disposable.dispose()",
                          "refuse to run already disposed",
                          "coroutine yield caught DisposedException because of null",
                          "end")
    }.joinNonBlocking()
  }
}