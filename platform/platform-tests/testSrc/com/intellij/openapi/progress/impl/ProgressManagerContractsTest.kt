// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.CoreProgressManager.shouldEnterModalityState
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rethrowLoggedErrorsIn
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import java.util.concurrent.atomic.AtomicInteger

class ProgressManagerContractsTest : LightPlatformTestCase() {
  @TestFor(issues = ["IDEA-241785"])
  fun `test leaked exception from backgroundable task`(): Unit = rethrowLoggedErrorsIn {
    DefaultLogger.disableStderrDumping(testRootDisposable)
    val message = "this is test exception message to ignore"
    try {
      object : Task.Backgroundable(project, "foo", true) {
        override fun run(indicator: ProgressIndicator) {
          throw RuntimeException(message)
        }
      }.queue()
      error("exception was expected")
    }
    catch (e: Throwable) {
      assertThat(e.message).contains(message)
    }
  }

  @TestFor(issues = ["IDEA-241785"])
  fun `test leaked invokeLater exception from backgroundable task`(): Unit = rethrowLoggedErrorsIn {
    DefaultLogger.disableStderrDumping(testRootDisposable)
    val message = "this is test exception message to ignore"
    try {
      object : Task.Backgroundable(project, "foo", true) {
        override fun run(indicator: ProgressIndicator) {
          ApplicationManager.getApplication().invokeAndWait {
            throw RuntimeException(message)
          }
        }
      }.queue()
      error("exception was expected")
    }
    catch (e: Throwable) {
      assertThat(e.message).contains(message)
    }
  }

  @TestFor(issues = ["IDEA-241785"])
  fun `test backgroundable modality in GUI`() = runWithGuiTasksMode { `test backgroundable modality in tests`() }

  @TestFor(issues = ["IDEA-241785"])
  fun `test backgroundable modality in tests`() = repeat(10) {
    require(ApplicationManager.getApplication().isDispatchThread)
    var callbackResult = ""

    val taskCompleted = AtomicInteger(0)
    ApplicationManager.getApplication().invokeLater {
      callbackResult += "invokeLater."
      taskCompleted.incrementAndGet()
    }

    object : Task.Backgroundable(project, "mock", true) {
      override fun run(indicator: ProgressIndicator) {
        // we add the next callbackResult update operation.
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

    assertThat(callbackResult).isEqualTo("invokeLater.fromProgress.")
  }

  @TestFor(issues = ["IDEA-241785"])
  fun `test invokeLater from pooled thread & modal in GUI`() = runWithGuiTasksMode {
    `test invokeLater from pooled thread & modal in tests`()
  }

  @TestFor(issues = ["IDEA-241785"])
  fun `test invokeLater from pooled thread & modal in tests`() = repeat(10) {
    require(ApplicationManager.getApplication().isDispatchThread)

    var callbackResult = ""
    val taskCompleted = AtomicInteger(0)

    object : Task.Modal(project, "mock", true) {
      override fun run(indicator: ProgressIndicator) {
        // ensure the messages queue is flushed
        ApplicationManager.getApplication().invokeAndWait {
          callbackResult += "fromProgress.1."
          taskCompleted.incrementAndGet()
        }

        //this will post a task from a pooled thread, which may not inherit the ModalityState to the EDT
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
    assertThat(callbackResult).isEqualTo("fromProgress.1.fromPool.fromProgress.2.")
  }

  @TestFor(issues = ["IDEA-241785"])
  fun `test invokeLater from pooled thread & background in GUI`() = runWithGuiTasksMode {
    `test invokeLater from pooled thread & background in tests`()
  }

  @TestFor(issues = ["IDEA-241785"])
  fun `test invokeLater from pooled thread & background in tests`() = repeat(10) {
    require(ApplicationManager.getApplication().isDispatchThread)

    var callbackResult = ""
    val taskCompleted = AtomicInteger(0)

    //we start a BACKGROUNDABLE task
    object : Task.Backgroundable(project, "Mock", true) {
      override fun run(indicator: ProgressIndicator) {
        // ensure the messages queue is flushed
        ApplicationManager.getApplication().invokeAndWait {
          callbackResult += "fromProgress.1."
          taskCompleted.incrementAndGet()
        }

        //this will post a task from a pooled thread, which may not inherit the ModalityState to the EDT
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
    assertThat(callbackResult).isEqualTo("fromProgress.1.fromPool.fromProgress.2.")
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

  @TestFor(issues = ["IDEA-307428"])
  fun `test should enter modality state`() {
    fun modalTask(): Task = object : Task.Modal(null, "", true) {
      override fun run(indicator: ProgressIndicator) = fail()
    }

    fun bgTask(startInBackground: Boolean): Task = object : Task.Backgroundable(null, "", true, { startInBackground }) {
      override fun run(indicator: ProgressIndicator) = fail()
    }

    fun cmTask(startInBackground: Boolean): Task = object : Task.ConditionalModal(null, "", true, { startInBackground }) {
      override fun run(indicator: ProgressIndicator) = fail()
    }

    fun doTest(vararg data: Pair<Boolean, Task>) {
      for ((expected, task) in data) {
        assertThat(shouldEnterModalityState(task)).isEqualTo(expected)
      }
    }

    val baselineData = arrayOf(
      true to modalTask(),
      false to bgTask(false),
      false to bgTask(true),
      false to cmTask(false),
      false to cmTask(true),
    )

    ApplicationManager.getApplication().executeOnPooledThread {
      doTest(*baselineData)
      runWithGuiTasksMode {
        doTest(*baselineData)
      }
    }.get()

    doTest(*baselineData)
    runWithGuiTasksMode {
      doTest(
        true to modalTask(),
        false to bgTask(false),
        false to bgTask(true),
        true to cmTask(false), // this is the only difference with baseline
        false to cmTask(true),
      )
    }
  }

  private fun runWithGuiTasksMode(action: () -> Unit) {
    PlatformTestUtil.withSystemProperty<Nothing>("intellij.progress.task.ignoreHeadless", "true", action)
  }
}
