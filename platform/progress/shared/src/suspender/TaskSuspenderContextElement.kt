// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.suspender

import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
object TaskSuspenderElementKey : CoroutineContext.Key<TaskSuspenderElement>

@ApiStatus.Internal
class TaskSuspenderElement(val taskSuspender: TaskSuspender) : AbstractCoroutineContextElement(TaskSuspenderElementKey)

/**
 * Converts this instance to a CoroutineContext element.
 *
 * This function is useful for integrating existing types or values
 * into coroutine-based APIs by converting them to coroutine context elements.
 *
 * @return a CoroutineContext element representing this instance.
 */
fun TaskSuspender.asContextElement(): CoroutineContext {
  return when (this) {
    is TaskSuspenderImpl -> this.getCoroutineContext()
    else -> TaskSuspenderElement(this)
  }
}
