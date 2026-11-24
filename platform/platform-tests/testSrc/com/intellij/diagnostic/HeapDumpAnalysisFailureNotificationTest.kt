// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.report.MemoryReportReason
import com.intellij.ide.plugins.runAndReturnWithLoggedWarnings
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.Task
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Path

@TestApplication
class HeapDumpAnalysisFailureNotificationTest {
  @Test
  fun `heap OOME shows the memory analysis failed notification`(@TestDisposable disposable: Disposable) {
    val notifications = collectHeapDumpAnalysisNotifications(disposable)

    // The test logger fails the test on an error, so this call also checks that the OOME is only a warning.
    val (_, warnings) = runAndReturnWithLoggedWarnings {
      createAnalysisTask().onThrowable(OutOfMemoryError("Java heap space"))
    }

    assertThat(warnings).containsExactly("Not enough heap space for heap dump analysis")
    assertThat(notifications.map { it.displayId to it.content })
      .containsExactly("memory.analysis.failed" to DiagnosticBundle.message("heap.dump.analysis.out.of.memory"))
  }

  @Test
  fun `other errors show the existing failure notification`(@TestDisposable disposable: Disposable) {
    val notifications = collectHeapDumpAnalysisNotifications(disposable)

    LoggedErrorProcessor.executeAndReturnLoggedError {
      createAnalysisTask().onThrowable(IOException("Map failed"))
    }

    assertThat(notifications.map { it.content }).containsExactly(DiagnosticBundle.message("heap.dump.analysis.exception"))
  }

  private fun collectHeapDumpAnalysisNotifications(disposable: Disposable): List<Notification> {
    val notifications = mutableListOf<Notification>()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == "Heap Dump Analysis") {
          notifications.add(notification)
        }
      }
    })
    return notifications
  }

  // AnalysisRunnable, AnalysisTask and HeapReportProperties are internal to intellij.platform.ide.hprof.
  private fun createAnalysisTask(): Task.Backgroundable {
    val heapProperties = Class.forName("com.intellij.diagnostic.report.HeapReportProperties").declaredConstructors
      .single { it.parameterCount == 3 }
      .apply { isAccessible = true }
      .newInstance(MemoryReportReason.OutOfMemory, "", "")
    val analysisRunnable = Class.forName("com.intellij.diagnostic.hprof.action.AnalysisRunnable").declaredConstructors.single()
      .apply { isAccessible = true }
      .newInstance(Path.of("unused.hprof"), heapProperties, false)
    return Class.forName("com.intellij.diagnostic.hprof.action.AnalysisRunnable\$AnalysisTask").declaredConstructors.single()
      .apply { isAccessible = true }
      .newInstance(analysisRunnable) as Task.Backgroundable
  }
}
