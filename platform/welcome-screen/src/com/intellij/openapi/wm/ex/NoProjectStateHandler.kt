// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal

private val EP_NAME: ExtensionPointName<NoProjectStateHandler> = ExtensionPointName("com.intellij.noProjectStateHandler")

@Internal
fun findNoProjectStateHandler(): (suspend () -> Project?)? {
  return EP_NAME.extensionsIfPointIsRegistered.firstNotNullOfOrNull { it.createHandler() }
}

@Internal
fun findProjectCloseHandler(projectToClose: Project): (suspend () -> Project?)? {
  return EP_NAME.extensionsIfPointIsRegistered.firstNotNullOfOrNull { it.createCloseHandler(projectToClose) }
}

@Internal
suspend fun executeNoProjectStateHandlerExpectingNonWelcomeScreenImplementation(): Project {
  val handler = requireNotNull(findNoProjectStateHandler()) {
    "`NoProjectStateHandler` not found, but it must be registered"
  }
  val project = requireNotNull(handler()) {
    "Handler returned `null`, but it must return a project"
  }
  require(!project.isDefault) {
    "Handler returned a default project, but it must return a non-default project"
  }
  return project
}

@Internal
interface NoProjectStateHandler {
  fun createHandler(): (suspend () -> Project?)?

  /**
   * Handler invoked when the user is closing [projectToClose] and the IDE should transition to the no-project state.
   *
   * Returning a non-null handler signals that this extension wants to take over the close + transition. Implementations are expected to close the
   * project as part of opening their target (typically by passing it as `projectToClose` to [com.intellij.ide.impl.OpenProjectTask])
   * so that the project's frame is reused for the new project.
   *
   * Default implementation returns `null`, meaning the platform falls back to the regular close.
   */
  fun createCloseHandler(projectToClose: Project): (suspend () -> Project?)? = null
}
