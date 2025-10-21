// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Internal

private val EP_NAME: ExtensionPointName<NoProjectStateHandler> = ExtensionPointName("com.intellij.noProjectStateHandler")

@Internal
fun findNoProjectStateHandler(): (suspend () -> Project?)? {
  return EP_NAME.extensionList.firstNotNullOfOrNull { it.createHandler() }
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
}
