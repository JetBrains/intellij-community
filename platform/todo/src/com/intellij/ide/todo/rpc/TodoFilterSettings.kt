// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.todo.TodoFilter
import com.intellij.psi.search.TodoAttributes
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoPattern
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TodoPatternConfig(
  val pattern: String,
  val isCaseSensitive: Boolean,
)

@ApiStatus.Internal
@Serializable
data class TodoFilterConfig(
  val name: String? = null,
  val patterns: List<TodoPatternConfig> = emptyList(),
)

@ApiStatus.Internal
fun TodoFilter.toConfig(): TodoFilterConfig {
  val patterns = mutableListOf<TodoPatternConfig>()
  val it = iterator()
  while (it.hasNext()) {
    val pattern = it.next()
    patterns.add(TodoPatternConfig(pattern.patternString, pattern.isCaseSensitive))
  }
  return TodoFilterConfig(name, patterns)
}

@ApiStatus.Internal
fun TodoFilterConfig.toTodoFilter(): TodoFilter {
  return TodoFilter().apply {
    name?.let { setName(it) }
    patterns.forEach { config: TodoPatternConfig ->
      val patternString = config.pattern
      val pattern = TodoPattern(patternString, TodoAttributes(TodoAttributesUtil.getDefaultColorSchemeTextAttributes()), config.isCaseSensitive)
      addTodoPattern(pattern)
    }
  }
}