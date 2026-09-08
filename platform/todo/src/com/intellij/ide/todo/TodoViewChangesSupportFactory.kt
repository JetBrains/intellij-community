// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TodoViewChangesSupportFactory {

  fun isAvailable(): Boolean

  fun create(project: Project): TodoViewChangesSupport

  companion object {
    internal val EP_NAME: ExtensionPointName<TodoViewChangesSupportFactory> =
      ExtensionPointName.create("com.intellij.todoViewChangesSupportFactory")
  }
}
