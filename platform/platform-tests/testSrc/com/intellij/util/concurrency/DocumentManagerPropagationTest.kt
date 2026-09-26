// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadContext
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class DocumentManagerPropagationTest : HeavyPlatformTestCase() {

  fun testCallbacks() = runWithContextPropagationEnabled {
    val service = PsiDocumentManager.getInstance(project)
    val element = TestElement("element")
    val performWhenAllCommittedTracker = AtomicBoolean(false)
    val performForCommittedDocumentTracker = AtomicBoolean(false)
    val performLaterWhenAllCommittedTracker = AtomicBoolean(false)
    val vFile = createTempVirtualFile("x.txt", null, "abc", StandardCharsets.UTF_8)
    val psiFile = psiManager.findFile(vFile)!!

    val document = service.getDocument(psiFile)!!

    timeoutRunBlocking {
      runWriteAction {
        document.setText("42")
      }
      withContext(element) {
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