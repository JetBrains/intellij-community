// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.configurable

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TodoConfigurableProductCallbackService {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TodoConfigurableProductCallbackService = project.service()
  }

  fun applyCallback()
}

internal class DefaultTodoConfigurableProductCallbackService(private val project: Project) : TodoConfigurableProductCallbackService {
  override fun applyCallback() { /*noop*/ }
}
