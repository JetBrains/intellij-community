// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.picker

import com.intellij.devkit.icons.inspector.model.ComponentIconExtractor
import com.intellij.devkit.icons.inspector.model.ComposeIconIndex
import com.intellij.devkit.icons.inspector.model.ComposeIcons
import com.intellij.devkit.icons.inspector.model.PickedIcon
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import java.awt.Component
import java.awt.Point
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private val LOG = logger<ComposeHitTracker>()

/**
 * Compose caches drawings and has no Swing icon to ask, so icons-API icons inside Compose hosts are located by their own
 * draws: while this tracker is open, a render capture records every Compose icon draw (with its bounds) into an index.
 * Entering a host makes its icons draw again ([IconRenderDebugger.requestRedraw]); [onDraws] is called on the EDT
 * (coalesced) whenever new draws arrived, so callers can re-run their hit-test.
 *
 * Shared by the hover picker ([IconPicker]) and the one-shot pick of the global gesture ([OneShotIconPick]). EDT only,
 * except for the capture callback.
 */
internal class ComposeHitTracker(private val onDraws: () -> Unit) : AutoCloseable {
  private val index = ComposeIconIndex()
  private var capture: AutoCloseable? = null
  private val signal = AtomicBoolean()

  @Volatile
  private var closed = false

  /** The Compose host whose draws are being (re)collected; its icons are redrawn when it changes. */
  private var trackedHost: WeakReference<Component>? = null

  fun open() {
    if (capture != null || closed) return
    capture = IconRenderDebugger.capture({ paintTarget -> ComposeIcons.isComposeHost(paintTarget) }) { capture ->
      if (capture.composePaintInfo == null || closed) return@capture
      index.add(capture)
      if (signal.compareAndSet(false, true)) {
        SwingUtilities.invokeLater {
          signal.set(false)
          if (!closed) onDraws()
        }
      }
    }
  }

  /**
   * The icon under [point] (in [deepest] coordinates): the Compose icon when [deepest] is inside a Compose host (tracking that
   * host), else the Swing icon of the component or one of its parents.
   */
  fun find(deepest: Component, point: Point): PickedIcon? {
    val panel = ComposeIcons.composePanelOf(deepest)
    if (panel != null) {
      track(ComposeIcons.hostOf(panel))
      return ComponentIconExtractor.findComposeIcon(deepest, point, index)
    }
    trackedHost = null
    return ComponentIconExtractor.findIcon(deepest, point)
  }

  /** Whether [deepest] is inside a Compose host (whose icons are only known once they drew while this tracker is open). */
  fun isCompose(deepest: Component): Boolean = ComposeIcons.composePanelOf(deepest) != null

  /** On entering a Compose host: drop its (possibly stale) indexed draws and make its icons draw again. */
  private fun track(host: Component) {
    if (trackedHost?.get() === host) return
    trackedHost = WeakReference(host)
    index.clear(host)
    IconRenderDebugger.requestRedraw(host)
    host.repaint()
  }

  override fun close() {
    if (closed) return
    closed = true
    try {
      capture?.close()
    }
    catch (t: Throwable) {
      LOG.warn(t)
    }
    capture = null
    index.clearAll()
    trackedHost = null
  }
}
