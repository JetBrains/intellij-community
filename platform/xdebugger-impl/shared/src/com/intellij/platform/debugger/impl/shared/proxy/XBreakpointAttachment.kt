// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import org.jetbrains.annotations.ApiStatus

/**
 * An attachment is something that attaches to a breakpoint's lifecycle.
 *
 * Attachments are created when a breakpoint is created and are automatically
 * disposed when the breakpoint is disposed (via coroutine scope cancellation).
 *
 * The [breakpointChanged] method is called whenever the breakpoint's state changes,
 * including breakpoint creation, allowing the attachment to react to changes
 * (e.g., create/dispose UI components based on breakpoint traits).
 *
 * NOTE: attachments react to existing breakpoint changes only after "Done" is pressed
 * in the breakpoint settings dialog.
 */
@ApiStatus.Internal
interface XBreakpointAttachment {
  /**
   * The breakpoint this attachment is attached to.
   */
  val breakpoint: XBreakpointProxy

  /**
   * Called when the breakpoint's state changes:
   * - Breakpoint changes via the "Edit Breakpoint" popup
   * - State-changing interactions via gutter (drag-and-drop, enable, disable)
   */
  fun breakpointChanged()
}
