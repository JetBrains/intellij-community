// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WindowManagerListener
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG = logger<ProjectFrameAssignments>()

/**
 * The projects of one frame: the frame owner, its frame helper, the frame guests, and the active project.
 *
 * [frameHelper] is `null` while [owner] has no frame yet, and in a headless application.
 * [guests] are in the order they joined. [activation] holds the guests in the order of their activation, the most recent last.
 * [active] is [owner] or a guest: the project that the frame UI gives to an action.
 */
@Internal
class FrameAssignment internal constructor(
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
  ): FrameAssignment = FrameAssignment(owner, frameHelper, guests, activation, active)
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
class FrameGuestLeft internal constructor(
  @JvmField val frameOwner: Project,
  @JvmField val activeChange: ActiveProjectChange?,
)

/**
 * The one record of which frame shows each open project.
 *
 * A project owns a frame, or it is a frame guest: a project with no frame of its own that the frame of its owner shows.
 * Each frame has one active project, the project that the frame UI gives to an action, see `WindowManagerEx.getActiveProject`.
 *
 * The record is independent of the UI: a guest joins before its startup activities run, and before its owner has a frame.
 * `WindowManagerImpl` and `TestWindowManager` each hold one instance, so a unit test sees the lifecycle of the IDE.
 *
 * A write takes the lock and swaps the snapshots. A read uses the snapshots without the lock, on any thread.
 * Each change is published through [publisher] after the lock is released, on the calling thread.
 */
@Internal
class ProjectFrameAssignments(private val publisher: () -> WindowManagerListener) {
  private val lock = ReentrantLock()

  @Volatile
  private var byOwner: Map<Project, FrameAssignment> = emptyMap()

  @Volatile
  private var ownerOfGuest: Map<Project, Project> = emptyMap()

  /** The record of every frame owner. */
  fun assignments(): Collection<FrameAssignment> = byOwner.values

  /** The record of [frameOwner], or `null` when [frameOwner] owns no frame and has no guest. */
  fun assignmentOf(frameOwner: Project): FrameAssignment? = byOwner[frameOwner]

  /** The project that owns the frame that shows [project]: [project] itself unless it is a frame guest. */
  fun ownerOf(project: Project): Project = ownerOfGuest[project] ?: project

  fun isGuest(project: Project): Boolean = project in ownerOfGuest

  fun guestsOf(frameOwner: Project): List<Project> = byOwner[frameOwner]?.guests ?: emptyList()

  /** The frame helper of the frame that shows [project], for an owner and for a guest. */
  fun frameHelperOf(project: Project): ProjectFrameHelper? = byOwner[ownerOf(project)]?.frameHelper

  /** The active project of the frame that shows [project]: the owner of that frame when the frame has no active guest. */
  fun activeProjectOf(project: Project): Project {
    val owner = ownerOf(project)
    val active = byOwner[owner]?.active ?: return owner
    return if (active.isDisposed) owner else active
  }

  /**
   * Records [frameHelper] as the frame of [owner]. A record that a guest created first is completed.
   * A second frame for the same owner replaces the first one, with an error in the log.
   */
  fun assignOwner(owner: Project, frameHelper: ProjectFrameHelper) {
    lock.withLock {
      val ownerOfOwner = ownerOfGuest[owner]
      if (ownerOfOwner != null) {
        LOG.error("$owner is a frame guest of $ownerOfOwner and cannot own a frame")
        return
      }
      val current = byOwner[owner]
      if (current?.frameHelper != null) {
        LOG.error("$owner owns a frame already")
      }
      val assignment = current?.copy(frameHelper = frameHelper) ?: FrameAssignment(owner, frameHelper, emptyList(), emptyList(), owner)
      byOwner = byOwner + (owner to assignment)
    }
  }

  /**
   * Removes the record of [owner].
   *
   * @return the removed record, with the guests that were still in the frame, or `null` when [owner] had no record
   */
  fun releaseOwner(owner: Project): FrameAssignment? {
    lock.withLock {
      val released = byOwner[owner] ?: return null
      byOwner = byOwner - owner
      if (released.guests.isNotEmpty()) {
        ownerOfGuest = ownerOfGuest - released.guests.toSet()
      }
      return released
    }
  }

  /**
   * Records [guest] as a frame guest of [frameOwner]. The frame of [frameOwner] may not exist yet.
   * A guest that is in the frame already stays as it is. A guest cannot own a frame, and a guest cannot host a guest.
   */
  fun addGuest(frameOwner: Project, guest: Project) {
    lock.withLock {
      val ownerOfOwner = ownerOfGuest[frameOwner]
      if (ownerOfOwner != null) {
        LOG.error("$frameOwner is a frame guest of $ownerOfOwner and cannot host $guest")
        return
      }
      if (guest === frameOwner || guest in byOwner) {
        LOG.error("$guest owns a frame and cannot join the frame of $frameOwner")
        return
      }
      val currentOwner = ownerOfGuest[guest]
      if (currentOwner != null) {
        if (currentOwner !== frameOwner) {
          LOG.error("$guest is a frame guest of $currentOwner and cannot join the frame of $frameOwner")
        }
        return
      }
      val current = byOwner[frameOwner] ?: FrameAssignment(frameOwner, null, emptyList(), emptyList(), frameOwner)
      byOwner = byOwner + (frameOwner to current.copy(guests = current.guests + guest, activation = current.activation + guest))
      ownerOfGuest = ownerOfGuest + (guest to frameOwner)
    }
    publisher().onFrameGuestsChanged(frameOwner)
  }

  /**
   * Takes [guest] out of its frame. When [guest] was the active project, the most recently activated remaining guest
   * becomes active, else the owner.
   *
   * @return what changed, or `null` when [guest] is not a frame guest
   */
  fun removeGuest(guest: Project): FrameGuestLeft? {
    val left: FrameGuestLeft
    lock.withLock {
      val frameOwner = ownerOfGuest[guest] ?: return null
      val current = byOwner.getValue(frameOwner)
      val guests = current.guests - guest
      val activation = current.activation - guest
      val active = if (current.active === guest) activation.lastOrNull { !it.isDisposed } ?: frameOwner else current.active
      ownerOfGuest = ownerOfGuest - guest
      // a record without a frame exists for its guests only
      byOwner = if (current.frameHelper == null && guests.isEmpty()) {
        byOwner - frameOwner
      }
      else {
        byOwner + (frameOwner to current.copy(guests = guests, activation = activation, active = active))
      }
      val activeChange = if (active === current.active) null else ActiveProjectChange(frameOwner, current.active, active)
      left = FrameGuestLeft(frameOwner, activeChange)
    }
    val listener = publisher()
    listener.onFrameGuestsChanged(left.frameOwner)
    left.activeChange?.let { listener.onActiveProjectChanged(it.frameOwner, it.active) }
    return left
  }

  /**
   * Makes [project] the active project of the frame that shows it.
   *
   * @return the change, or `null` when no frame shows [project] or when [project] is active already
   */
  fun setActive(project: Project): ActiveProjectChange? {
    val change: ActiveProjectChange
    lock.withLock {
      val frameOwner = ownerOf(project)
      val current = byOwner[frameOwner] ?: return null
      if (current.active === project) return null
      val activation = if (project === frameOwner) current.activation else current.activation - project + project
      byOwner = byOwner + (frameOwner to current.copy(activation = activation, active = project))
      change = ActiveProjectChange(frameOwner, current.active, project)
    }
    publisher().onActiveProjectChanged(change.frameOwner, change.active)
    return change
  }
}
