// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.stacktrace.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class StackTracePluginScope(private val coroutineScope: CoroutineScope) {
  companion object {
    fun createChildScope(project: Project): CoroutineScope {
      return scope(project).childScope("DevKitStackTracePlugin")
    }

    fun scope(project: Project): CoroutineScope {
      return project.service<StackTracePluginScope>().coroutineScope
    }
  }
}
