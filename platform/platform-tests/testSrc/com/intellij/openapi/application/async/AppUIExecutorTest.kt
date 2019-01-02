// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async

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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.LinkedBlockingQueue
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

/**
 * @author eldar
 */
class AppUIExecutorTest : LightPlatformTestCase() {
  override fun runInDispatchThread() = false

  override fun invokeTestRunnable(runnable: Runnable) {
    SwingUtilities.invokeLater(runnable)
    UIUtil.dispatchAllInvocationEvents()
  }

  private object SwingDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = SwingUtilities.invokeLater(block)
  }

  private fun Deferred<Unit>.joinNonBlocking() {
    var countDown = 5
    object : Runnable {
      override fun run() {
        if (isCompleted) {
          try {
            @Suppress("EXPERIMENTAL_API_USAGE")
            getCompleted()
          }
          catch (ignored: CancellationException) {
          }
        }
        else {
          if (countDown-- <= 0) fail("Too many EDT reschedules")
          SwingUtilities.invokeLater(this)
        }
      }
    }.run()
  }

  fun `test coroutine onUiThread`() {
    val executor = AppUIExecutor.onUiThread(ModalityState.any())
    GlobalScope.async(executor.coroutineDispatchingContext()) {
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

    GlobalScope.async(SwingDispatcher) {
      queue.add("start")

      launch(executor.coroutineDispatchingContext()) {
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
                          "coroutine yield caught JobCancellationException because of null",
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

    GlobalScope.async(SwingDispatcher) {
      val pdm = PsiDocumentManager.getInstance(getProject())
      val commitChannel = Channel<Unit>()
      val job = launch(executor.coroutineDispatchingContext()) {
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
      launch(transactionExecutor.coroutineDispatchingContext() + job) {
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

    val executor = AppUIExecutor.onUiThread()
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

    GlobalScope.async(executor.coroutineDispatchingContext()) {
      assertTrue(scheduled)
      yield()
      assertTrue(scheduled)
    }.joinNonBlocking()
  }

  fun `test custom expirable constraints disposed before suspending`() {
    val queue = LinkedBlockingQueue<String>()

    var scheduled = false

    val disposable = Disposable {
      queue.add("disposable.dispose()")
    }.also { Disposer.register(testRootDisposable, it) }

    val executor = AppUIExecutor.onUiThread()
      .withConstraint(object : AsyncExecution.ExpirableContextConstraint {
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
      }, disposable)

    queue.add("start")

    GlobalScope.async(SwingDispatcher) {
      launch(executor.coroutineDispatchingContext()) {
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
                          "coroutine yield caught JobCancellationException because of null",
                          "end")
    }.joinNonBlocking()
  }

  fun `test custom expirable constraints disposed before resuming`() {
    val queue = LinkedBlockingQueue<String>()

    var scheduled = false
    var shouldDisposeBeforeSend = false

    val disposable = object : Disposable.Parent {
      override fun beforeTreeDispose() {
        queue.add("disposable.beforeTreeDispose()")
      }

      override fun dispose() {
        queue.add("disposable.dispose()")
      }
    }.also { Disposer.register(testRootDisposable, it) }

    val uiExecutor = AppUIExecutor.onUiThread()
    val executor = uiExecutor
      .withConstraint(object : AsyncExecution.ExpirableContextConstraint {
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
      }, disposable)

    GlobalScope.async(SwingDispatcher) {
      queue.add("start")

      val channel = Channel<Unit>()
      val job = launch(executor.coroutineDispatchingContext()) {
        queue.add("coroutine start")
        assertTrue(scheduled)
        channel.receive()
        assertTrue(scheduled)
        try {
          shouldDisposeBeforeSend = true
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
      launch(uiExecutor.coroutineDispatchingContext() + job) {
        try {
          while (true) {
            if (shouldDisposeBeforeSend) {
              queue.add("disposing")
              Disposer.dispose(disposable)
            }
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
                          "disposable.beforeTreeDispose()",
                          "disposable.dispose()",
                          "coroutine yield caught JobCancellationException because of null",
                          "end")
    }.joinNonBlocking()
  }

  fun `test custom expirable constraints disposed during dispatching`() =
    doTestCustomExpirableConstraintsDisposedDuringDispatching(expectedLog = arrayOf(
      "[context: !outer + !inner] start",
      "[context: outer + inner] coroutine start",
      "[context: outer + inner] before receive",
      "disposing constraint",
      "constraintDisposable.beforeTreeDispose()",
      "constraintDisposable.dispose()",
      "refuse to run already disposed",
      "[context: !outer + inner] after receive disposed",  // channel.receive() is atomic
      "[context: !outer + inner] coroutine yield caught JobCancellationException because of null",
      "[context: !outer + !inner] end")
    ) { queue, constraintDisposable, _ ->
      queue.add("disposing constraint")
      Disposer.dispose(constraintDisposable)
    }


  fun `test custom expirable constraints disposed during dispatching through both disposables`() =
    doTestCustomExpirableConstraintsDisposedDuringDispatching(expectedLog = arrayOf(
      "[context: !outer + !inner] start",
      "[context: outer + inner] coroutine start",
      "[context: outer + inner] before receive",
      "disposing anotherDisposable",
      "anotherDisposable.beforeTreeDispose()",
      "anotherDisposable.dispose()",
      "disposing constraint",
      "constraintDisposable.beforeTreeDispose()",
      "constraintDisposable.dispose()",
      "refuse to run already disposed",
      "[context: !outer + inner] after receive disposed",  // channel.receive() is atomic
      "[context: !outer + inner] coroutine yield caught JobCancellationException because of null",
      "[context: !outer + !inner] end")
    ) { queue, constraintDisposable, anotherDisposable ->
      queue.add("disposing anotherDisposable")
      Disposer.dispose(anotherDisposable)
      queue.add("disposing constraint")
      Disposer.dispose(constraintDisposable)
    }

  fun `test custom expirable constraints disposed another disposable`() =
    doTestCustomExpirableConstraintsDisposedDuringDispatching(expectedLog = arrayOf(
      "[context: !outer + !inner] start",
      "[context: outer + inner] coroutine start",
      "[context: outer + inner] before receive",
      "disposing anotherDisposable",
      "anotherDisposable.beforeTreeDispose()",
      "anotherDisposable.dispose()",
      "[context: outer + inner] after receive disposed",
      "[context: outer + inner] coroutine yield caught JobCancellationException because of null",
      "[context: !outer + !inner] end")
    ) { queue, _, anotherDisposable ->
      queue.add("disposing anotherDisposable")
      Disposer.dispose(anotherDisposable)
    }

  private fun doTestCustomExpirableConstraintsDisposedDuringDispatching(expectedLog: Array<String>,
                                                                        doDispose: (queue: LinkedBlockingQueue<String>,
                                                                                    constraintDisposable: Disposable.Parent,
                                                                                    anotherDisposable: Disposable.Parent) -> Unit) {
    val queue = LinkedBlockingQueue<String>()

    var outerScheduled = false
    var innerScheduled = false
    var shouldDisposeOnDispatch = false

    fun emit(s: String) {
      fun Boolean.toFlagString(name: String) = if (this) name else "!$name"
      val contextState = "context: ${outerScheduled.toFlagString("outer")} + ${innerScheduled.toFlagString("inner")}"
      queue += "[$contextState] $s"
    }

    val constraintDisposable = object : Disposable.Parent {
      override fun beforeTreeDispose() {
        queue.add("constraintDisposable.beforeTreeDispose()")
      }

      override fun dispose() {
        queue.add("constraintDisposable.dispose()")
      }
    }.also { Disposer.register(testRootDisposable, it) }

    val anotherDisposable = object : Disposable.Parent {
      override fun beforeTreeDispose() {
        queue.add("anotherDisposable.beforeTreeDispose()")
      }

      override fun dispose() {
        queue.add("anotherDisposable.dispose()")
      }
    }.also { Disposer.register(testRootDisposable, it) }

    val uiExecutor = AppUIExecutor.onUiThread()
    val executor = uiExecutor
      .withConstraint(object : AsyncExecution.ExpirableContextConstraint {
        override val isCorrectContext: Boolean
          get() = outerScheduled

        override fun scheduleExpirable(runnable: Runnable) {
          if (shouldDisposeOnDispatch) {
            doDispose(queue, constraintDisposable, anotherDisposable)
          }
          if (Disposer.isDisposed(constraintDisposable)) {
            queue.add("refuse to run already disposed")
            return
          }
          outerScheduled = true
          runnable.run()
          outerScheduled = false
        }

        override fun toString() = "test outer"
      }, constraintDisposable)
      .withConstraint(object : AsyncExecution.SimpleContextConstraint {
        override val isCorrectContext: Boolean
          get() = innerScheduled

        override fun schedule(runnable: Runnable) {
          innerScheduled = true
          runnable.run()
          innerScheduled = false
        }

        override fun toString() = "test inner"
      })
      .expireWith(anotherDisposable)

    GlobalScope.async(SwingDispatcher) {
      emit("start")

      val channel = Channel<Unit>()
      val job = launch(executor.coroutineDispatchingContext()) {
        emit("coroutine start")
        assertTrue(outerScheduled)
        channel.receive()
        assertTrue(outerScheduled)
        try {
          shouldDisposeOnDispatch = true
          emit("before receive")
          channel.receive()
          emit("after receive disposed")
          yield()
          emit("after yield disposed")
        }
        catch (e: Throwable) {
          emit("coroutine yield caught ${e.javaClass.simpleName} because of ${e.cause?.javaClass?.simpleName}")
          throw e
        }
        emit("coroutine end")

        channel.close()
      }
      launch(uiExecutor.coroutineDispatchingContext() + job) {
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
      emit("end")
      assertOrderedEquals(queue, *expectedLog)

    }.joinNonBlocking()
  }
}