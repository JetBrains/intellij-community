// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.shared.XDebuggerUtilImplShared
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XSourcePosition
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class XSourcePositionDto(
  val line: Int,
  val offset: Int,
  val fileId: VirtualFileId,
  // TODO[IJPL-160146]: localXSourcePosition is passed only for `XSourcePosition.createNavigatable`
  @Transient val localXSourcePosition: XSourcePosition? = null,
)

@ApiStatus.Internal
fun XSourcePosition.toRpc(): XSourcePositionDto {
  return XSourcePositionDto(line, offset, file.rpcId(), this)
}

@ApiStatus.Internal
fun XSourcePositionDto.sourcePosition(): XSourcePosition {
  if (localXSourcePosition != null) {
    return localXSourcePosition
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