// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.rpc.util.textRange
import com.intellij.ide.rpc.util.toRpc
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.XDebuggerTreeNodeHyperlinkDto
import com.intellij.platform.debugger.impl.rpc.XSourcePositionDto
import com.intellij.platform.debugger.impl.shared.XDebuggerUtilImplShared
import com.intellij.pom.Navigatable
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.impl.rpc.models.storeGlobally
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
fun XSourcePosition.toRpc(): XSourcePositionDto {
  return runReadAction {
    val textRangeDto = (this as? ExecutionPointHighlighter.HighlighterProvider)?.highlightRange?.toRpc()
    XSourcePositionDto(line, offset, file.rpcId(), textRangeDto, this)
  }
}

@ApiStatus.Internal
fun XSourcePositionDto.sourcePosition(): XSourcePosition {
  localXSourcePosition?.let {
    return it
  }

  return SerializedXSourcePosition(this)
}

private class SerializedXSourcePosition(private val dto: XSourcePositionDto) : XSourcePosition, ExecutionPointHighlighter.HighlighterProvider {
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

  override fun getHighlightRange(): TextRange? = dto.textRangeDto?.textRange()
}

fun XDebuggerTreeNodeHyperlink.toRpc(cs: CoroutineScope): XDebuggerTreeNodeHyperlinkDto {
  val id = storeGlobally(cs)
  return XDebuggerTreeNodeHyperlinkDto(
    id,
    linkText,
    linkTooltip,
    linkIcon?.rpcId(),
    shortcutSupplier?.get(),
    alwaysOnScreen(),
    textAttributes,
    this,
  )
}
