// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.*
import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.progress.blockingContext
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext

class DocumentManagerPropagationTest : HeavyPlatformTestCase() {

  fun testCallbacks() = runWithContextPropagationEnabled {
    val service = PsiDocumentManager.getInstance(project)
    val element = TestElement("element")
    val performWhenAllCommittedTracker = AtomicBoolean(false)
    val performForCommittedDocumentTracker = AtomicBoolean(false)
    val performLaterWhenAllCommittedTracker = AtomicBoolean(false)
    val psiFile = installThreadContext(EmptyCoroutineContext).use {
      val vFile = createTempVirtualFile("x.txt", null, "abc", StandardCharsets.UTF_8)
      val psiFile = psiManager.findFile(vFile)!!
      psiFile
    }

    val document = service.getDocument(psiFile)!!

    timeoutRunBlocking {
      blockingContext {
        runWriteAction {
          document.setText("42")
        }
      }
      withContext(element) {
        blockingContext {

          service.performForCommittedDocument(document) {
            performForCommittedDocumentTracker.set(true)
            assertSame(element, currentThreadContext()[TestElementKey])
          }

          service.performWhenAllCommitted {
            performWhenAllCommittedTracker.set(true)
            assertSame(element, currentThreadContext()[TestElementKey])
          }

          service.performLaterWhenAllCommitted {
            performLaterWhenAllCommittedTracker.set(true)
            assertSame(element, currentThreadContext()[TestElementKey])
          }

        }
      }

      blockingContext {
        assertFalse(performForCommittedDocumentTracker.get())
        assertFalse(performWhenAllCommittedTracker.get())
        assertFalse(performLaterWhenAllCommittedTracker.get())

        assertNull(currentThreadContext()[TestElementKey])
        service.commitDocument(document)

        assertTrue(performWhenAllCommittedTracker.get())
        assertTrue(performForCommittedDocumentTracker.get())
        IdeEventQueue.getInstance().flushQueue() // forcing events processing
        assertTrue(performLaterWhenAllCommittedTracker.get())
      }
    }
  }

}