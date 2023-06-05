// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadContext
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.SystemProperties
import com.intellij.util.timeoutRunBlocking
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class DumbServicePropagationTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    val key = "idea.force.dumb.queue.tasks"
    val prev = System.setProperty(key, "true")
    disposeOnTearDown(Disposable {
      SystemProperties.setProperty(key, prev)
    })
  }

  fun testDumbQueue() = runWithContextPropagationEnabled {
    val service = DumbService.getInstance(project)
    val element = TestElement("element")
    val callTracker = AtomicBoolean(false)

    timeoutRunBlocking {
      withContext(element) {
        blockingContext {
          service.queueTask(object : DumbModeTask() {
            override fun performInDumbMode(indicator: ProgressIndicator) {
              callTracker.set(true)
              assertSame(element, currentThreadContext()[TestElementKey])
            }
          })
        }
      }

      blockingContext {
        assertFalse("dumb task should not be completed", callTracker.get())
        assertNull(currentThreadContext()[TestElementKey])
        service.completeJustSubmittedTasks()
        IdeEventQueue.getInstance().flushQueue() // forcing events processing
        assertTrue("dumb task should be completed", callTracker.get())
      }
    }
  }

  fun testRunWhenSmart() = runWithContextPropagationEnabled {
    val service = DumbService.getInstance(project)
    val element = TestElement("element")
    val callTracker = AtomicBoolean(false)
    runBlocking {

      // spawn dumb mode
      blockingContext {
        service.queueTask(object : DumbModeTask() {
          override fun performInDumbMode(indicator: ProgressIndicator) {
            // immediately finishes on execution
          }
        })
      }
      assertTrue(service.isDumb)

      // set up context
      withContext(element) {
        blockingContext {
          service.runWhenSmart {
            callTracker.set(true)
            assertSame(element, currentThreadContext()[TestElementKey])
          }
        }
      }

      // discharge dumb mode
      blockingContext {
        assertFalse("runWhenSmart should not be completed", callTracker.get())
        assertNull(currentThreadContext()[TestElementKey])
        service.completeJustSubmittedTasks()
        IdeEventQueue.getInstance().flushQueue() // forcing events processing
        assertTrue("runWhenSmart should be completed", callTracker.get())
      }
    }
  }
}