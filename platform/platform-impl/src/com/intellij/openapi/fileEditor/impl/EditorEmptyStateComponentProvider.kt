// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.application.UI
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import kotlin.time.TimeSource

@ApiStatus.Internal
interface EditorEmptyStateComponentProvider {
  fun getKind(): Kind = Kind.RICH

  fun isAvailable(splitters: EditorsSplitters): Boolean = true

  /**
   * Called asynchronously by the editor host, off the EDT. Implementations should choose their dispatcher explicitly, and own the hop
   * to the EDT they need to build Swing components on — through [buildEditorEmptyStateComponentOnUiThread], so that the host can
   * budget that half separately.
   *
   * The two halves are budgeted apart, because they cost different things: 100 ms of UI-thread time is a startup freeze whatever the
   * provider's kind, while time spent off it only delays the component — 100 ms for [Kind.FALLBACK], which has nothing to resolve
   * before it builds, and one second for [Kind.RICH], which may resolve services and query a backend first. Exceeding either budget
   * is reported as a warning.
   *
   * The component is not necessarily shown as soon as it is returned — the host may be holding presentation back while startup still
   * decides what the editor area will show.
   */
  suspend fun createComponent(splitters: EditorsSplitters): JComponent?

  fun disposeComponent(component: JComponent) {
  }

  enum class Kind {
    RICH,
    FALLBACK,
  }

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorEmptyStateComponentProvider> =
      ExtensionPointName("com.intellij.editorEmptyStateComponentProvider")
  }
}

/**
 * Runs the UI-thread half of [EditorEmptyStateComponentProvider.createComponent] — Swing construction — and reports how long it took
 * to the host.
 *
 * An empty state is prepared while the project is still opening, so what a provider does on the UI thread there freezes startup while
 * what it does off it only delays a component that nothing is waiting for. The host budgets the two separately, and can tell them
 * apart only for a provider that hops through here; a provider that needs the write-intent lock can still take it inside the block.
 *
 * Only time spent inside [block] is attributed to the UI thread: waiting for the thread to become free is contention, not this
 * provider's cost.
 */
@ApiStatus.Internal
suspend fun <T> buildEditorEmptyStateComponentOnUiThread(block: suspend CoroutineScope.() -> T): T {
  val uiBuildTime = currentCoroutineContext()[EditorEmptyStateUiBuildTime]
  return withContext(Dispatchers.UI) {
    if (uiBuildTime == null) {
      block()
    }
    else {
      val startedAt = TimeSource.Monotonic.markNow()
      try {
        block()
      }
      finally {
        uiBuildTime.add(startedAt.elapsedNow())
      }
    }
  }
}