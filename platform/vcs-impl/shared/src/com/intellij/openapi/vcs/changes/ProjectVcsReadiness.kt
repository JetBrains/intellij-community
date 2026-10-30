// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import org.jetbrains.annotations.ApiStatus

/** Declared in VcsExtensions.xml. The Git branches popup also reads it, and hides its Commit action while this is off. */
@ApiStatus.Internal
const val RECENT_PROJECT_COMMIT: String = "vcs.recent.project.commit"

/**
 * Whether [this] project commits from a window of its own instead of the Commit tool window.
 *
 * The welcome screen loads a recent project with no frame of its own, so the project has no tool window. The welcome screen commits
 * such a project from a window, behind [RECENT_PROJECT_COMMIT].
 */
@get:ApiStatus.Internal
val Project.isCommitHostedInWindow: Boolean
  get() = Registry.`is`(RECENT_PROJECT_COMMIT) &&
          ApplicationManager.getApplication().service<ProjectFrameCapabilitiesService>().has(this, ProjectFrameCapability.NO_FRAME_OF_ITS_OWN)

/**
 * Whether [this] project computes its changes.
 *
 * An open project always does. A project that [isCommitHostedInWindow] does too, because its Commit action needs its changes. That action
 * and this tracking are the same feature, so they are turned on together.
 */
@get:ApiStatus.Internal
val Project.tracksChanges: Boolean
  get() = isOpen || isCommitHostedInWindow

/**
 * Whether the changes of [this] project may be built into a tree.
 *
 * [Project.isInitialized] is the usual answer, but it requires the project to be open. A project that [isCommitHostedInWindow] is never
 * open. It is live and it [tracksChanges] all the same, and it shows its changes in a window of its own. Only that case is added. An open
 * project still waits to be initialized, so nothing builds a tree while it is still coming up.
 */
@get:ApiStatus.Internal
val Project.isReadyForChangesView: Boolean
  get() = isInitialized || (isCommitHostedInWindow && !isDisposed)
