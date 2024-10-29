// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Ignore("Manually run only")
@RunWith(JUnit4::class)
class SteppingUnderReadActionManualTest : HeavyPlatformTestCase() {
  @Test
  fun testCoroutineReadActionStepping() {
    doTest {
      readAction {
        cancellingCode()
      }
    }
  }

  @Test
  fun testReadActionStepping() {
    doTest {
      ReadAction.nonBlocking<Unit> { cancellingCode() }.executeSynchronously()
    }
  }

  private fun doTest(action: suspend () -> Unit) {
    AppExecutorUtil.getAppExecutorService().submit {
      runBlocking {
        val channel = Channel<Unit>(Channel.UNLIMITED)
        val writeJob = launch(Dispatchers.Default) {
          var counter = 0
          while (true) {
            writeAction {
              println("Write action ${counter++}")
            }
            channel.send(Unit)
          }
        }

        repeat(10) {
          channel.receive()
          while (channel.tryReceive().isSuccess) {
          }
          action()
        }
        writeJob.cancel()
      }
    }
  }

  private fun cancellingCode() {
    println("ReadAction")
    ProgressManager.checkCanceled()
    try {
      ProgressManager.checkCanceled()
      Thread.sleep(1)
      ProgressManager.checkCanceled()
      ProgressManager.checkCanceled()
      ProgressManager.checkCanceled()
    }
    catch (e: ProcessCanceledException) {
      println("Read action cancelled")
      throw e
    }
  }
}
