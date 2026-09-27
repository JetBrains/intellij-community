// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.TaskInfoEntity
import com.intellij.platform.ide.progress.TaskStorage
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.kernel.withKernel
import com.intellij.testFramework.junit5.TestApplication
import fleet.kernel.ChangeInterceptor
import fleet.kernel.change
import fleet.util.UID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
internal class TaskStorageTest {

  /**
   * `change` commits and then catches up with the DB; a cancellation in between (IJPL-256852) used to leave
   * the committed task in the DB with nothing to delete it, so the progress stayed in the status bar forever.
   */
  @Test
  fun `a task cancelled after its creation is committed leaves no entity`(): Unit = runBlocking {
    val title = "test task ${UID.random()}"
    val cancelOnce = AtomicBoolean(true)
    val cancelAfterCommit = ChangeInterceptor("cancel after commit") { changeFn, next ->
      val change = next(changeFn)
      if (cancelOnce.getAndSet(false)) throw CancellationException("cancelled after commit")
      change
    }

    withContext(cancelAfterCommit) {
      assertThrows<CancellationException> {
        TaskStorage.getInstance().addTask(ProjectManager.getInstance().defaultProject, title, TaskCancellation.nonCancellable(),
                                          TaskSuspension.NonSuspendable, visibleInStatusBar = true)
      }
    }

    withKernel {
      assertEquals(0, change { TaskInfoEntity.all().count { it.title == title } })
    }
  }
}
