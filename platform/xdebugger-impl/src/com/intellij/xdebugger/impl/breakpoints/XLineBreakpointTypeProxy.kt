// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface XLineBreakpointTypeProxy : XBreakpointTypeProxy {
  val temporaryIcon: Icon?

  val priority: Int

  fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean

  @Suppress("DEPRECATION")
  class Monolith @Deprecated("Use type.asProxy() instead") internal constructor(
    project: Project,
    lineBreakpointType: XLineBreakpointType<*>,
  ) : XBreakpointTypeProxy.Monolith(project, lineBreakpointType), XLineBreakpointTypeProxy {
    override val breakpointType: XLineBreakpointType<*> = lineBreakpointType

    override val temporaryIcon: Icon?
      get() = breakpointType.temporaryIcon

    override val priority: Int get() = breakpointType.priority

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
      return breakpointType.canPutAt(file, line, project)
    }
  }
}