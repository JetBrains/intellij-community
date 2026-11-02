// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.mock.MockProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.WindowManagerListener
import com.intellij.testFramework.LoggedErrorProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/** The record of the frame owners, the frame guests and the active projects, without a frame. */
internal class ProjectFrameAssignmentsTest {
  /** One `onActiveProjectChanged` event of the publisher. */
  private data class ActiveChanged(val frameOwner: Project, val active: Project)

  private val disposable = Disposer.newDisposable()
  private val events = ArrayList<ActiveChanged>()
  private val assignments = ProjectFrameAssignments {
    object : WindowManagerListener {
      override fun onFramesChanged() {
      }

      override fun onActiveProjectChanged(frameOwner: Project, activeProject: Project) {
        events.add(ActiveChanged(frameOwner, activeProject))
      }
    }
  }
  private val owner: Project = MockProject(null, disposable)
  private val first: Project = MockProject(null, disposable)
  private val second: Project = MockProject(null, disposable)

  @AfterEach
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun `a project without a record owns itself`() {
    assertThat(assignments.ownerOf(owner)).isSameAs(owner)
    assertThat(assignments.isGuest(owner)).isFalse()
    assertThat(assignments.activeProjectOf(owner)).isSameAs(owner)
    assertThat(assignments.frameHelperOf(owner)).isNull()
    assertThat(assignments.setActive(owner)).isNull()
  }

  @Test
  fun `a guest joins before its owner has a frame`() {
    assignments.addGuest(owner, first)

    assertThat(assignments.isGuest(first)).isTrue()
    assertThat(assignments.ownerOf(first)).isSameAs(owner)
    assertThat(assignments.guestsOf(owner)).containsExactly(first)
    assertThat(assignments.frameHelperOf(first)).isNull()
    assertThat(assignments.activeProjectOf(first)).isSameAs(owner)
    assertThat(assignments.assignmentOf(owner)!!.frameHelper).isNull()
    assertThat(events).describedAs("a join changes no active project").isEmpty()
  }

  @Test
  fun `a second join of the same guest changes nothing`() {
    assignments.addGuest(owner, first)
    assignments.addGuest(owner, first)

    assertThat(assignments.guestsOf(owner)).containsExactly(first)
    assertThat(events).describedAs("a join changes no active project").isEmpty()
  }

  @Test
  fun `the active project follows a select and falls back to the most recently active guest`() {
    assignments.addGuest(owner, first)
    assignments.addGuest(owner, second)
    events.clear()

    assertThat(assignments.setActive(second)!!.active).isSameAs(second)
    assertThat(assignments.setActive(first)!!.active).isSameAs(first)
    assertThat(assignments.setActive(first)).isNull()
    assertThat(assignments.activeProjectOf(owner)).isSameAs(first)
    assertThat(assignments.activeProjectOf(second)).isSameAs(first)

    val firstLeft = assignments.removeGuest(first)!!
    assertThat(firstLeft.frameOwner).isSameAs(owner)
    val firstChange = firstLeft.activeChange!!
    assertThat(firstChange.previousActive).isSameAs(first)
    assertThat(firstChange.active).isSameAs(second)
    assertThat(assignments.isGuest(first)).isFalse()
    assertThat(assignments.guestsOf(owner)).containsExactly(second)

    val secondLeft = assignments.removeGuest(second)!!
    assertThat(secondLeft.activeChange!!.active).isSameAs(owner)
    assertThat(assignments.assignmentOf(owner)).describedAs("a record without a frame goes with its last guest").isNull()

    assertThat(events).containsExactly(
      ActiveChanged(owner, second),
      ActiveChanged(owner, first),
      ActiveChanged(owner, second),
      ActiveChanged(owner, owner),
    )
  }

  @Test
  fun `a guest that is not active leaves without an active change`() {
    assignments.addGuest(owner, first)
    assignments.addGuest(owner, second)
    assignments.setActive(second)

    val left = assignments.removeGuest(first)!!
    assertThat(left.activeChange).isNull()
    assertThat(assignments.activeProjectOf(owner)).isSameAs(second)
  }

  @Test
  fun `a guest that never was active falls back to the latest joined guest`() {
    assignments.addGuest(owner, first)
    assignments.addGuest(owner, second)
    assignments.setActive(first)

    assertThat(assignments.removeGuest(first)!!.activeChange!!.active).isSameAs(second)
  }

  @Test
  fun `the owner itself can be the active project again`() {
    assignments.addGuest(owner, first)
    assignments.setActive(first)

    assertThat(assignments.setActive(owner)!!.active).isSameAs(owner)
    assertThat(assignments.activeProjectOf(first)).isSameAs(owner)
    // the owner is not a guest, so it is not in the activation order
    assertThat(assignments.removeGuest(first)!!.activeChange).isNull()
  }

  @Test
  fun `a released owner takes its guests out of the record`() {
    assignments.addGuest(owner, first)
    assignments.addGuest(owner, second)

    val released = assignments.releaseOwner(owner)!!
    assertThat(released.guests).containsExactly(first, second)
    assertThat(assignments.isGuest(first)).isFalse()
    assertThat(assignments.isGuest(second)).isFalse()
    assertThat(assignments.assignmentOf(owner)).isNull()
    assertThat(assignments.releaseOwner(owner)).isNull()
  }

  @Test
  fun `a project that is not a guest does not leave`() {
    assertThat(assignments.removeGuest(first)).isNull()
    assertThat(events).isEmpty()
  }

  @Test
  fun `a guest cannot host a guest, and a guest cannot join a second frame`() {
    assignments.addGuest(owner, first)
    LoggedErrorProcessor.executeAndReturnLoggedError { assignments.addGuest(first, second) }
    LoggedErrorProcessor.executeAndReturnLoggedError { assignments.addGuest(second, first) }

    assertThat(assignments.isGuest(second)).isFalse()
    assertThat(assignments.guestsOf(first)).isEmpty()
    assertThat(assignments.ownerOf(first)).isSameAs(owner)
    assertThat(assignments.assignmentOf(second)).isNull()
  }

  @Test
  fun `a frame owner cannot join a frame as a guest`() {
    assignments.addGuest(owner, first)
    LoggedErrorProcessor.executeAndReturnLoggedError { assignments.addGuest(second, owner) }

    assertThat(assignments.isGuest(owner)).isFalse()
    assertThat(assignments.assignmentOf(second)).isNull()
  }
}
