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
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author eldar
 */
class AppUIExecutorTest : LightPlatformTestCase() {
  override fun runInDispatchThread() = false
  override fun invokeTestRunnable(runnable: Runnable) = runnable.run()

  fun `test coroutine onUiThread`() {
    val executor = AppUIExecutor.onUiThread(ModalityState.any())
    runBlocking((executor as AsyncExecution).createJobContext()) {
      ApplicationManager.getApplication().assertIsDispatchThread()
    }
  }

  fun `test coroutine withExpirable`() {
    val queue = LinkedBlockingQueue<String>()
    val disposable = Disposable {
      queue.add("disposed")
    }.also { Disposer.register(testRootDisposable, it) }

    val executor = AppUIExecutor.onUiThread(ModalityState.any())
      .expireWith(disposable)

    queue.add("start")
    runBlocking(Unconfined) {
      launch((executor as AsyncExecution).createJobContext(coroutineContext)) {
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
    }
    queue.add("end")

    assertOrderedEquals(queue,
                        "start",
                        "coroutine start",
                        "disposed",
                        "coroutine before yield",
                        "coroutine yield caught JobCancellationException because of DisposedException",
                        "end")
  }

  private fun createDocument(): Document {
    val file = PsiFileFactory.getInstance(getProject()).createFileFromText("a.txt", PlainTextFileType.INSTANCE, "")
    return file.viewProvider.document!!
  }

  fun `test withDocumentsCommitted`() {
    val executor = AppUIExecutor.onUiThread(ModalityState.any())
      .withDocumentsCommitted(getProject())
      .inWriteAction()

    val writeActionExecutor = AppUIExecutor.onUiThread(ModalityState.any())
      .inWriteAction()

    runBlocking(Unconfined) {
      val pdm = PsiDocumentManager.getInstance(getProject())
      val commitChannel = Channel<Unit>()
      val job = launch((executor as AsyncExecution).createJobContext(coroutineContext)) {
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
      launch((writeActionExecutor as AsyncExecution).createJobContext(coroutineContext, job)) {
        while (true) {
          pdm.commitAllDocuments()
          commitChannel.send(Unit)
        }
      }.join()
      coroutineContext.cancelChildren()
    }
  }
}