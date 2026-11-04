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
internal class IdeFrameRegistryTest {
  /** One `onActiveProjectChanged` event of the publisher. */
  private data class ActiveChanged(val frameOwner: Project, val active: Project)

  private val disposable = Disposer.newDisposable()
  private val events = ArrayList<ActiveChanged>()
  private val registry = IdeFrameRegistry {
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
    assertThat(registry.ownerOf(owner)).isSameAs(owner)
    assertThat(registry.isGuest(owner)).isFalse()
    assertThat(registry.activeProjectOf(owner)).isSameAs(owner)
    assertThat(registry.frameHelperOf(owner)).isNull()
    assertThat(registry.setActive(owner)).isNull()
  }

  @Test
  fun `a guest joins before its owner has a frame`() {
    registry.addGuest(owner, first)

    assertThat(registry.isGuest(first)).isTrue()
    assertThat(registry.ownerOf(first)).isSameAs(owner)
    assertThat(registry.guestsOf(owner)).containsExactly(first)
    assertThat(registry.frameHelperOf(first)).isNull()
    assertThat(registry.activeProjectOf(first)).isSameAs(owner)
    assertThat(registry.frameOf(owner)!!.frameHelper).isNull()
    assertThat(events).describedAs("a join changes no active project").isEmpty()
  }

  @Test
  fun `a second join of the same guest changes nothing`() {
    registry.addGuest(owner, first)
    registry.addGuest(owner, first)

    assertThat(registry.guestsOf(owner)).containsExactly(first)
    assertThat(events).describedAs("a join changes no active project").isEmpty()
  }

  @Test
  fun `the active project follows a select and falls back to the most recently active guest`() {
    registry.addGuest(owner, first)
    registry.addGuest(owner, second)
    events.clear()

    assertThat(registry.setActive(second)!!.active).isSameAs(second)
    assertThat(registry.setActive(first)!!.active).isSameAs(first)
    assertThat(registry.setActive(first)).isNull()
    assertThat(registry.activeProjectOf(owner)).isSameAs(first)
    assertThat(registry.activeProjectOf(second)).isSameAs(first)

    val firstLeft = registry.removeGuest(first)!!
    assertThat(firstLeft.frameOwner).isSameAs(owner)
    val firstChange = firstLeft.activeChange!!
    assertThat(firstChange.previousActive).isSameAs(first)
    assertThat(firstChange.active).isSameAs(second)
    assertThat(registry.isGuest(first)).isFalse()
    assertThat(registry.guestsOf(owner)).containsExactly(second)

    val secondLeft = registry.removeGuest(second)!!
    assertThat(secondLeft.activeChange!!.active).isSameAs(owner)
    assertThat(registry.frameOf(owner)).describedAs("a record without a frame goes with its last guest").isNull()

    assertThat(events).containsExactly(
      ActiveChanged(owner, second),
      ActiveChanged(owner, first),
      ActiveChanged(owner, second),
      ActiveChanged(owner, owner),
    )
  }

  @Test
  fun `a guest that is not active leaves without an active change`() {
    registry.addGuest(owner, first)
    registry.addGuest(owner, second)
    registry.setActive(second)

    val left = registry.removeGuest(first)!!
    assertThat(left.activeChange).isNull()
    assertThat(registry.activeProjectOf(owner)).isSameAs(second)
  }

  @Test
  fun `a guest that never was active falls back to the latest joined guest`() {
    registry.addGuest(owner, first)
    registry.addGuest(owner, second)
    registry.setActive(first)

    assertThat(registry.removeGuest(first)!!.activeChange!!.active).isSameAs(second)
  }

  @Test
  fun `the owner itself can be the active project again`() {
    registry.addGuest(owner, first)
    registry.setActive(first)

    assertThat(registry.setActive(owner)!!.active).isSameAs(owner)
    assertThat(registry.activeProjectOf(first)).isSameAs(owner)
    // the owner is not a guest, so it is not in the activation order
    assertThat(registry.removeGuest(first)!!.activeChange).isNull()
  }

  @Test
  fun `a released owner takes its guests out of the record`() {
    registry.addGuest(owner, first)
    registry.addGuest(owner, second)

    val released = registry.removeOwner(owner)!!
    assertThat(released.guests).containsExactly(first, second)
    assertThat(registry.isGuest(first)).isFalse()
    assertThat(registry.isGuest(second)).isFalse()
    assertThat(registry.frameOf(owner)).isNull()
    assertThat(registry.removeOwner(owner)).isNull()
  }

  @Test
  fun `a project that is not a guest does not leave`() {
    assertThat(registry.removeGuest(first)).isNull()
    assertThat(events).isEmpty()
  }

  @Test
  fun `a guest cannot host a guest, and a guest cannot join a second frame`() {
    registry.addGuest(owner, first)
    LoggedErrorProcessor.executeAndReturnLoggedError { registry.addGuest(first, second) }
    LoggedErrorProcessor.executeAndReturnLoggedError { registry.addGuest(second, first) }

    assertThat(registry.isGuest(second)).isFalse()
    assertThat(registry.guestsOf(first)).isEmpty()
    assertThat(registry.ownerOf(first)).isSameAs(owner)
    assertThat(registry.frameOf(second)).isNull()
  }

  @Test
  fun `a frame owner cannot join a frame as a guest`() {
    registry.addGuest(owner, first)
    LoggedErrorProcessor.executeAndReturnLoggedError { registry.addGuest(second, owner) }

    assertThat(registry.isGuest(owner)).isFalse()
    assertThat(registry.frameOf(second)).isNull()
  }

  @Test
  fun `a disposed guest is not active and cannot become active`() {
    val guestDisposable = Disposer.newDisposable(disposable, "guest")
    val guest: Project = MockProject(null, guestDisposable)
    registry.addGuest(owner, guest)
    registry.setActive(guest)
    Disposer.dispose(guestDisposable)

    assertThat(registry.activeProjectOf(owner)).describedAs("a disposed active guest gives way to the owner").isSameAs(owner)
    assertThat(registry.setActive(guest)).isNull()
    assertThat(registry.activeProjectOf(guest)).isSameAs(owner)
  }
}
