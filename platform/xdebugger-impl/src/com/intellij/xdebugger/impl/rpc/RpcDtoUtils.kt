// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.XSourcePositionDto
import com.intellij.platform.debugger.impl.shared.XDebuggerUtilImplShared
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XSourcePosition
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
fun XSourcePosition.toRpc(): XSourcePositionDto {
  return XSourcePositionDto(line, offset, file.rpcId(), this)
}

@ApiStatus.Internal
fun XSourcePositionDto.sourcePosition(): XSourcePosition {
  localXSourcePosition?.let {
    return it
  }

  return SerializedXSourcePosition(this)
}

private class SerializedXSourcePosition(private val dto: XSourcePositionDto) : XSourcePosition {
  private val virtualFile = dto.fileId.virtualFile()

  override fun getLine(): Int {
    return dto.line
  }

  override fun getOffset(): Int {
    return dto.offset
  }

  override fun getFile(): VirtualFile {
    return virtualFile!!
  }

  override fun createNavigatable(project: Project): Navigatable {
    return XDebuggerUtilImplShared.createNavigatable(project, this)
  }
}
