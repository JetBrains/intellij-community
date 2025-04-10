// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.icons.icon
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeDto
import javax.swing.Icon

internal class FrontendXBreakpointType(
  private val dto: XBreakpointTypeDto,
) : XBreakpointTypeProxy {
  override val id: String = dto.id.id
  override val index: Int = dto.index
  override val title: String = dto.title
  override val enabledIcon: Icon = dto.enabledIcon.icon()
  override val isLineBreakpoint: Boolean = dto.lineTypeInfo != null
  override val isSuspendThreadSupported: Boolean = dto.suspendThreadSupported
  override val priority: Int? = dto.lineTypeInfo?.priority
}