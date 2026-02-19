// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.rpc.util.TextRangeDto
import com.intellij.ide.vfs.VirtualFileId
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
    val textRangeDto: TextRangeDto?,
  // TODO[IJPL-160146]: localXSourcePosition is passed only for `XSourcePosition.createNavigatable`
    @Transient val localXSourcePosition: XSourcePosition? = null,
)
