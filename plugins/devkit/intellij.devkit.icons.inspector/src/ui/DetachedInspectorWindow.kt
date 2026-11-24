// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.IconInspectorController
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import java.awt.Dialog
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent

/** Where the Icon Inspector of a project lives when no modal dialog is involved. Stored per project. */
internal enum class InspectorMode {
  /** The Icon Inspector tool window. */
  DOCKED,

  /** One separate window ([IconInspectorFrame]), see [DetachedWindowSlot]. */
  DETACHED,
}

/** A window that shows the detached inspector: the ownerless [IconInspectorFrame], or an [IconInspectorDialog]. */
internal interface DetachedInspectorHost {
  fun hostWindow(): Window?

  fun closeHost()
}

/**
 * Holds the single detached inspector window. There is at most one host at any time: to move the inspector to another
 * owner, the slot closes the current host before it opens the next one, and the next host gets the bounds of the closed one.
 *
 * The owner is `null` for the ownerless [IconInspectorFrame]. A modal dialog is the owner only when the toolkit cannot
 * exclude the frame from modality (see [isModalExclusionSupported]). Generic over the window types, so it is testable headless.
 */
internal class DetachedWindowSlot<W : Any, H : Any>(private val hosts: Hosts<W, H>) {
  interface Hosts<W, H> {
    fun open(owner: W?, bounds: Rectangle?): H

    fun close(host: H)

    fun bounds(host: H): Rectangle?

    fun toFront(host: H)
  }

  var current: H? = null
    private set

  var currentOwner: W? = null
    private set

  /** Shows the window with the owner [owner]: brings the current host to the front, or moves the inspector to a new host. */
  fun show(owner: W?) {
    val host = current
    if (host != null && currentOwner == owner) {
      hosts.toFront(host)
      return
    }
    val bounds = host?.let(hosts::bounds)
    clear()
    if (host != null) hosts.close(host)
    current = hosts.open(owner, bounds)
    currentOwner = owner
  }

  /** The owner [owner] of the current host closes: moves the inspector to [next] (the next showing modal dialog, or `null`). */
  fun ownerGone(owner: W, next: W?) {
    if (current != null && currentOwner == owner) show(next)
  }

  /** [host] is gone without the slot (for example, the user closed it). Returns `true` if it was the current host. */
  fun forget(host: H): Boolean {
    if (current !== host) return false
    clear()
    return true
  }

  fun close() {
    val host = current ?: return
    clear()
    hosts.close(host)
  }

  private fun clear() {
    current = null
    currentOwner = null
  }
}

/**
 * Whether the toolkit can exclude a top-level window from application modality, so that it stays usable while a modal
 * dialog is open. IntelliJ modal dialogs are `APPLICATION_MODAL` (see `DialogWrapper.IdeModalityType.toAwtModality`).
 * The Windows, X11 and macOS toolkits support it. Event dispatch in the IDE does not block an excluded window: AWT
 * decides the blocking, and `IdeEventQueue` filters events by window only for modal progress (`pumpEventsForHierarchy`).
 */
internal fun isModalExclusionSupported(): Boolean =
  !GraphicsEnvironment.isHeadless() &&
  Toolkit.getDefaultToolkit().isModalExclusionTypeSupported(Dialog.ModalExclusionType.APPLICATION_EXCLUDE)

/**
 * The detached Icon Inspector: an ownerless frame (a [FrameWrapper], so it has the IDE frame decorations and closes with
 * the project). It is ownerless, so that no window that a modal dialog blocks owns it. [excludeFromModality] makes it
 * stay usable while modal dialogs are open.
 *
 * [onUserClose] runs when the user closes the window with its close button; [onDock] runs for the "dock" button.
 */
internal class IconInspectorFrame(
  project: Project,
  controller: IconInspectorController,
  onDock: () -> Unit,
  private val excludeFromModality: Boolean,
  private val onUserClose: (IconInspectorFrame) -> Unit,
) : FrameWrapper(
  project = project,
  dimensionKey = DIMENSION_KEY,
  title = DevkitIconsInspectorBundle.message("dialogs.window.title"),
  component = createInspectorComponent(controller, onDock),
), DetachedInspectorHost {
  /** Shows the frame at [bounds], or at the remembered bounds when [bounds] is `null`. */
  fun open(bounds: Rectangle?) {
    val frame = getFrame()
    // before the frame is shown: AWT computes the blocking of a window when it becomes visible
    if (excludeFromModality) frame.modalExclusionType = Dialog.ModalExclusionType.APPLICATION_EXCLUDE
    // added before FrameWrapper adds its own listener (in show), so the user close is reported before the frame closes
    frame.addWindowListener(object : WindowAdapter() {
      override fun windowClosing(e: WindowEvent) = onUserClose(this@IconInspectorFrame)
    })
    if (bounds == null) {
      show()
    }
    else {
      frame.bounds = bounds
      show(false)
    }
  }

  override fun hostWindow(): Window? = if (isDisposed) null else getFrame()

  override fun closeHost() = close()

  companion object {
    /** Bounds of the detached window, remembered per project by the `WindowStateService`. */
    const val DIMENSION_KEY: String = "IconInspector.DetachedWindow"
  }
}

/** The inspector UI for a separate window. [onDock], if set, adds the "dock" button. */
internal fun createInspectorComponent(controller: IconInspectorController, onDock: (() -> Unit)?): JComponent {
  enableNewSwingCompositing()
  return JewelComposePanel { IconInspectorPanel(controller, onDock) }.apply {
    preferredSize = JBUI.size(900, 640)
    minimumSize = JBUI.size(400, 300)
  }
}
