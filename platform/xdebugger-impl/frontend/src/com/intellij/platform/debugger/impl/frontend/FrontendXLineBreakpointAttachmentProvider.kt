// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointAttachment
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point for creating attachments for frontend line breakpoints.
 *
 * Attachments are objects that attach to a breakpoint's lifecycle and react to its state changes.
 * They are created when the breakpoint is created and automatically disposed when the breakpoint
 * is disposed (via coroutine scope cancellation).
 *
 * @see XBreakpointAttachment
 * @see XLineBreakpointProxy
 */
@ApiStatus.Internal
interface FrontendXLineBreakpointAttachmentProvider {

  /**
   * Creates an attachment for the given breakpoint.
   *
   * @param breakpoint the breakpoint to create an attachment for
   * @param breakpointScope a coroutine scope tied to the breakpoint's lifecycle;
   *        when the breakpoint is disposed, this scope is cancelled
   * @return an attachment, or `null` if this provider does not want to attach to this breakpoint
   */
  fun createAttachment(
    breakpoint: XLineBreakpointProxy,
    breakpointScope: CoroutineScope,
  ): XBreakpointAttachment?

  companion object {
    private val EP_NAME: ExtensionPointName<FrontendXLineBreakpointAttachmentProvider> =
      ExtensionPointName.create("com.intellij.xdebugger.frontendLineBreakpointAttachmentProvider")

    /**
     * Creates attachments for the given breakpoint from all registered providers.
     *
     * @param breakpoint the breakpoint to create attachments for
     * @param breakpointScope a coroutine scope tied to the breakpoint's lifecycle
     * @return a list of attachments from all providers that returned non-null
     */
    fun createAttachments(
      breakpoint: XLineBreakpointProxy,
      breakpointScope: CoroutineScope,
    ): List<XBreakpointAttachment> {
      return EP_NAME.extensionList.mapNotNull { provider ->
        provider.createAttachment(breakpoint, breakpointScope)
      }
    }
  }
}
