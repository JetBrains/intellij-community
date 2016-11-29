/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.impl.LaterInvocatorTest.flushSwingQueue
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities

/**
 * @author Denis Fokin
 *
 */

internal class Testable {

  private val isRun = AtomicBoolean(false)
  private var myExceptionConsumer: (Exception) -> Unit = {}
  private val tasksToExecute = Collections.synchronizedList(ArrayList<() -> Unit>())
  private val completenessLock = ReentrantLock()
  private val completenessCondition = completenessLock.newCondition()

  private val edtLock = ReentrantLock()
  private val edtCondition = edtLock.newCondition()
  private val suspisiousWakeUpGuard = AtomicBoolean(false)

  private val blockEDT = {
    edtLock.lock()
    try {
      while (suspisiousWakeUpGuard.get()) {
        edtCondition.await()
      }
      edtCondition.signalAll()
    } finally {
      edtLock.unlock()
    }
  }

  private var waitSuspendedEDT = false

  fun suspendEDT(): Testable {
    waitSuspendedEDT = true
    return this
  }

  fun execute(lambda: () -> Unit): Testable {
    if (!waitSuspendedEDT) {
      SwingUtilities.invokeLater(lambda)
    } else {
      tasksToExecute.add(lambda)
    }
    return this
  }

  fun flushEDT(): Testable {
    return execute{
                     flushSwingQueue()
                     flushSwingQueue()
                   }
  }

  fun continueEDT(): Testable {
    if (!waitSuspendedEDT) throw RuntimeException("EDT has not been suspended.")
    edtLock.lock()
    try {
      SwingUtilities.invokeLater(blockEDT)
      SwingUtilities.invokeLater {
        try {
          tasksToExecute.forEach({ it.invoke() })
        }
        catch (e: Exception) {
          myExceptionConsumer.invoke(e)
        }
      }
    } finally {
     edtLock.unlock()
    }
    return this
  }

  internal fun ifExceptions(exceptionConsumer:(Exception) -> Unit): Testable {
    myExceptionConsumer = exceptionConsumer
    return this
  }

  private fun ifPass(completionAction: Runnable): Testable {
    if (!isRun.get()) throw RuntimeException("Testable has not been run yet")
    completionAction.run()
    return this
  }

  fun completed (): Testable {
    SwingUtilities.invokeLater {
      completenessLock.lock()
      try {
        completenessCondition.signalAll()
      }  finally {
        completenessLock.unlock()
      }
    }
    return this;
  }

  fun waitCompletion(condition: Condition, lock: ReentrantLock, timout: Long = 0) {
    lock.lock()
    try {
      condition.await(timout, TimeUnit.MILLISECONDS)
    } finally {
      lock.unlock()
    }
  }


  fun waitCompletion(millis: Long) {
    completenessLock.lock()
    try {
      completenessCondition.await(millis, TimeUnit.MILLISECONDS)
    } finally {
      completenessLock.unlock()
    }
  }
}
