// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Suppress("UsagesOfObsoleteApi")
class MergingUpdateQueueDeadlockTest {

  @Test
  fun `test no deadlock with write lock and put rejected`() = timeoutRunBlocking(5.seconds) {
    val disposable = Disposer.newDisposable()
    val queue = MergingUpdateQueue(
      name = "DeadlockTestQueue",
      mergingTimeSpan = 1000,
      isActive = true,
      modalityStateComponent = null,
      parent = disposable,
      activationComponent = null,
      thread = Alarm.ThreadToUse.POOLED_THREAD
    )

    assertFalse(queue.isPassThrough, "Test must not use pass-through queue")

    val t2HoldsQueueLock = CountDownLatch(1)
    val t1InsideWriteAction = CountDownLatch(1)

    val victim = object : Update("victim_identity") {
      override fun setRejected() {
        super.setRejected()
        runReadAction {}
      }

      override fun run() {}
    }

    queue.queue(victim)

    val t2 = AppExecutorUtil.getAppExecutorService().submit {
      // We use a DIFFERENT identity to bypass the 'containsKey' optimization
      // and force the queue to call canEat() inside the lock.
      queue.queue(object : Update("aggressor_identity") {
        override fun run() {}

        override fun canEat(update: Update): Boolean {
          if (update === victim) {
            t2HoldsQueueLock.countDown()
            try {
              if (!t1InsideWriteAction.await(2, TimeUnit.SECONDS)) {
                return false
              }
              // Small sleep to ensure T1 proceeds to try acquiring Queue Lock
              Thread.sleep(50)
            }
            catch (e: InterruptedException) {
              throw RuntimeException(e)
            }
            return true // Eat the victim, triggering setRejected()
          }
          return false
        }
      })
    }

    edtWriteAction {
      try {
        if (t2HoldsQueueLock.await(2, TimeUnit.SECONDS)) {
          t1InsideWriteAction.countDown()

          // Before fix: blocks here forever (T2 holds lock while blocked on our Write Lock).
          queue.queue(object : Update("other_identity") {
            override fun run() {}
          })
        }
      }
      catch (e: InterruptedException) {
        throw RuntimeException(e)
      }
    }

   t2.get(2, TimeUnit.SECONDS)
    Disposer.dispose(disposable)
    }
}
