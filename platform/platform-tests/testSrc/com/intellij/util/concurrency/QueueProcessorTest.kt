/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.concurrency

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestCase
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private const val TIMEOUT_MS = 1000L

class QueueProcessorTest : PlatformTestCase() {

  fun `test waiting for returns on finish condition`() {
    var stop = false;
    val semaphore = Semaphore(0)
    val processor = QueueProcessor<Any>({ semaphore.down() }, { stop })
    
    processor.add(1)
    stop = true;
    semaphore.up()

    assertTrue(processor.waitFor(TIMEOUT_MS));
    processor.waitFor(); // just in case let's check this method as well - hopefully, it won't hang since waitFor(timeout) works
  }

  fun `test works fine after thrown exception`() {
    LoggedErrorProcessor.getInstance().disableStderrDumping(testRootDisposable)

    val resultQueue = LinkedBlockingQueue<Any>()
    val queueProcessor = QueueProcessor<() -> Any> {
      try {
        resultQueue.add(it())
      }
      catch (e: Throwable) {
        resultQueue.add(e)
        throw e
      }
    }

    fun check(expectedResult: Any, item: () -> Any) {
      queueProcessor.add(item)
      assertEquals(expectedResult, resultQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
      assertEmpty(resultQueue)
    }
    fun check(expectedResult: Number) = check(expectedResult) { expectedResult }
    fun check(expectedException: Throwable) = check(expectedException) {
      throw expectedException.also {
        it.addSuppressed(Throwable())
      }
    }

    check(1)
    check(Throwable())
    check(2)
    check(Error())
    check(3)
    check(RuntimeException())
    check(4)
    check(ProcessCanceledException())  // this used to make the remaining queue elements stuck
    check(5)
    check(Exception())
    check(6)
    check(ExecutionException("EE"))
    check(7)
  }
}