// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.undo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.ProjectCommandTracker
import com.intellij.openapi.command.impl.SeparatedCommandListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A [ProjectCommandTracker] is inside a command during a command of its project and during a transparent action.
 * A command of another project and a command without a project do not count.
 */
@TestApplication
internal class ProjectCommandTrackerTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
    val otherProject = projectFixture(openAfterCreation = true)
  }

  @Test
  fun `a command of the project counts`() {
    val inside = insideCommandDuring { task ->
      CommandProcessor.getInstance().executeCommand(project.get(), task, "own command", null)
    }
    assertTrue(inside)
  }

  @Test
  fun `a transparent action counts`() {
    val inside = insideCommandDuring { task ->
      CommandProcessor.getInstance().runUndoTransparentAction(task)
    }
    assertTrue(inside)
  }

  @Test
  fun `a command of another project does not count`() {
    val inside = insideCommandDuring { task ->
      CommandProcessor.getInstance().executeCommand(otherProject.get(), task, "other command", null)
    }
    assertFalse(inside)
  }

  @Test
  fun `a command without a project does not count`() {
    val inside = insideCommandDuring { task ->
      CommandProcessor.getInstance().executeCommand(null, task, "command without project", null)
    }
    assertFalse(inside)
  }

  /**
   * Connects a tracker of [project] and returns whether it is inside a command while [run] runs the task it gets.
   * Checks that the tracker is not inside a command after [run].
   */
  private fun insideCommandDuring(run: (Runnable) -> Unit): Boolean {
    return timeoutRunBlocking(context = Dispatchers.EDT) {
      val tracker = Tracker(project.get())
      val disposable = Disposer.newDisposable()
      try {
        project.get().messageBus.connect(disposable).subscribe(SeparatedCommandListener.TOPIC, tracker)
        var inside = false
        run(Runnable { inside = tracker.isInside() })
        assertFalse(tracker.isInside())
        inside
      } finally {
        Disposer.dispose(disposable)
      }
    }
  }
}

private class Tracker(project: Project) : ProjectCommandTracker(project) {
  fun isInside(): Boolean {
    return isInsideCommand
  }
}
