// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal

private val EP_NAME: ExtensionPointName<ProjectClosingTransitionHandler> =
  ExtensionPointName("com.intellij.projectClosingTransitionHandler")

@Internal
fun findProjectClosingTransitionHandler(projectToClose: Project): (suspend () -> Project?)? {
  return EP_NAME.extensionsIfPointIsRegistered.firstNotNullOfOrNull { it.createTransitionHandler(projectToClose) }
}

/**
 * Handles the transition when the user is closing [Project] and the IDE should move to the no-project state
 * (typically a welcome screen) instead of just closing the window.
 *
 * Returning a non-null handler signals that this extension wants to take over the close + transition. Implementations are
 * expected to close the project as part of opening their target (typically by passing it as `projectToClose` to
 * so that the project's frame is reused for the new project.
 *
 * This extension point is intentionally opt-in per IDE: only IDEs that register an implementation get the
 * frame-reusing close transition. When no implementation is registered (or all return `null`), the platform falls back
 * to the regular close behavior.
 */
@Internal
interface ProjectClosingTransitionHandler {
  fun createTransitionHandler(projectToClose: Project): (suspend () -> Project?)?
}
