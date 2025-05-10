// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XSourcePosition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object XDebuggerUtilImplShared {
  @ApiStatus.Internal
  @JvmStatic
  fun createNavigatable(project: Project, position: XSourcePosition): Navigatable {
    return XSourcePositionNavigatable(project, position)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun createOpenFileDescriptor(project: Project, position: XSourcePosition): OpenFileDescriptor {
    return if (position.getOffset() != -1)
      OpenFileDescriptor(project, position.getFile(), position.getOffset())
    else
      OpenFileDescriptor(project, position.getFile(), position.getLine(), 0)
  }

  private class XSourcePositionNavigatable(private val myProject: Project, private val myPosition: XSourcePosition) : Navigatable {
    override fun navigate(requestFocus: Boolean) {
      createOpenFileDescriptor(myProject, myPosition).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean {
      return myPosition.getFile().isValid()
    }

    override fun canNavigateToSource(): Boolean {
      return canNavigate()
    }
  }
}