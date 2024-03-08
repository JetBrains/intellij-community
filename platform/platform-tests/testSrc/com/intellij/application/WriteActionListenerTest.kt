// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application

import com.intellij.openapi.application.ApplicationListener
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.application
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class WriteActionListenerTest {
  @JvmField
  @Rule
  val appRule = ApplicationRule()

  @JvmField
  @Rule
  val testRootDisposableRule = DisposableRule()

  private class CountingWriteActionListener : ApplicationListener {
    val beforeWriteActionStartCount: AtomicInteger = AtomicInteger()
    var afterWriteActionFinishedCount: AtomicInteger = AtomicInteger()
    val writeActionStartCount: AtomicInteger = AtomicInteger()
    var writeActionFinishedCount: AtomicInteger = AtomicInteger()

    override fun beforeWriteActionStart(action: Any) {
      beforeWriteActionStartCount.incrementAndGet()
    }

    override fun afterWriteActionFinished(action: Any) {
      afterWriteActionFinishedCount.incrementAndGet()
    }

    override fun writeActionStarted(action: Any) {
      writeActionStartCount.incrementAndGet()
    }

    override fun writeActionFinished(action: Any) {
      writeActionFinishedCount.incrementAndGet()
    }

    fun assertState(beforeWriteActionStartCount: Int, afterWriteActionFinishedCount: Int,
                    writeActionStartCount: Int, writeActionFinishedCount: Int) {
      assertEquals(beforeWriteActionStartCount, this.beforeWriteActionStartCount.get())
      assertEquals(afterWriteActionFinishedCount, this.afterWriteActionFinishedCount.get())
      assertEquals(writeActionStartCount, this.writeActionStartCount.get())
      assertEquals(writeActionFinishedCount, this.writeActionFinishedCount.get())
    }
  }

  @Test
  fun testNestedWriteActionsBlocking() {
    val listener = CountingWriteActionListener()
    application.addApplicationListener(listener, testRootDisposableRule.disposable)

    runInEdtAndWait {
      application.runWriteAction {
        listener.assertState(1, 0, 1, 0)
        application.runWriteAction {
          listener.assertState(1, 0, 2, 0)
        }
        listener.assertState(1, 0, 2, 1)
      }
      listener.assertState(1, 1, 2, 2)
    }
  }
}