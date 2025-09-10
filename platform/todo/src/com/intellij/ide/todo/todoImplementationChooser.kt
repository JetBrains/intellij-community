// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun shouldUseSplitTodo(): Boolean {
  val shouldUseSplitTodo = Registry.`is`("todo.toolwindow.split", true)
  fileLogger().debug("Using TODO  ${if (shouldUseSplitTodo) "split" else "fallback"} implementation")
  return shouldUseSplitTodo
}