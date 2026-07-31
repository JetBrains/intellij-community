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

  /**
   * Whether this provider's empty state is the focus target of an editor area that shows it — the counterpart, for an area with no
   * editor in it, of the composite the platform focuses when it opens one.
   *
   * Asked synchronously and before the component exists, because project open decides what to focus while the empty state is still
   * being prepared. A provider that claims focus is focused where an editor would have been: after project open finds no editors to
   * restore, and after the user closes the area's last tab. It is also offered as the area's default focus component, so
   * <kbd>Esc</kbd> and Focus Editor reach it. `false` — the default — keeps an empty state out of the focus path entirely.
   *
   * Claiming focus is not the same as taking it: focus is never moved out of a tool window the user is working in.
   */
  fun claimsFocus(splitters: EditorsSplitters): Boolean = false

  /**
   * The component to focus inside [component], which this provider created — the editable field of a composer rather than its host
   * panel, say. Consulted only for a provider that returns `true` from [claimsFocus]; `null` means there is nothing to focus yet.
   *
   * `null` is also the default, rather than [component] itself: a host panel is usually not focusable, so returning it would answer a
   * claim with a focus request that quietly does nothing, where `null` tells the platform that the claim it made on this area's focus
   * cannot be kept and lets whoever stood down for it focus instead.
   */
  fun getPreferredFocusedComponent(component: JComponent): JComponent? = null

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
 *
 * Two limits of that split are worth knowing, because both understate the UI-thread half or the whole:
 * - this measures wall time inside [block], so a suspension point that leaves the UI thread inside it is still charged to the UI budget;
 * - only what a provider routes through here is counted, so a provider that hops on its own, or that loses the
 *   [EditorEmptyStateUiBuildTime] context element on the way, reports no UI time at all.
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
