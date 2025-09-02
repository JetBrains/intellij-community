// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * @see com.intellij.xdebugger.impl.rpc.models.XStackFrameValueIdType
 * @see com.intellij.xdebugger.impl.rpc.models.XStackFrameModel
 */
@ApiStatus.Internal
@Serializable
data class XStackFrameId(override val uid: UID) : Id