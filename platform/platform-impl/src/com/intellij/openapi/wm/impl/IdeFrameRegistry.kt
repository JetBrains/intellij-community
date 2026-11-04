// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WindowManagerListener
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG = logger<IdeFrameRegistry>()

/**
 * The projects of one frame: the frame owner, its frame helper, the frame guests, and the active project.
 *
 * [frameHelper] is `null` while [owner] has no frame yet, and in a headless application.
 * [guests] are in the order they joined. [activation] holds the guests in the order of their activation, the most recent last.
 * [active] is [owner] or a guest: the project that the frame UI gives to an action.
 */
@Internal
class IdeFrameProjects internal constructor(
  @JvmField val owner: Project,
  @JvmField val frameHelper: ProjectFrameHelper?,
  @JvmField val guests: List<Project>,
  @JvmField val activation: List<Project>,
  @JvmField val active: Project,
) {
  internal fun copy(
    frameHelper: ProjectFrameHelper? = this.frameHelper,
    guests: List<Project> = this.guests,
    activation: List<Project> = this.activation,
    active: Project = this.active,
  ): IdeFrameProjects = IdeFrameProjects(owner, frameHelper, guests, activation, active)
}

/** The active project of the frame of [frameOwner] changed from [previousActive] to [active]. */
@Internal
class ActiveProjectChange internal constructor(
  @JvmField val frameOwner: Project,
  @JvmField val previousActive: Project,
  @JvmField val active: Project,
)

/** A frame guest left the frame of [frameOwner]. [activeChange] is set when the guest was the active project. */
@Internal
class IdeFrameGuestLeft internal constructor(
  @JvmField val frameOwner: Project,
  @JvmField val activeChange: ActiveProjectChange?,
)

/**
 * The registry of the IDE frames: which frame shows each open project.
 *
 * A project owns a frame, or it is a frame guest: a project with no frame of its own that the frame of its owner shows.
 * Each frame has one active project, the project that the frame UI gives to an action, see `WindowManagerEx.getActiveProject`.
 *
 * The record is independent of the UI: a guest joins before its startup activities run, and before its owner has a frame.
 * `WindowManagerImpl` and `TestWindowManager` each hold one instance, so a unit test sees the lifecycle of the IDE.
 *
 * A write takes the lock and swaps one immutable [State]. A query reads that state once, without the lock, on any thread,
 * so its answer is the record before or after a write, never a mix of the two.
 * A change of the active project is published through [publisher] after the lock is released, on the calling thread.
 */
@Internal
class IdeFrameRegistry(private val publisher: () -> WindowManagerListener) {
  /** The record at one point in time: the frame of each owner, and the owner of each guest. */
  private class State(
    val byOwner: Map<Project, IdeFrameProjects>,
    val ownerOfGuest: Map<Project, Project>,
  ) {
    fun ownerOf(project: Project): Project = ownerOfGuest[project] ?: project

    /** The [IdeFrameProjects] of the frame that shows [project], for an owner and for a guest. */
    fun frameShowing(project: Project): IdeFrameProjects? = byOwner[ownerOf(project)]
  }

  private val lock = ReentrantLock()

  @Volatile
  private var state = State(byOwner = emptyMap(), ownerOfGuest = emptyMap())

  /** The [IdeFrameProjects] of every frame owner. */
  fun frames(): Collection<IdeFrameProjects> = state.byOwner.values

  /** The [IdeFrameProjects] of [frameOwner], or `null` when [frameOwner] owns no frame and has no guest. */
  fun frameOf(frameOwner: Project): IdeFrameProjects? = state.byOwner[frameOwner]

  /** The project that owns the frame that shows [project]: [project] itself unless it is a frame guest. */
  fun ownerOf(project: Project): Project = state.ownerOf(project)

  fun isGuest(project: Project): Boolean = project in state.ownerOfGuest

  fun guestsOf(frameOwner: Project): List<Project> = state.byOwner[frameOwner]?.guests ?: emptyList()

  /** The frame helper of the frame that shows [project], for an owner and for a guest. */
  fun frameHelperOf(project: Project): ProjectFrameHelper? = state.frameShowing(project)?.frameHelper

  /** The active project of the frame that shows [project]: the owner of that frame when the frame has no active guest. */
  fun activeProjectOf(project: Project): Project {
    val state = state
    val owner = state.ownerOf(project)
    val active = state.byOwner[owner]?.active ?: return owner
    return if (active.isDisposed) owner else active
  }

  /**
   * Records [frameHelper] as the frame of [owner]. The [IdeFrameProjects] that a guest created first is completed.
   * A second frame for the same owner replaces the first one, with an error in the log.
   */
  fun registerFrame(owner: Project, frameHelper: ProjectFrameHelper) {
    lock.withLock {
      val state = state
      val ownerOfOwner = state.ownerOfGuest[owner]
      if (ownerOfOwner != null) {
        LOG.error("$owner is a frame guest of $ownerOfOwner and cannot own a frame")
        return
      }
      val current = state.byOwner[owner]
      if (current?.frameHelper != null) {
        LOG.error("$owner owns a frame already")
      }
      val projects = current?.copy(frameHelper = frameHelper) ?: IdeFrameProjects(owner, frameHelper, emptyList(), emptyList(), owner)
      this.state = State(byOwner = state.byOwner + (owner to projects), ownerOfGuest = state.ownerOfGuest)
    }
  }

  /**
   * Removes the [IdeFrameProjects] of [owner].
   *
   * @return the removed [IdeFrameProjects], with the guests that were still in the frame, or `null` when [owner] had none
   */
  fun removeOwner(owner: Project): IdeFrameProjects? {
    lock.withLock {
      val state = state
      val released = state.byOwner[owner] ?: return null
      this.state = State(byOwner = state.byOwner - owner, ownerOfGuest = state.ownerOfGuest - released.guests.toSet())
      return released
    }
  }

  /**
   * Records [guest] as a frame guest of [frameOwner]. The frame of [frameOwner] may not exist yet.
   * A guest that is in the frame already stays as it is. A guest cannot own a frame, and a guest cannot host a guest.
   */
  fun addGuest(frameOwner: Project, guest: Project) {
    lock.withLock {
      val state = state
      val ownerOfOwner = state.ownerOfGuest[frameOwner]
      if (ownerOfOwner != null) {
        LOG.error("$frameOwner is a frame guest of $ownerOfOwner and cannot host $guest")
        return
      }
      if (guest === frameOwner || guest in state.byOwner) {
        LOG.error("$guest owns a frame and cannot join the frame of $frameOwner")
        return
      }
      val currentOwner = state.ownerOfGuest[guest]
      if (currentOwner != null) {
        if (currentOwner !== frameOwner) {
          LOG.error("$guest is a frame guest of $currentOwner and cannot join the frame of $frameOwner")
        }
        return
      }
      val current = state.byOwner[frameOwner] ?: IdeFrameProjects(frameOwner, null, emptyList(), emptyList(), frameOwner)
      val joined = current.copy(guests = current.guests + guest, activation = current.activation + guest)
      this.state = State(byOwner = state.byOwner + (frameOwner to joined), ownerOfGuest = state.ownerOfGuest + (guest to frameOwner))
    }
  }

  /**
   * Takes [guest] out of its frame. When [guest] was the active project, the most recently activated remaining guest
   * becomes active, else the owner.
   *
   * @return what changed, or `null` when [guest] is not a frame guest
   */
  fun removeGuest(guest: Project): IdeFrameGuestLeft? {
    val left: IdeFrameGuestLeft
    lock.withLock {
      val state = state
      val frameOwner = state.ownerOfGuest[guest] ?: return null
      val current = state.byOwner.getValue(frameOwner)
      val guests = current.guests - guest
      val activation = current.activation - guest
      val active = if (current.active === guest) activation.lastOrNull { !it.isDisposed } ?: frameOwner else current.active
      // a record without a frame exists for its guests only
      val byOwner = if (current.frameHelper == null && guests.isEmpty()) {
        state.byOwner - frameOwner
      }
      else {
        state.byOwner + (frameOwner to current.copy(guests = guests, activation = activation, active = active))
      }
      this.state = State(byOwner = byOwner, ownerOfGuest = state.ownerOfGuest - guest)
      val activeChange = if (active === current.active) null else ActiveProjectChange(frameOwner, current.active, active)
      left = IdeFrameGuestLeft(frameOwner, activeChange)
    }
    left.activeChange?.let { publisher().onActiveProjectChanged(it.frameOwner, it.active) }
    return left
  }

  /**
   * Makes [project] the active project of the frame that shows it.
   *
   * @return the change, or `null` when no frame shows [project], when [project] is active already, or when [project] is disposed
   */
  fun setActive(project: Project): ActiveProjectChange? {
    val change: ActiveProjectChange
    lock.withLock {
      if (project.isDisposed) return null
      val state = state
      val frameOwner = state.ownerOf(project)
      val current = state.byOwner[frameOwner] ?: return null
      if (current.active === project) return null
      val activation = if (project === frameOwner) current.activation else current.activation - project + project
      this.state = State(byOwner = state.byOwner + (frameOwner to current.copy(activation = activation, active = project)), ownerOfGuest = state.ownerOfGuest)
      change = ActiveProjectChange(frameOwner, current.active, project)
    }
    publisher().onActiveProjectChanged(change.frameOwner, change.active)
    return change
  }
}
