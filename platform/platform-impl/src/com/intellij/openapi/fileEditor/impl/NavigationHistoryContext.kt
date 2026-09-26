// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.asContextElement
import org.jetbrains.annotations.ApiStatus

/** Limits command-history suppression to the coroutine performing a navigation operation. */
@ApiStatus.Internal
object NavigationHistoryContext {
  private val currentSnapshot = ThreadLocal<IdeDocumentHistory.NavigationHistorySnapshot?>()

  val isActive: Boolean
    get() = currentSnapshot.get() != null

  fun withContextElement(snapshot: IdeDocumentHistory.NavigationHistorySnapshot): ThreadContextElement<IdeDocumentHistory.NavigationHistorySnapshot?> {
    return currentSnapshot.asContextElement(snapshot)
  }
}
