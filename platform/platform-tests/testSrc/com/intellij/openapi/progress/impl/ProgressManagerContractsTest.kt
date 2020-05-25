// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import java.util.concurrent.atomic.AtomicInteger

class ProgressManagerContractsTest : LightPlatformTestCase() {

  fun `test backgroundable modality in tests`() = repeat(10) {
    require(ApplicationManager.getApplication().isDispatchThread)
    val callbackResult = `task_backgroundable vs invokeLater`()
    assertThat(callbackResult).isEqualTo("fromProgress.invokeLater.")
  }

  fun `test backgroundable modality in GUI`() = repeat(10) {
    require(ApplicationManager.getApplication().isDispatchThread)
    runWithGuiTasksMode {
      val callbackResult = `task_backgroundable vs invokeLater`()
      assertThat(callbackResult).isEqualTo("invokeLater.fromProgress.")
    }
  }

  fun `test invoke later from pooled thread in tests`() = repeat(10) {
    require(ApplicationManager.getApplication().isDispatchThread)

    val callbackResult = `task_modal vs pooled thread`()
    assertThat(callbackResult).isEqualTo("fromProgress.1.fromProgress.2.fromPool.")
  }

  fun `test invoke later from pooled thread in GUI`() = repeat(10) {
    require(ApplicationManager.getApplication().isDispatchThread)
    runWithGuiTasksMode {
      val callbackResult = `task_modal vs pooled thread`()
      assertThat(callbackResult).isEqualTo("fromProgress.1.fromPool.fromProgress.2.")
    }
  }

  private fun `task_modal vs pooled thread`() : String {
    var callbackResult = ""
    val taskCompleted = AtomicInteger(0)

    object : Task.Backgroundable(project, "mock", true) {
      override fun run(indicator: ProgressIndicator) {
        // ensure the messages queue is flushed
        ApplicationManager.getApplication().invokeAndWait {
          callbackResult += "fromProgress.1."
          taskCompleted.incrementAndGet()
        }

        //this will post a task from a pooled thread to the EDT
        AppExecutorUtil.getAppExecutorService().submit {
          ApplicationManager.getApplication().invokeLater {
            callbackResult += "fromPool."
          }
        }.get()

        // flush messages to see if we have it here or not
        ApplicationManager.getApplication().invokeAndWait {
          callbackResult += "fromProgress.2."
          taskCompleted.incrementAndGet()
        }
      }

      override fun onFinished() {
        super.onFinished()
        taskCompleted.incrementAndGet()
      }
    }.queue()

    flushEdtEvents { taskCompleted.get() < 3 }

    return callbackResult
  }

  private fun `task_backgroundable vs invokeLater`(): String {
    var callbackResult = ""
    val taskCompleted = AtomicInteger(0)

    // 1. queue a default command via invokeLater
    ApplicationManager.getApplication().invokeLater {
      callbackResult += "invokeLater."
      taskCompleted.incrementAndGet()
    }

    // 2. submit a backgroundable task (that may want to access the invokeLater side-effects
    object : Task.Backgroundable(project, "mock", true) {
      override fun run(indicator: ProgressIndicator) {
        // we add the next callbackResult update operation.
        // now it depends from the context:
        // - [for tests]. this will run first
        // - [for gui]. this will run the second!
        ApplicationManager.getApplication().invokeAndWait {
          callbackResult += "fromProgress."
          taskCompleted.incrementAndGet()
        }
      }

      override fun onFinished() {
        super.onFinished()
        taskCompleted.incrementAndGet()
      }
    }.queue()

    flushEdtEvents { taskCompleted.get() < 3 }

    return callbackResult
  }

  private fun flushEdtEvents(predicate: () -> Boolean) {
    UIUtil.dispatchAllInvocationEvents()

    //just in case of GUI mode, we'd need to wait for task to complete
    while (predicate()) {
      UIUtil.dispatchAllInvocationEvents()
      Thread.sleep(20)
    }

    UIUtil.dispatchAllInvocationEvents()
  }


  private inline fun <Y> runWithGuiTasksMode(action: () -> Y): Y {
    val key = "intellij.progress.task.ignoreHeadless"
    val prev = System.setProperty(key, "true")
    try {
      return action()
    } finally {
      if (prev != null) {
        System.setProperty(key, prev)
      } else {
        System.clearProperty(key)
      }
    }
  }
}
