// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai.assistedReview.llm

import ai.grazie.model.task.data.TaskStreamData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

suspend fun Flow<TaskStreamData>.waitForCompletion(): String {
  if (this is SharedFlow) {
    error("Cannot wait for completion of a hot flow. It will never complete.")
  }

  val result = StringBuilder()
  collect { result.append(it.content) }

  return result.toString()
}
