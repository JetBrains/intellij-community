// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XLineBreakpointManagerProxy {
  fun getDocumentBreakpointProxies(document: Document): Collection<XLineBreakpointProxy>
  fun queueBreakpointUpdateCallback(breakpoint: XLightLineBreakpointProxy, callback: Runnable)
  fun breakpointChanged(breakpoint: XLightLineBreakpointProxy)
  fun getAllBreakpoints(): Collection<XLineBreakpointProxy>
}
